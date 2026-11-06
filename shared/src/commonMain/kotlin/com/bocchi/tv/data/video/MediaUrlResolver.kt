package com.bocchi.tv.data.video

import com.bocchi.tv.data.video.SimpleLruCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter

/**
 * 播放地址解析器（跨平台）。
 *
 * 功能：把采集源返回的播放地址转换成播放器可直接使用的媒体地址。按三层处理：
 *      ① 已是直链（m3u8/mp4 等）直接返回，零额外请求；
 *      ② 配置了外部解析接口时先交给它解析；
 *      ③ 仍拿不到则抓取网页，从 HTML 中提取 m3u8/mp4，或跟随一层 iframe 后再提取。
 *      解析结果带缓存，同一集不会反复请求。
 * 参数：client —— HTTP 客户端；parseApiProvider —— 返回当前配置的解析接口地址（空串表示不用外部解析）。
 * 返回值：无（服务类）。
 */
class MediaUrlResolver(
    private val client: HttpClient,
    private val parseApiProvider: () -> String = { "" }
) {

    /** 解析结果缓存：键为原始地址，值为可播放地址。 */
    private val cache = SimpleLruCache<String, String>(MAX_CACHE_ENTRIES)

    /**
     * 判断是否为可直接播放的媒体直链。
     *
     * 功能：按扩展名判断，兼容带 query 的地址（如 `index.m3u8?auth=xxx`）。
     * 参数：url —— 待判断的地址。
     * 返回值：Boolean —— 是直链返回 true。
     */
    fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http")) return false
        return DIRECT_MEDIA_REGEX.containsMatchIn(lower)
    }

    /**
     * 解析播放地址（带缓存）。
     *
     * 功能：直链原样返回；非直链走「外部解析接口 → 本地页面提取」两层兜底。
     *      任一层失败都会继续尝试下一层，最终失败返回 null 由界面提示切换线路。
     * 参数：raw —— 采集源给出的原始播放地址。
     * 返回值：String? —— 可播放地址；解析失败返回 null。
     */
    suspend fun resolve(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (isDirectMediaUrl(trimmed)) return trimmed
        cache.get(trimmed)?.let { return it }
        val resolved = resolveUncached(trimmed)
        if (resolved != null) cache.put(trimmed, resolved, CACHE_TTL_MS)
        return resolved
    }

    /**
     * 实际执行解析（无缓存）。
     *
     * 参数：raw —— 已去除首尾空白的非直链播放地址。
     * 返回值：String? —— 解析出的媒体地址；无法解析时返回 null。
     */
    private suspend fun resolveUncached(raw: String): String? {
        val parseApi = parseApiProvider().trim()
        if (parseApi.isNotEmpty()) {
            buildParseRequestUrl(parseApi, raw)?.let { requestUrl ->
                fetchText(requestUrl)?.let { body ->
                    extractMediaUrl(body)?.let { return it }
                }
            }
        }
        fetchText(raw)?.let { body ->
            extractMediaUrl(body)?.let { return it }
            extractRedirectUrl(body, raw)?.let { redirectUrl ->
                fetchText(redirectUrl)?.let { nested ->
                    extractMediaUrl(nested)?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * 校验解析接口地址格式。
     *
     * 功能：空串视为合法（表示关闭外部解析）；非空时必须带 http/https 协议且能被解析成合法地址。
     *      含 `{url}` 占位符的地址会先替换为示例值再校验，不会因占位符被误判为非法。
     * 参数：api —— 用户输入的解析接口地址。
     * 返回值：String? —— 合法时返回 null；不合法时返回可直接展示的错误文案。
     */
    fun validateParseApi(api: String): String? {
        val trimmed = api.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "接口地址需要以 http:// 或 https:// 开头"
        }
        val checkable = trimmed.replace(URL_PLACEHOLDER, "sample")
        return runCatching { URLBuilder(checkable).build() }
            .fold(onSuccess = { null }, onFailure = { "接口地址格式不正确，请检查是否包含非法字符" })
    }

    /**
     * 构造解析请求地址。
     *
     * 功能：地址含 `{url}` 时把占位符替换为编码后的播放地址；否则把播放地址编码后拼接在末尾。
     * 参数：parseApi —— 解析接口地址；raw —— 原始播放地址。
     * 返回值：String? —— 请求地址；接口地址不合法时返回 null。
     */
    fun buildParseRequestUrl(parseApi: String, raw: String): String? {
        if (validateParseApi(parseApi) != null) return null
        val trimmed = parseApi.trim()
        if (trimmed.isEmpty()) return null
        val encoded = raw.encodeURLParameter()
        return if (trimmed.contains(URL_PLACEHOLDER)) {
            trimmed.replace(URL_PLACEHOLDER, encoded)
        } else {
            trimmed + encoded
        }
    }

    /**
     * 抓取网页文本。
     *
     * 参数：url —— 目标地址。
     * 返回值：String? —— 响应文本；请求失败返回 null。
     */
    private suspend fun fetchText(url: String): String? = runCatching {
        client.get(url) {
            headers.append(HttpHeaders.UserAgent, DESKTOP_USER_AGENT)
        }.bodyAsText()
    }.getOrNull()

    /**
     * 从网页内容中提取媒体地址。
     *
     * 功能：先找完整的 http(s) 直链，再找协议相对地址（`//cdn/x.m3u8`）并补全为 https。
     * 参数：body —— 网页文本。
     * 返回值：String? —— 提取到的媒体地址；没有则返回 null。
     */
    fun extractMediaUrl(body: String): String? {
        ABSOLUTE_MEDIA_REGEX.find(body)?.let { return it.value }
        PROTOCOL_RELATIVE_MEDIA_REGEX.find(body)?.let { return "https:${it.value}" }
        return null
    }

    /**
     * 从网页内容中提取 iframe 跳转地址。
     *
     * 功能：部分解析页把真实播放器放在 iframe 里，需要再跟一层。
     * 参数：body —— 网页文本；baseUrl —— 当前页面地址（用于补全相对路径）。
     * 返回值：String? —— iframe 的绝对地址；没有则返回 null。
     */
    fun extractRedirectUrl(body: String, baseUrl: String): String? {
        val src = IFRAME_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()
        if (src.isNullOrEmpty()) return null
        return absoluteUrl(src, baseUrl)
    }

    /**
     * 把相对地址补全为绝对地址。
     *
     * 参数：target —— 目标地址（可能是绝对地址、协议相对地址或以 / 开头的相对路径）；
     *      baseUrl —— 当前页面地址。
     * 返回值：String? —— 绝对地址；无法补全时返回 null。
     */
    private fun absoluteUrl(target: String, baseUrl: String): String? {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        if (target.startsWith("//")) return "https:$target"
        return runCatching {
            val base = URLBuilder(baseUrl).build()
            val origin = buildString {
                append(base.protocol.name).append("://").append(base.host)
                if (base.port != base.protocol.defaultPort) append(':').append(base.port)
            }
            if (target.startsWith("/")) {
                origin + target
            } else {
                val directory = base.encodedPath.substringBeforeLast('/', "")
                "$origin$directory/$target"
            }
        }.getOrNull()
    }

    /** 清空解析缓存。 */
    fun clearCache() {
        cache.clear()
    }

    private companion object {
        /** 解析接口地址中的插入位置占位符。 */
        const val URL_PLACEHOLDER: String = "{url}"

        /** 解析结果缓存条目数。 */
        const val MAX_CACHE_ENTRIES: Int = 64

        /** 解析结果存活时长（10 分钟）。 */
        const val CACHE_TTL_MS: Long = 10 * 60 * 1000L

        /** 抓取网页时使用的 UA，避免部分站点拒绝空 UA 请求。 */
        const val DESKTOP_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        /** 直链扩展名判断。 */
        val DIRECT_MEDIA_REGEX = Regex("""\.(m3u8|mp4|flv|mkv|webm|mov|ts|m4a|mp3)(\?|#|$)""")

        /** 网页中的完整媒体地址。 */
        val ABSOLUTE_MEDIA_REGEX =
            Regex("""https?://[^\s"'<>\\]+\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)

        /** 网页中的协议相对媒体地址。 */
        val PROTOCOL_RELATIVE_MEDIA_REGEX =
            Regex("""//[^\s"'<>\\]+\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)

        /** iframe 引用。 */
        val IFRAME_REGEX = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
