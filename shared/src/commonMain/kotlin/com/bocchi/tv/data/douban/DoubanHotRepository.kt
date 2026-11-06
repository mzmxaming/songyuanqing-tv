package com.bocchi.tv.data.douban

import com.bocchi.tv.data.remote.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

/**
 * 豆瓣热门条目。
 *
 * 参数：id 豆瓣条目 ID；title 片名；rate 评分；cover 海报地址。
 * 返回值：无（数据类）。
 */
data class DoubanSubject(
    val id: String,
    val title: String,
    val rate: String,
    val cover: String
)

/** 豆瓣热门接口响应（内部使用）。 */
@Serializable
private data class DoubanHotResponse(
    val subjects: List<DoubanSubjectDto> = emptyList()
)

/** 豆瓣热门单条响应（内部使用，仅取所需字段）。 */
@Serializable
private data class DoubanSubjectDto(
    val id: String = "",
    val title: String = "",
    val rate: String = "",
    val cover: String = ""
)

/**
 * 豆瓣热门推荐仓库（跨平台）。
 *
 * 功能：请求豆瓣公开的「热门电影」列表用于首页推荐位；接口不可用时返回空列表，不影响首页其它内容。
 *      豆瓣要求带 Referer，否则返回 418。
 * 参数：client —— 共享的 Ktor 客户端。
 * 返回值：无（服务类）。
 */
class DoubanHotRepository(private val client: HttpClient) {

    /**
     * 拉取豆瓣热门电影。
     *
     * 功能：响应体可能带 BOM，解析前先去除；任何失败都返回空列表。
     * 参数：无。
     * 返回值：List<DoubanSubject> —— 热门条目（最多 20 条）。
     */
    suspend fun loadHotSubjects(): List<DoubanSubject> = runCatching {
        val body = client.get(HOT_URL) {
            header("Referer", REFERER)
        }.bodyAsText().removePrefix("\uFEFF").trim()
        if (body.isEmpty()) return@runCatching emptyList()
        AppJson.decodeFromString(DoubanHotResponse.serializer(), body)
            .subjects
            .filter { it.title.isNotBlank() }
            .map { dto ->
                DoubanSubject(
                    id = dto.id,
                    title = dto.title,
                    rate = dto.rate,
                    cover = dto.cover
                )
            }
    }.getOrDefault(emptyList())

    /** 豆瓣热门电影接口。 */
    private companion object {
        const val HOT_URL: String =
            "https://movie.douban.com/j/search_subjects?type=movie&tag=%E7%83%AD%E9%97%A8&page_limit=20&page_start=0"
        const val REFERER: String = "https://movie.douban.com/"
    }
}
