package com.bocchi.tv.data.source

import com.bocchi.tv.data.remote.AppJson
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

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

    /** 内置源 key 集合，用于界面区分「内置 / 自定义」。 */
    val KEYS: Set<String> = ALL.map { it.key }.toSet()
}

/**
 * 持久化的数据源条目。
 *
 * 参数：key 唯一键；name 显示名；api 接口地址；searchParam 搜索参数名。
 * 返回值：无（数据类）。
 */
@Serializable
private data class StoredSource(
    val key: String = "",
    val name: String = "",
    val api: String = "",
    val searchParam: String = "wd"
)

/**
 * 数据源仓库（跨平台）。
 *
 * 功能：维护内置源 + 自定义源 + 内置源覆盖（改名/改地址）+ 删除记录 + 开关状态，
 *      全部持久化到本地设置，使桌面与移动端行为一致。
 * 参数：settings —— 跨平台键值存储。
 * 返回值：无（服务类）。
 */
class SourceStore(private val settings: Settings) {

    /**
     * 读取全部数据源（含覆盖与开关状态）。
     *
     * 参数：无。
     * 返回值：List<VideoSource> —— 内置源在前、自定义源在后。
     */
    fun all(): List<VideoSource> {
        val removed = readRemoved()
        val overrides = readOverrides().associateBy { it.key }
        val builtins = BuiltinSources.ALL
            .filterNot { it.key in removed }
            .map { source ->
                val override = overrides[source.key]
                VideoSource(
                    key = source.key,
                    name = override?.name?.ifBlank { source.name } ?: source.name,
                    api = override?.api?.ifBlank { source.api } ?: source.api,
                    searchParam = override?.searchParam?.ifBlank { source.searchParam } ?: source.searchParam,
                    enabled = isEnabled(source.key, source.enabled)
                )
            }
        val customs = readCustoms().map { stored ->
            VideoSource(
                key = stored.key,
                name = stored.name,
                api = stored.api,
                searchParam = stored.searchParam,
                enabled = isEnabled(stored.key, true)
            )
        }
        return builtins + customs
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
     * 判断某源是否为内置源。
     *
     * 参数：key —— 源唯一键。
     * 返回值：Boolean —— 内置源返回 true。
     */
    fun isBuiltin(key: String): Boolean = key in BuiltinSources.KEYS

    /**
     * 新增自定义源。
     *
     * 功能：key 采用 `custom_序号` 形式，保证唯一且不与内置源冲突。
     * 参数：name —— 显示名；api —— 接口地址；searchParam —— 搜索参数名。
     * 返回值：Boolean —— 校验通过并写入返回 true；字段为空或地址重复返回 false。
     */
    fun addCustom(name: String, api: String, searchParam: String): Boolean {
        val trimmedApi = api.trim()
        if (name.isBlank() || trimmedApi.isBlank()) return false
        if (all().any { it.api.trim() == trimmedApi }) return false
        val customs = readCustoms()
        val nextIndex = (customs.size + 1)
        var key = "custom_$nextIndex"
        var suffix = nextIndex
        while (all().any { it.key == key }) {
            suffix += 1
            key = "custom_$suffix"
        }
        writeCustoms(
            customs + StoredSource(
                key = key,
                name = name.trim(),
                api = trimmedApi,
                searchParam = searchParam.trim().ifBlank { "wd" }
            )
        )
        return true
    }

    /**
     * 修改数据源。
     *
     * 功能：内置源写入「覆盖记录」，自定义源直接改自身记录；地址不可与其它源重复。
     * 参数：source —— 要保存的数据源（key 必须已存在）。
     * 返回值：Boolean —— 保存成功返回 true。
     */
    fun update(source: VideoSource): Boolean {
        val trimmedApi = source.api.trim()
        if (source.name.isBlank() || trimmedApi.isBlank()) return false
        if (all().any { it.key != source.key && it.api.trim() == trimmedApi }) return false

        if (isBuiltin(source.key)) {
            val overrides = readOverrides().filterNot { it.key == source.key } +
                StoredSource(source.key, source.name.trim(), trimmedApi, source.searchParam.trim().ifBlank { "wd" })
            writeOverrides(overrides)
        } else {
            val customs = readCustoms().map {
                if (it.key == source.key) {
                    StoredSource(source.key, source.name.trim(), trimmedApi, source.searchParam.trim().ifBlank { "wd" })
                } else {
                    it
                }
            }
            writeCustoms(customs)
        }
        return true
    }

    /**
     * 删除数据源。
     *
     * 功能：内置源记入「已删除」清单，避免下次启动被自动补回；自定义源直接移除。
     * 参数：key —— 源唯一键。
     * 返回值：无。
     */
    fun remove(key: String) {
        if (isBuiltin(key)) {
            writeRemoved(readRemoved() + key)
        } else {
            writeCustoms(readCustoms().filterNot { it.key == key })
        }
        settings.remove(enabledKey(key))
    }

    /**
     * 恢复全部内置源。
     *
     * 功能：清空删除记录与覆盖记录，让内置源回到默认状态（自定义源保留）。
     * 参数：无。
     * 返回值：无。
     */
    fun restoreBuiltins() {
        settings.remove(REMOVED_KEY)
        settings.remove(OVERRIDE_KEY)
        BuiltinSources.ALL.forEach { settings.remove(enabledKey(it.key)) }
    }

    /**
     * 读取某源的开关状态。
     *
     * 参数：key —— 源唯一键；default —— 未设置时的默认值。
     * 返回值：Boolean —— 是否启用。
     */
    private fun isEnabled(key: String, default: Boolean): Boolean =
        settings.getBoolean(enabledKey(key), default)

    /**
     * 生成某个源开关的存储键。
     *
     * 参数：key —— 源唯一键。
     * 返回值：String —— 存储键。
     */
    private fun enabledKey(key: String): String = "source_enabled_$key"

    /**
     * 读取自定义源。
     *
     * 参数：无。
     * 返回值：List<StoredSource> —— 解析失败时为空列表。
     */
    private fun readCustoms(): List<StoredSource> = decode(settings.getString(CUSTOM_KEY, ""))

    /**
     * 写入自定义源。
     *
     * 参数：items —— 列表。
     * 返回值：无。
     */
    private fun writeCustoms(items: List<StoredSource>) {
        settings.putString(CUSTOM_KEY, encode(items))
    }

    /**
     * 读取内置源覆盖记录。
     *
     * 参数：无。
     * 返回值：List<StoredSource> —— 解析失败时为空列表。
     */
    private fun readOverrides(): List<StoredSource> = decode(settings.getString(OVERRIDE_KEY, ""))

    /**
     * 写入内置源覆盖记录。
     *
     * 参数：items —— 列表。
     * 返回值：无。
     */
    private fun writeOverrides(items: List<StoredSource>) {
        settings.putString(OVERRIDE_KEY, encode(items))
    }

    /**
     * 读取已删除的内置源 key。
     *
     * 参数：无。
     * 返回值：Set<String> —— key 集合。
     */
    private fun readRemoved(): Set<String> {
        val raw = settings.getString(REMOVED_KEY, "")
        if (raw.isBlank()) return emptySet()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(String.serializer()), raw).toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * 写入已删除的内置源 key。
     *
     * 参数：keys —— key 集合。
     * 返回值：无。
     */
    private fun writeRemoved(keys: Set<String>) {
        settings.putString(REMOVED_KEY, AppJson.encodeToString(ListSerializer(String.serializer()), keys.toList()))
    }

    /**
     * 反序列化数据源列表。
     *
     * 参数：raw —— JSON 文本。
     * 返回值：List<StoredSource> —— 解析失败时为空列表。
     */
    private fun decode(raw: String): List<StoredSource> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(StoredSource.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /**
     * 序列化数据源列表。
     *
     * 参数：items —— 列表。
     * 返回值：String —— JSON 文本。
     */
    private fun encode(items: List<StoredSource>): String =
        AppJson.encodeToString(ListSerializer(StoredSource.serializer()), items)

    private companion object {
        const val CUSTOM_KEY: String = "custom_sources_json"
        const val OVERRIDE_KEY: String = "builtin_source_overrides_json"
        const val REMOVED_KEY: String = "removed_builtin_keys"
    }
}
