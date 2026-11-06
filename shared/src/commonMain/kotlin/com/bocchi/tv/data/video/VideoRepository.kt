package com.bocchi.tv.data.video

import com.bocchi.tv.data.remote.MacCmsClient
import com.bocchi.tv.data.source.SourceStore
import com.bocchi.tv.data.source.VideoSource
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.PlayLine
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 影片仓库（跨平台）。
 *
 * 功能：对多个采集源做并发聚合——搜索时同时向所有已启用源发请求，边完成边推送快照，
 *      并按归一化片名把同一部影片归并为一条，界面因此能实时看到「已完成 x/y 个源」。
 * 参数：macCms —— 采集接口客户端；resolver —— 播放地址解析器；sourceStore —— 数据源仓库。
 * 返回值：无（服务类）。
 */
class VideoRepository(
    private val macCms: MacCmsClient,
    private val resolver: MediaUrlResolver,
    private val sourceStore: SourceStore
) {

    /**
     * 聚合搜索。
     *
     * 功能：并发请求全部已启用源，每完成一个源就推出一份结果快照；同一部影片按归一化片名归并，
     *      因此后续详情页可以在多个源之间切换线路。
     * 参数：keyword —— 搜索关键词。
     * 返回值：Flow<AggregateResult> —— 流式结果快照。
     */
    fun search(keyword: String): Flow<AggregateResult> = channelFlow {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            send(AggregateResult())
            return@channelFlow
        }
        val sources = sourceStore.enabled()
        if (sources.isEmpty()) {
            send(AggregateResult())
            return@channelFlow
        }

        val groups = LinkedHashMap<String, MutableList<VideoSummary>>()
        val mutex = Mutex()
        var finished = 0
        var empty = 0

        coroutineScope {
            sources.forEach { source ->
                launch {
                    val hits = runCatching { fetchSummaries(source, trimmed) }.getOrDefault(emptyList())
                    val snapshot = mutex.withLock {
                        finished++
                        if (hits.isEmpty()) empty++
                        hits.forEach { hit ->
                            val groupKey = PlayUrlParser.normalizeTitle(hit.title).ifBlank { hit.title }
                            groups.getOrPut(groupKey) { mutableListOf() }.add(hit)
                        }
                        AggregateResult(
                            items = buildAggregated(groups),
                            finishedSources = finished,
                            totalSources = sources.size,
                            emptySources = empty
                        )
                    }
                    send(snapshot)
                }
            }
        }
    }

    /**
     * 加载影片详情与播放线路。
     *
     * 功能：用聚合结果里某个源的影片 ID 回查该源详情，取回播放线路；
     *      播放地址保持原始形态（不预解析），交给播放器在起播前按需解析，避免无谓请求。
     * 参数：match —— 聚合结果中该影片在某个源上的命中记录。
     * 返回值：VideoDetail? —— 详情；请求失败或数据异常返回 null。
     */
    suspend fun loadDetail(match: VideoSummary): VideoDetail? {
        val source = sourceStore.all().firstOrNull { it.key == match.sourceKey } ?: return null
        val response = runCatching {
            macCms.query(source.api, mapOf("ac" to "detail", "ids" to match.vodId))
        }.getOrNull() ?: return null
        val dto = response.list.firstOrNull() ?: return null

        val lines = PlayUrlParser.parseLines(dto.vodPlayFrom, dto.vodPlayUrl).map { parsed ->
            PlayLine(
                sourceKey = source.key,
                sourceName = source.name,
                lineName = parsed.name,
                episodes = parsed.episodes
            )
        }
        return VideoDetail(
            title = dto.vodName.ifBlank { match.title },
            poster = dto.vodPic.ifBlank { match.poster },
            remarks = dto.vodRemarks.ifBlank { match.remarks },
            year = dto.vodYear.ifBlank { match.year },
            area = dto.vodArea,
            lang = dto.vodLang,
            actor = dto.vodActor,
            director = dto.vodDirector,
            score = dto.vodScore.ifBlank { match.score },
            typeName = dto.typeName.ifBlank { match.typeName },
            content = PlayUrlParser.cleanHtml(dto.vodContent),
            lines = lines
        )
    }

    /**
     * 解析播放地址为可播放形态。
     *
     * 参数：raw —— 采集源给出的原始地址。
     * 返回值：String? —— 可播放地址；无法解析返回 null。
     */
    suspend fun resolvePlayUrl(raw: String): String? = resolver.resolve(raw)

    /**
     * 清空解析缓存与列表缓存。
     *
     * 参数：无。
     * 返回值：无。
     */
    fun clearCaches() {
        resolver.clearCache()
    }

    /**
     * 请求单个源的搜索结果。
     *
     * 参数：source —— 数据源；keyword —— 关键词。
     * 返回值：List<VideoSummary> —— 该源命中的影片。
     */
    private suspend fun fetchSummaries(source: VideoSource, keyword: String): List<VideoSummary> {
        val params = buildMap {
            put("ac", "list")
            put(source.searchParam, keyword)
            put("pg", "1")
        }
        return macCms.query(source.api, params).list.mapNotNull { dto ->
            val title = dto.vodName.trim()
            if (title.isEmpty()) {
                null
            } else {
                VideoSummary(
                    sourceKey = source.key,
                    sourceName = source.name,
                    vodId = dto.vodId,
                    title = title,
                    poster = dto.vodPic,
                    remarks = dto.vodRemarks,
                    year = dto.vodYear,
                    typeName = dto.typeName,
                    score = dto.vodScore
                )
            }
        }
    }

    /**
     * 把分组结果转换成界面条目。
     *
     * 功能：每组取第一条命中的基础信息，海报/评分等字段优先取有值的那个，避免因首个源缺字段而空白。
     * 参数：groups —— 归一化片名到命中列表的映射。
     * 返回值：List<AggregatedVideo> —— 聚合后的条目列表。
     */
    private fun buildAggregated(groups: Map<String, List<VideoSummary>>): List<AggregatedVideo> =
        groups.values.mapNotNull { matches ->
            val first = matches.firstOrNull() ?: return@mapNotNull null
            AggregatedVideo(
                title = first.title,
                poster = matches.firstOrNull { it.poster.isNotBlank() }?.poster ?: first.poster,
                remarks = matches.firstOrNull { it.remarks.isNotBlank() }?.remarks ?: first.remarks,
                year = matches.firstOrNull { it.year.isNotBlank() }?.year ?: first.year,
                typeName = first.typeName,
                score = matches.firstOrNull { it.score.isNotBlank() }?.score ?: first.score,
                matches = matches
            )
        }
}
