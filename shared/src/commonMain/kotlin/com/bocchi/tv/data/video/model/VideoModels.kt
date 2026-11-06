package com.bocchi.tv.data.video.model

/**
 * 播放剧集（一个可播放条目）。
 *
 * 参数：name 集数名称，如「第1集」「正片」；url 播放地址（m3u8 或 mp4）。
 * 返回值：无（数据类）。
 */
data class PlayEpisode(
    val name: String,
    val url: String
)

/**
 * 播放线路。
 *
 * 功能：一条线路对应「一个数据源内的一条播放线路」；多源聚合时同一影片会有多条线路供切换。
 * 参数：sourceKey 数据源唯一键；sourceName 数据源名称；lineName 源内线路名；episodes 剧集列表。
 * 返回值：无（数据类）。
 */
data class PlayLine(
    val sourceKey: String,
    val sourceName: String,
    val lineName: String,
    val episodes: List<PlayEpisode>
) {
    /** 线路展示名：单线路源直接显示源名，多线路源显示「源名·线路名」。 */
    val displayName: String get() = if (lineName.isBlank()) sourceName else "$sourceName·$lineName"
}

/**
 * 单个数据源命中的影片（聚合前）。
 *
 * 参数：sourceKey 数据源唯一键；sourceName 数据源名称；vodId 源内影片 ID；title 片名；
 *      poster 海报地址；remarks 备注；year 年份；typeName 分类名；score 评分。
 * 返回值：无（数据类）。
 */
data class VideoSummary(
    val sourceKey: String,
    val sourceName: String,
    val vodId: String,
    val title: String,
    val poster: String,
    val remarks: String,
    val year: String,
    val typeName: String,
    val score: String
) {
    /** 源内唯一标识，用于详情页精确回查。 */
    val uniqueKey: String get() = "$sourceKey:$vodId"
}

/**
 * 聚合后的影片条目。
 *
 * 功能：把多个数据源命中的同一部影片合并为一条，展示时只呈现最佳信息，详情页可切换各源线路。
 * 参数：title 片名；poster 海报；remarks 备注；year 年份；typeName 分类名；score 评分；
 *      matches 命中该影片的全部源结果（按源顺序）。
 * 返回值：无（数据类）。
 */
data class AggregatedVideo(
    val title: String,
    val poster: String,
    val remarks: String,
    val year: String,
    val typeName: String,
    val score: String,
    val matches: List<VideoSummary>
) {
    /** 命中的源数量，界面上以「N 个源」提示可选线路数。 */
    val sourceCount: Int get() = matches.size
}

/**
 * 影片详情。
 *
 * 参数：title 片名；poster 海报；remarks 备注；year 年份；area 地区；lang 语言；
 *      actor 主演；director 导演；score 评分；typeName 分类名；content 剧情简介（已清洗 HTML）；
 *      lines 可切换的播放线路。
 * 返回值：无（数据类）。
 */
data class VideoDetail(
    val title: String,
    val poster: String,
    val remarks: String,
    val year: String,
    val area: String,
    val lang: String,
    val actor: String,
    val director: String,
    val score: String,
    val typeName: String,
    val content: String,
    val lines: List<PlayLine>
)

/**
 * 聚合查询进度快照。
 *
 * 功能：聚合采用流式返回，每完成一个数据源就推出一份快照，界面据此实时刷新。
 * 参数：items 当前已归并的结果；finishedSources 已完成的源数量；totalSources 参与聚合的源总数；
 *      emptySources 无结果或请求失败的源数量。
 * 返回值：无（数据类）。
 */
data class AggregateResult(
    val items: List<AggregatedVideo> = emptyList(),
    val finishedSources: Int = 0,
    val totalSources: Int = 0,
    val emptySources: Int = 0
) {
    /** 是否已经聚合完毕。 */
    val isComplete: Boolean get() = totalSources > 0 && finishedSources >= totalSources

    /** 是否处于「有源参与但一条结果都没有」的空状态。 */
    val isEmpty: Boolean get() = isComplete && items.isEmpty()
}
