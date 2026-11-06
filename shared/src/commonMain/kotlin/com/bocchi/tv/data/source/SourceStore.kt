package com.bocchi.tv.data.source

import com.russhwolf.settings.Settings

/**
 * 数据源定义。
 *
 * 参数：key 唯一键（开关与去重都依赖它）；name 显示名；api 采集接口地址；
 *      searchParam 搜索参数名（多数源为 wd）；enabled 是否启用。
 * 返回值：无（数据类）。
 */
data class VideoSource(
    val key: String,
    val name: String,
    val api: String,
    val searchParam: String = "wd",
    val enabled: Boolean = true
)

/**
 * 内置数据源清单。
 *
 * 功能：与安卓版 assets 中的清单保持一致（14 个实测可用源），此处以常量形式内置，
 *      便于桌面与 iOS 在无资源读取能力的情况下也能直接使用。
 * 参数：无（object 单例）。
 * 返回值：无。
 */
object BuiltinSources {

    /** 全部内置源（顺序即界面展示顺序）。 */
    val ALL: List<VideoSource> = listOf(
        VideoSource("bfzy", "暴风资源", "https://bfzyapi.com/api.php/provide/vod/"),
        VideoSource("dbzy", "豆瓣资源", "https://dbzy.tv/api.php/provide/vod/"),
        VideoSource("jszy", "极速资源", "https://jszyapi.com/api.php/provide/vod/"),
        VideoSource("lziapi", "量子资源", "https://cj.lziapi.com/api.php/provide/vod/"),
        VideoSource("ffzy", "非凡影视", "https://ffzy5.tv/api.php/provide/vod/"),
        VideoSource("zy360", "360资源", "https://360zy.com/api.php/provide/vod/"),
        VideoSource("tyys", "天涯资源", "https://tyyszy.com/api.php/provide/vod/"),
        VideoSource("dytt", "电影天堂资源", "https://caiji.dyttzyapi.com/api.php/provide/vod/"),
        VideoSource("juliang", "巨量资源", "https://api.juliang.live/api/provide/vod/"),
        VideoSource("mtzy", "茅台资源", "https://mtzy5.com/api.php/provide/vod/"),
        VideoSource("jinying", "金鹰资源", "https://jinyingzy.tv/api.php/provide/vod/"),
        VideoSource("huya", "虎牙资源", "https://huyazy.cc/api.php/provide/vod/"),
        VideoSource("haohua", "豪华资源", "https://haohuazy.tv/api.php/provide/vod/"),
        VideoSource("subo", "速播资源", "https://subozy.tv/api.php/provide/vod/")
    )
}

/**
 * 数据源仓库（跨平台）。
 *
 * 功能：提供内置源列表，并把用户对每个源的启用/停用状态持久化到本地设置，
 *      使桌面与移动端都能记住用户的选择。
 * 参数：settings —— 跨平台键值存储。
 * 返回值：无（服务类）。
 */
class SourceStore(private val settings: Settings) {

    /**
     * 读取全部数据源（含用户开关状态）。
     *
     * 参数：无。
     * 返回值：List<VideoSource> —— 按内置顺序排列的源列表。
     */
    fun all(): List<VideoSource> = BuiltinSources.ALL.map { source ->
        source.copy(enabled = settings.getBoolean(enabledKey(source.key), source.enabled))
    }

    /**
     * 读取已启用的数据源。
     *
     * 参数：无。
     * 返回值：List<VideoSource> —— 参与搜索与聚合的源。
     */
    fun enabled(): List<VideoSource> = all().filter { it.enabled }

    /**
     * 设置某个源的启用状态。
     *
     * 参数：key —— 源唯一键；enabled —— 是否启用。
     * 返回值：无。
     */
    fun setEnabled(key: String, enabled: Boolean) {
        settings.putBoolean(enabledKey(key), enabled)
    }

    /**
     * 生成某个源开关的存储键。
     *
     * 参数：key —— 源唯一键。
     * 返回值：String —— 存储键。
     */
    private fun enabledKey(key: String): String = "source_enabled_$key"
}
