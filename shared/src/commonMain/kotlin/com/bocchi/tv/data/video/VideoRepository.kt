package com.bocchi.tv.data.video

import com.bocchi.tv.data.remote.MacCmsClient
import com.bocchi.tv.data.remote.dto.MacCmsClassDto
import com.bocchi.tv.data.remote.dto.MacCmsVodDto
import com.bocchi.tv.data.source.SourceStore
import com.bocchi.tv.data.source.VideoSource
import com.bocchi.tv.data.video.model.AggregateResult
import com.bocchi.tv.data.video.model.AggregatedVideo
import com.bocchi.tv.data.video.model.BrowseCategory
import com.bocchi.tv.data.video.model.PlayLine
import com.bocchi.tv.data.video.model.VideoDetail
import com.bocchi.tv.data.video.model.VideoSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 影片仓库（跨平台）。
 *
 * 功能：对多个采集源做并发聚合——搜索或分类浏览时同时向所有已启用源发请求，边完成边推送快照，
 *      并按归一化片名把同一部影片归并为一条，界面因此能实时看到「已完成 x/y 个源」。
 * 参数：macCms —— 采集接口客户端；resolver —— 播放地址解析器；sourceStore —— 数据源仓库。
 * 返回值：无（服务类）。
 */
class VideoRepository(
    private val macCms: MacCmsClient,
    private val resolver: MediaUrlResolver,
    private val sourceStore: SourceStore
) {

    /** 分类表缓存：源 key -> 分类表（30 分钟）。 */
    private val classListCache = SimpleLruCache<String, List<MacCmsClassDto>>(32)

    /**
     * 聚合搜索。
     *
     * 参数：keyword —— 搜索关键词。
     * 返回值：Flow<AggregateResult> —— 流式结果快照。
     */
    fun search(keyword: String): Flow<AggregateResult> =
        aggregate(keyword = keyword.trim().ifBlank { null })

    /**
     * 分类浏览。
     *
     * 功能：按统一分类并发拉取各源内容；[subCategory] 为空时按「顶层分类 + 子分类」多 ID 取样。
     * 参数：category —— 统一分类；subCategory —— 二级分类名（为空表示全部）。
     * 返回值：Flow<AggregateResult> —— 流式结果快照。
     */
    fun browse(category: BrowseCategory, subCategory: String? = null): Flow<AggregateResult> =
        aggregate(keyword = null, category = category, subCategory = subCategory)

    /**
     * 加载某统一分类的二级分类名（跨源合并）。
     *
     * 功能：并发拉取各已启用源的分类表，统计子分类被多少源收录，按收录数倒序取前
     *      [MAX_SUB_CATEGORIES] 个，供分类页做二级筛选。
     * 参数：category —— 统一分类。
     * 返回值：List<String> —— 子分类名列表；没有任何源提供时为空列表。
     */
    suspend fun loadSubCategories(category: BrowseCategory): List<String> {
        val sources = sourceStore.enabled()
        if (sources.isEmpty()) return emptyList()

        val counter = LinkedHashMap<String, Int>()
        val mutex = Mutex()
        coroutineScope {
            sources.forEach { source ->
                launch {
                    val names = runCatching {
                        ClassMappingResolver.buildSubCategories(loadClasses(source), category)
                    }.getOrDefault(emptyList())
                    if (names.isNotEmpty()) {
                        mutex.withLock {
                            names.forEach { name -> counter[name] = (counter[name] ?: 0) + 1 }
                        }
                    }
                }
            }
        }
        return counter.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_SUB_CATEGORIES)
            .map { it.key }
    }

    /**
     * 加载影片详情与播放线路。
     *
     * 功能：用聚合结果里某个源的影片 ID 回查该源详情，取回播放线路；
     *      播放地址保持原始形态（不预解析），交给播放器在起播前按需解析。
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
     * 清空解析缓存与分类表缓存。
     *
     * 参数：无。
     * 返回值：无。
     */
    fun clearCaches() {
        resolver.clearCache()
        classListCache.clear()
    }

    /**
     * 执行一轮多源聚合（搜索与分类浏览共用）。
     *
     * 参数：keyword —— 关键词（分类浏览时为 null）；category —— 统一分类（搜索时为 null）；
     *      subCategory —— 二级分类名。
     * 返回值：Flow<AggregateResult> —— 流式结果快照。
     */
    private fun aggregate(
        keyword: String?,
        category: BrowseCategory? = null,
        subCategory: String? = null
    ): Flow<AggregateResult> = channelFlow {
        if (keyword == null && category == null) {
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
        send(AggregateResult(totalSources = sources.size, finishedSources = 0))

        coroutineScope {
            sources.forEach { source ->
                launch {
                    val hits = runCatching {
                        fetchSummaries(source, keyword, category, subCategory)
                    }.getOrDefault(emptyList())
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
     * 请求单个源的内容（搜索或分类浏览）。
     *
     * 功能：分类浏览时先取该源分类表，解析出需要请求的分类 ID；指定二级分类时只请求该子分类。
     * 参数：source —— 数据源；keyword —— 关键词（浏览时为 null）；category —— 统一分类；
     *      subCategory —— 二级分类名。
     * 返回值：List<VideoSummary> —— 该源命中的影片。
     */
    private suspend fun fetchSummaries(
        source: VideoSource,
        keyword: String?,
        category: BrowseCategory?,
        subCategory: String?
    ): List<VideoSummary> {
        if (category == null) {
            return fetchPage(source, keyword, typeId = null)
        }
        if (!subCategory.isNullOrBlank()) {
            val typeId = ClassMappingResolver
                .findTypeIdBySubName(loadClasses(source), category, subCategory)
                ?: return emptyList()
            return fetchPage(source, keyword = null, typeId = typeId)
        }
        val typeIds = ClassMappingResolver.build(loadClasses(source))[category].orEmpty()
        if (typeIds.isEmpty()) return emptyList()
        return coroutineScope {
            typeIds
                .map { typeId -> async { fetchPage(source, keyword = null, typeId = typeId) } }
                .awaitAll()
                .flatten()
        }
    }

    /**
     * 请求某源的一页列表。
     *
     * 参数：source —— 数据源；keyword —— 关键词；typeId —— 源内分类 ID。
     * 返回值：List<VideoSummary> —— 影片摘要；失败时为空列表。
     */
    private suspend fun fetchPage(
        source: VideoSource,
        keyword: String?,
        typeId: String?
    ): List<VideoSummary> {
        val params = buildMap {
            put("ac", "list")
            keyword?.takeIf { it.isNotBlank() }?.let {
                put(source.searchParam.ifBlank { "wd" }, it)
            }
            typeId?.takeIf { it.isNotBlank() }?.let { put("t", it) }
            put("pg", "1")
        }
        return runCatching {
            macCms.query(source.api, params).list.mapNotNull { dto -> dto.toSummary(source) }
        }.getOrDefault(emptyList())
    }

    /**
     * 加载某数据源的分类表（带缓存）。
     *
     * 参数：source —— 数据源。
     * 返回值：List<MacCmsClassDto> —— 分类表；失败时为空列表。
     */
    private suspend fun loadClasses(source: VideoSource): List<MacCmsClassDto> {
        classListCache.get(source.key)?.let { return it }
        val classes = runCatching {
            macCms.query(source.api, mapOf("ac" to "list", "pg" to "1")).classes
        }.getOrDefault(emptyList())
        if (classes.isNotEmpty()) {
            classListCache.put(source.key, classes, CLASS_CACHE_TTL_MS)
        }
        return classes
    }

    /**
     * 把采集条目转成界面摘要。
     *
     * 参数：source —— 所属数据源。
     * 返回值：VideoSummary? —— 片名为空时返回 null。
     */
    private fun MacCmsVodDto.toSummary(source: VideoSource): VideoSummary? {
        val name = vodName.trim()
        if (name.isEmpty()) return null
        return VideoSummary(
            sourceKey = source.key,
            sourceName = source.name,
            vodId = vodId,
            title = name,
            poster = vodPic,
            remarks = vodRemarks,
            year = vodYear,
            typeName = typeName,
            score = vodScore
        )
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

    private companion object {
        const val MAX_SUB_CATEGORIES: Int = 24
        const val CLASS_CACHE_TTL_MS: Long = 30 * 60 * 1000L
    }
}
