package com.bocchi.tv.data.video

import com.bocchi.tv.data.video.model.PlayEpisode

/**
 * 播放地址与文本解析工具。
 *
 * 功能：处理苹果CMS 采集接口的播放字段格式、简介 HTML 清洗，以及片名归一化（用于多源去重归并）。
 *      全部为纯函数，便于单元测试，可跨平台复用。
 * 参数：无（object 单例）。
 * 返回值：无。
 */
object PlayUrlParser {

    /** 线路分隔符：vod_play_from 与 vod_play_url 中多条线路以 `$$$` 分隔。 */
    private const val LINE_SEPARATOR = "$$$"

    /** 集数分隔符：同一条线路内多集以 `#` 分隔。 */
    private const val EPISODE_SEPARATOR = "#"

    /** 集数与地址分隔符：`第1集$https://...` 形式。 */
    private const val NAME_URL_SEPARATOR = "$"

    /** 连续空白（含全角空格）。 */
    private val WHITESPACE_REGEX = Regex("[\\s\\u3000]+")

    /** 常见标点（半角 + 中日韩全角标点），去除后仅保留文字与数字用于比对。 */
    private val PUNCTUATION_REGEX = Regex("[\\p{Punct}，。、！？：；「」『』（）〔〕《》〈〉【】·—…～“”‘’]+")

    /** 片名中的年份括号（如「(2023)」），归并时忽略，避免同片因年份写法不同而分成两条。 */
    private val BRACKETED_YEAR_REGEX = Regex("[（(\\[【]\\s*(19|20)\\d{2}\\s*[)）\\]】]")

    /** 片名中常见的画质/语言/状态修饰词，归并时忽略。 */
    private val NOISE_TOKEN_REGEX = Regex(
        "(国语|粤语|中字|中文字幕|双语|高清|超清|蓝光|bluray|4k|1080p|720p|完结|全集|抢先|枪版|hdvd)"
    )

    /** HTML 换行标签，清洗简介时转换为换行。 */
    private val HTML_BREAK_REGEX = Regex("(?i)<\\s*(br|/p|/div)\\s*/?\\s*>")

    /** 其余 HTML 标签。 */
    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    /**
     * 源内播放线路解析结果。
     *
     * 参数：name 线路名（如 bfzym3u8，可能为空）；episodes 该线路的剧集。
     * 返回值：无（数据类）。
     */
    data class ParsedLine(val name: String, val episodes: List<PlayEpisode>)

    /**
     * 解析播放线路。
     *
     * 功能：把 `vod_play_from` 与 `vod_play_url` 按 `$$$` 拆分后逐条对应解析；两者数量不一致时按较小者截断，
     *      线路名为空时使用空串（界面回退为源名）。
     * 参数：playFrom —— 线路名字段；playUrl —— 播放地址字段。
     * 返回值：List<ParsedLine> —— 解析出的线路列表（已过滤没有有效剧集的线路）。
     */
    fun parseLines(playFrom: String, playUrl: String): List<ParsedLine> {
        if (playUrl.isBlank()) return emptyList()
        val urlGroups = playUrl.split(LINE_SEPARATOR)
        val nameGroups = if (playFrom.isBlank()) emptyList() else playFrom.split(LINE_SEPARATOR)
        return urlGroups.mapIndexedNotNull { index, group ->
            val episodes = parseEpisodes(group)
            if (episodes.isEmpty()) {
                null
            } else {
                ParsedLine(
                    name = nameGroups.getOrNull(index)?.trim().orEmpty(),
                    episodes = episodes
                )
            }
        }
    }

    /**
     * 解析单条线路下的剧集列表。
     *
     * 功能：按 `#` 拆分后逐集按 `$` 拆名称与地址；缺少名称时按**原始顺序**命名为「第N集」，
     *      跳过没有地址的条目。
     * 参数：group —— 单条线路的原始地址串。
     * 返回值：List<PlayEpisode> —— 剧集列表。
     */
    fun parseEpisodes(group: String): List<PlayEpisode> {
        if (group.isBlank()) return emptyList()
        val episodes = mutableListOf<PlayEpisode>()
        group.split(EPISODE_SEPARATOR).forEachIndexed { index, entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            val separatorIndex = trimmed.indexOf(NAME_URL_SEPARATOR)
            val rawName = if (separatorIndex >= 0) trimmed.substring(0, separatorIndex) else ""
            val url = if (separatorIndex >= 0) trimmed.substring(separatorIndex + 1) else trimmed
            if (url.isBlank()) return@forEachIndexed
            val name = rawName.trim().ifBlank { "第${index + 1}集" }
            episodes += PlayEpisode(name = name, url = url.trim())
        }
        return episodes
    }

    /**
     * 清洗简介文本。
     *
     * 功能：把换行标签转为换行、去除其余 HTML 标签、还原常见实体、合并多余空白，使简介可直接展示。
     * 参数：raw —— 采集接口返回的简介原文（含 HTML）。
     * 返回值：String —— 可展示的纯文本简介。
     */
    fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        var text = HTML_BREAK_REGEX.replace(raw, "\n")
        text = HTML_TAG_REGEX.replace(text, "")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
        text = text.lines().joinToString("\n") { line -> line.trim() }
        return text.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /**
     * 片名归一化。
     *
     * 功能：去掉年份括号、空白与标点、转小写、去掉画质/语言/完结等修饰词，用于把不同源对同一影片的
     *      不同写法归并到同一分组。
     * 参数：raw —— 原始片名。
     * 返回值：String —— 归一化后的片名（可能为空，调用方需回退使用原文）。
     */
    fun normalizeTitle(raw: String): String {
        if (raw.isBlank()) return ""
        var title = raw.trim().lowercase()
        title = BRACKETED_YEAR_REGEX.replace(title, "")
        title = WHITESPACE_REGEX.replace(title, "")
        title = PUNCTUATION_REGEX.replace(title, "")
        title = NOISE_TOKEN_REGEX.replace(title, "")
        return title
    }
}
