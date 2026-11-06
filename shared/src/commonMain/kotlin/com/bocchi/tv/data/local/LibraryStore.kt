package com.bocchi.tv.data.local

import com.bocchi.tv.data.remote.AppJson
import com.bocchi.tv.data.video.model.VideoSummary
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable

/**
 * 收藏记录（持久化结构）。
 *
 * 参数：sourceKey 源标识；sourceName 源名称；vodId 源内影片 ID；title 片名；poster 海报；
 *      remarks 备注；year 年份；typeName 分类名；score 评分。
 * 返回值：无（数据类）。
 */
@Serializable
data class StoredFavorite(
    val sourceKey: String = "",
    val sourceName: String = "",
    val vodId: String = "",
    val title: String = "",
    val poster: String = "",
    val remarks: String = "",
    val year: String = "",
    val typeName: String = "",
    val score: String = ""
) {
    /** 唯一键：与 [VideoSummary.uniqueKey] 保持一致。 */
    val vodKey: String get() = "$sourceKey:$vodId"

    /** 还原成聚合条目可用的摘要对象。 */
    fun toSummary(): VideoSummary = VideoSummary(
        sourceKey = sourceKey,
        sourceName = sourceName,
        vodId = vodId,
        title = title,
        poster = poster,
        remarks = remarks,
        year = year,
        typeName = typeName,
        score = score
    )
}

/**
 * 播放历史记录（持久化结构）。
 *
 * 参数：见收藏字段，另加 episodeName 集数名、episodeIndex 集数下标、positionMs 已播放毫秒、
 *      durationMs 总时长毫秒。
 * 返回值：无（数据类）。
 */
@Serializable
data class StoredHistory(
    val sourceKey: String = "",
    val sourceName: String = "",
    val vodId: String = "",
    val title: String = "",
    val poster: String = "",
    val remarks: String = "",
    val year: String = "",
    val typeName: String = "",
    val score: String = "",
    val episodeName: String = "",
    val episodeIndex: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    /** 唯一键：与 [VideoSummary.uniqueKey] 保持一致。 */
    val vodKey: String get() = "$sourceKey:$vodId"

    /** 还原成聚合条目可用的摘要对象。 */
    fun toSummary(): VideoSummary = VideoSummary(
        sourceKey = sourceKey,
        sourceName = sourceName,
        vodId = vodId,
        title = title,
        poster = poster,
        remarks = remarks,
        year = year,
        typeName = typeName,
        score = score
    )
}

/**
 * 从摘要生成收藏/历史的基础字段。
 *
 * 参数：summary —— 影片摘要。
 * 返回值：StoredFavorite —— 只填了基础字段的记录。
 */
private fun VideoSummary.toFavorite(): StoredFavorite = StoredFavorite(
    sourceKey = sourceKey,
    sourceName = sourceName,
    vodId = vodId,
    title = title,
    poster = poster,
    remarks = remarks,
    year = year,
    typeName = typeName,
    score = score
)

/**
 * 收藏仓库（跨平台）。
 *
 * 功能：以 JSON 形式存入本地键值存储，按收藏时间倒序保存；同一影片（源 + ID）只保留一条。
 * 参数：settings —— 跨平台键值存储。
 * 返回值：无（服务类）。
 */
class FavoriteStore(private val settings: Settings) {

    /**
     * 读取全部收藏。
     *
     * 参数：无。
     * 返回值：List<StoredFavorite> —— 最近收藏在前。
     */
    fun all(): List<StoredFavorite> = read()

    /**
     * 判断某影片是否已收藏。
     *
     * 参数：vodKey —— 唯一键（源:影片ID）。
     * 返回值：Boolean —— 已收藏返回 true。
     */
    fun contains(vodKey: String): Boolean = read().any { it.vodKey == vodKey }

    /**
     * 切换收藏状态。
     *
     * 参数：summary —— 影片摘要。
     * 返回值：Boolean —— 切换后是否为「已收藏」。
     */
    fun toggle(summary: VideoSummary): Boolean {
        val current = read()
        val existing = current.any { it.vodKey == summary.uniqueKey }
        val next = if (existing) {
            current.filterNot { it.vodKey == summary.uniqueKey }
        } else {
            listOf(summary.toFavorite()) + current
        }
        write(next)
        return !existing
    }

