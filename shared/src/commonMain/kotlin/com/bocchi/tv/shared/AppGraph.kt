package com.bocchi.tv.shared

import com.bocchi.tv.data.remote.MacCmsClient
import com.bocchi.tv.data.remote.createHttpClient
import com.bocchi.tv.data.source.SourceStore
import com.bocchi.tv.data.video.MediaUrlResolver
import com.bocchi.tv.data.video.VideoRepository
import com.russhwolf.settings.Settings

/**
 * 应用依赖容器（跨平台单例）。
 *
 * 功能：集中构建并复用网络客户端、数据源仓库、解析器与影片仓库，避免各界面各自 new 一份导致缓存失效。
 *      解析接口地址存于本地设置，修改后立即对后续解析生效。
 * 参数：无（object 单例）。
 * 返回值：无。
 */
object AppGraph {

    /** 跨平台键值存储（桌面为 Java Preferences，iOS 为 NSUserDefaults）。 */
    private val settings: Settings by lazy { Settings() }

    /** 全局 HTTP 客户端（连接池复用）。 */
    private val httpClient by lazy { createHttpClient() }

    /** 数据源仓库。 */
    val sourceStore: SourceStore by lazy { SourceStore(settings) }

    /** 播放地址解析器，解析接口地址每次解析时实时读取，改配置无需重启。 */
    private val resolver: MediaUrlResolver by lazy {
        MediaUrlResolver(httpClient) { getParseApiUrl() }
    }

    /** 影片仓库。 */
    val videoRepository: VideoRepository by lazy {
        VideoRepository(MacCmsClient(httpClient), resolver, sourceStore)
    }

    /**
     * 读取当前配置的解析接口地址。
     *
     * 参数：无。
     * 返回值：String —— 接口地址；未配置时为空串。
     */
    fun getParseApiUrl(): String = settings.getString(PARSE_API_KEY, "")

    /**
     * 保存解析接口地址。
     *
     * 参数：url —— 接口地址（空串表示关闭外部解析）。
     * 返回值：无。
     */
    fun setParseApiUrl(url: String) {
        settings.putString(PARSE_API_KEY, url.trim())
    }

    /**
     * 校验解析接口地址格式。
     *
     * 功能：转发给解析器统一校验，保证界面与数据层判定一致。
     * 参数：url —— 待校验地址。
     * 返回值：String? —— 合法返回 null，否则返回错误文案。
     */
    fun validateParseApi(url: String): String? = resolver.validateParseApi(url)

    /** 解析接口地址的存储键。 */
    private const val PARSE_API_KEY: String = "parse_api_url"
}
