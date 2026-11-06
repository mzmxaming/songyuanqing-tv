package com.bocchi.tv.data.video.model

/**
 * 统一分类（跨源）。
 *
 * 功能：把各数据源命名不一致的分类归一为首页标签；具体到某个源时，再用 [matches] 在该源的分类表中
 *      找到对应的分类节点，进而拿到该节点的分类 ID 与其二级子分类。
 * 参数：displayName 界面展示名；preferredNames 优先匹配的分类名（先精确、后包含）；
 *      excludedNameParts 需要排除的名称片段（如「电影解说」「短剧大全」不应归入电影/剧集）。
 * 返回值：无（枚举）。
 */
enum class BrowseCategory(
    val displayName: String,
    private val preferredNames: List<String>,
    private val excludedNameParts: List<String>
) {
    /** 电影。 */
    MOVIE(
        displayName = "电影",
        preferredNames = listOf("电影片", "电影"),
        excludedNameParts = listOf("解说", "预告", "短剧", "动漫", "漫剧", "动画")
    ),

    /** 剧集（连续剧 / 电视剧）。 */
    SERIES(
        displayName = "剧集",
        preferredNames = listOf("连续剧", "电视剧", "剧集", "剧"),
        excludedNameParts = listOf("短剧", "喜剧", "悲剧", "话剧", "音乐剧", "舞台剧", "预告")
    ),

    /** 短剧（竖屏短剧为主）。 */
    SHORT_PLAY(
        displayName = "短剧",
        preferredNames = listOf("短剧大全", "爽文短剧", "短剧场", "短剧"),
        excludedNameParts = listOf("预告")
    ),

    /** 综艺。 */
    VARIETY(
        displayName = "综艺",
        preferredNames = listOf("综艺片", "综艺"),
        excludedNameParts = listOf("短剧")
    ),

    /** 动漫。 */
    ANIME(
        displayName = "动漫",
        preferredNames = listOf("动漫片", "动漫", "动画片", "动画"),
        excludedNameParts = listOf("短剧", "漫剧", "电影")
    ),

    /** 纪录片。 */
    DOCUMENTARY(
        displayName = "纪录片",
        preferredNames = listOf("纪录片", "记录片"),
        excludedNameParts = listOf("短剧")
    );

    /**
     * 判断某个采集源的分类名是否属于本统一分类。
     *
     * 功能：先排除含敏感片段的名称，再做精确匹配，最后退化为包含匹配。
     * 参数：className —— 数据源返回的分类名称，如「电影片」「连续剧」。
     * 返回值：Boolean —— true 表示该分类名归入本分类。
     */
    fun matches(className: String): Boolean {
        val name = className.trim()
        if (name.isEmpty()) return false
        if (excludedNameParts.any { name.contains(it) }) return false
        if (preferredNames.any { it == name }) return true
        return preferredNames.any { name.contains(it) }
    }
}