    /**
     * 移除一条收藏。
     *
     * 参数：vodKey —— 唯一键。
     * 返回值：无。
     */
    fun remove(vodKey: String) {
        write(read().filterNot { it.vodKey == vodKey })
    }

    /** 清空收藏。 */
    fun clear() {
        write(emptyList())
    }

    /**
     * 读取并反序列化。
     *
     * 参数：无。
     * 返回值：List<StoredFavorite> —— 解析失败时返回空列表。
     */
    private fun read(): List<StoredFavorite> {
        val raw = settings.getString(KEY, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(StoredFavorite.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /**
     * 序列化并写入。
     *
     * 参数：items —— 收藏列表。
     * 返回值：无。
     */
    private fun write(items: List<StoredFavorite>) {
        val raw = AppJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(StoredFavorite.serializer()),
            items
        )
        settings.putString(KEY, raw)
    }

    private companion object {
        const val KEY: String = "favorites_json"
    }
}

/**
 * 播放历史仓库（跨平台）。
 *
 * 功能：保存每部影片最近一次的播放位置用于「继续观看」；同一影片只保留一条并置顶；
 *      最多保留 [MAX_ENTRIES] 条，避免无限增长。
 * 参数：settings —— 跨平台键值存储。
 * 返回值：无（服务类）。
 */
class PlayHistoryStore(private val settings: Settings) {

    /**
     * 读取全部历史。
     *
     * 参数：无。
     * 返回值：List<StoredHistory> —— 最近播放在前。
     */
    fun all(): List<StoredHistory> = read()

    /**
     * 查询某影片的播放记录。
     *
     * 参数：vodKey —— 唯一键。
     * 返回值：StoredHistory? —— 未看过返回 null。
     */
    fun find(vodKey: String): StoredHistory? = read().firstOrNull { it.vodKey == vodKey }

    /**
     * 记录一次播放进度。
     *
     * 功能：同一影片先移除旧记录再插到队首；播放位置与总时长为 0 时不写入，避免脏数据。
     * 参数：summary —— 影片摘要；episodeName —— 集数名；episodeIndex —— 集数下标；
     *      positionMs —— 已播放毫秒；durationMs —— 总时长毫秒。
     * 返回值：无。
     */
    fun record(
        summary: VideoSummary,
        episodeName: String,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0L) return
        val base = summary.toFavorite()
        val entry = StoredHistory(
            sourceKey = base.sourceKey,
            sourceName = base.sourceName,
            vodId = base.vodId,
            title = base.title,
            poster = base.poster,
            remarks = base.remarks,
            year = base.year,
            typeName = base.typeName,
            score = base.score,
            episodeName = episodeName,
            episodeIndex = episodeIndex,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs
        )
        val next = (listOf(entry) + read().filterNot { it.vodKey == entry.vodKey }).take(MAX_ENTRIES)
        write(next)
    }

    /**
     * 删除一条历史。
     *
     * 参数：vodKey —— 唯一键。
     * 返回值：无。
     */
    fun remove(vodKey: String) {
        write(read().filterNot { it.vodKey == vodKey })
    }

    /** 清空历史。 */
    fun clear() {
        write(emptyList())
    }

    /**
     * 读取并反序列化。
     *
     * 参数：无。
     * 返回值：List<StoredHistory> —— 解析失败时返回空列表。
     */
    private fun read(): List<StoredHistory> {
        val raw = settings.getString(KEY, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(StoredHistory.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /**
     * 序列化并写入。
     *
     * 参数：items —— 历史列表。
     * 返回值：无。
     */
    private fun write(items: List<StoredHistory>) {
        val raw = AppJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(StoredHistory.serializer()),
            items
        )
        settings.putString(KEY, raw)
    }

    private companion object {
        const val KEY: String = "play_history_json"
        const val MAX_ENTRIES: Int = 100
    }
}
