package com.bocchi.tv.desktop.player

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * HLS 清单本地化工具。
 *
 * 功能：下载远程 m3u8，把其中所有相对地址（AES 密钥、初始化分片、子清单、分片）改写为绝对地址，
 *      再保存成本地清单交给 libVLC 播放。用于规避部分 libVLC 版本在解析相对密钥地址时
 *      拼接出 `https://host:` 这类残缺地址、导致解复用失败的问题。
 * 参数：无（工具对象）。
 * 返回值：无。
 */
object HlsManifestRewriter {

    /** 浏览器 UA，与播放器请求保持一致，避免源站拒绝。 */
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** 最大递归层数：主清单 → 子清单。 */
    private const val MAX_DEPTH = 2

    /** 清单属性中需要改写的 URI 正则（EXT-X-KEY / EXT-X-MAP / EXT-X-MEDIA / EXT-X-I-FRAME-STREAM-INF）。 */
    private val URI_ATTRIBUTE = Regex("URI=\"([^\"]+)\"")

    /**
     * 尝试把远程 m3u8 改写成绝对地址的本地清单。
     *
     * 参数：url —— 播放地址；referer —— 请求来源，可为空；depth —— 当前递归深度。
     * 返回值：String? —— 成功时返回本地清单的 file:// 地址；非 m3u8 或失败时返回 null（调用方回退到原地址）。
     */
    fun rewrite(url: String, referer: String?, depth: Int = 0): String? {
        if (depth > MAX_DEPTH) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val text = fetch(url, referer) ?: return null
        if (!text.trimStart().startsWith("#EXTM3U")) return null

        val base = URL(url)
        val isMaster = text.contains("#EXT-X-STREAM-INF")
        val out = StringBuilder(text.length + 256)
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> out.append('\n')
                line.startsWith("#") -> {
                    out.append(URI_ATTRIBUTE.replace(line) { match ->
                        val uri = match.groupValues[1]
                        "URI=\"${absolutize(base, uri, referer, depth)}\""
                    }).append('\n')
                }
                else -> {
                    // 非注释行：分片地址或子清单地址
                    val absolute = absolutize(base, line, referer, depth)
                    out.append(absolute).append('\n')
                }
            }
        }

        val dir = File(System.getProperty("java.io.tmpdir"), "songyuanqing-hls").apply { mkdirs() }
        val file = File.createTempFile("manifest-", ".m3u8", dir)
        file.deleteOnExit()
        file.writeText(out.toString())
        // 必须是 file:/// 三段式，File.toURI() 生成的是 file:/，libVLC 会当成相对路径
        val localUrl = "file://" + file.absolutePath
        if (!isMaster) return localUrl

        // 主清单：子清单同样改写成绝对地址，避免 libVLC 解析子清单时再次出错
        return if (depth + 1 <= MAX_DEPTH) localUrl else null
    }

    /**
     * 把单个地址按清单所在位置补全为绝对地址；子清单会递归改写为本地文件。
     *
     * 参数：base —— 清单地址；uri —— 待处理地址；referer —— 请求来源；depth —— 当前递归深度。
     * 返回值：String —— 绝对地址；无法解析时原样返回。
     */
    private fun absolutize(base: URL, uri: String, referer: String?, depth: Int): String {
        if (uri.isBlank()) return uri
        if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("file:")) return uri
        return runCatching {
            val resolved = URL(base, uri).toString()
            when {
                // 子清单：下载并改写后指回本地文件
                resolved.contains(".m3u8") -> rewrite(resolved, referer, depth + 1) ?: resolved
                else -> resolved
            }
        }.getOrDefault(uri)
    }

    /**
     * 下载清单文本。
     *
     * 参数：url —— 清单地址；referer —— 请求来源。
     * 返回值：String? —— 文本内容；失败返回 null。
     */
    private fun fetch(url: String, referer: String?): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
        if (!referer.isNullOrBlank()) connection.setRequestProperty("Referer", referer)
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
