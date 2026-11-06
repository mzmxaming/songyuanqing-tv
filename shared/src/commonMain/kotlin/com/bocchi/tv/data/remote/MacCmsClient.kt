package com.bocchi.tv.data.remote

import com.bocchi.tv.data.remote.dto.MacCmsVodResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json

/**
 * 苹果CMS V10 采集接口客户端（跨平台）。
 *
 * 功能：按采集站约定的 query 参数请求 `ac=list`（列表/搜索）或 `ac=detail`（详情，含播放地址），
 *      把响应文本宽松解析为 DTO。先取文本再手动解析，是为了兼容部分源返回的 BOM 与非标准 JSON。
 * 参数：client —— 复用的 HTTP 客户端；json —— JSON 解析器。
 * 返回值：无（服务类）。
 */
class MacCmsClient(
    private val client: HttpClient,
    private val json: Json = AppJson
) {

    /**
     * 请求采集接口。
     *
     * 功能：在源地址上追加 query 参数后发起 GET；HTTP 非 2xx 或解析失败时抛出异常，由调用方按源降级处理。
     * 参数：api —— 源采集接口地址；params —— query 参数（如 ac / wd / pg / ids）。
     * 返回值：MacCmsVodResponseDto —— 解析后的响应。
     */
    suspend fun query(api: String, params: Map<String, String>): MacCmsVodResponseDto {
        val url = URLBuilder(api.trim()).apply {
            params.forEach { (key, value) -> parameters.append(key, value) }
        }.build().toString()

        val text = client.get(url)
            .bodyAsText()
            .trim()
            .removePrefix("\uFEFF")

        return json.decodeFromString(MacCmsVodResponseDto.serializer(), text)
    }
}
