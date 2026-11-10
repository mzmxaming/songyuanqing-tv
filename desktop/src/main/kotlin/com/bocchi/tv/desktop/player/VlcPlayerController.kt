package com.bocchi.tv.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

/** libVLC 动态库的候选文件名（Windows / macOS / Linux）。 */
private val VLC_LIBRARY_NAMES = listOf("libvlc.dll", "libvlc.dylib", "libvlc.so", "libvlc.so.5")

/** 请求媒体时使用的浏览器 UA，规避部分源站对播放器默认 UA 的拦截。 */
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

/**
 * 定位随包内置的 libVLC 目录。
 *
 * 功能：依次尝试开发期系统属性、安装包资源目录、便携版目录；目录内需存在 libVLC 动态库。
 * 参数：无。
 * 返回值：File? —— 命中返回目录，未内置返回 null。
 */
private fun resolveBundledVlcDirectory(): File? {
    val candidates = buildList {
        System.getProperty("bundled.vlc.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { add(File(it)) }
        System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { add(File(it, "vlc")) }
        // 便携解压版兜底：<安装目录>/app/resources/vlc
        System.getProperty("jpackage.app-path")
            ?.let { File(it).parentFile }
            ?.let { add(File(it, "app/resources/vlc")) }
    }
    return candidates.firstOrNull { candidate ->
        if (!candidate.isDirectory) return@firstOrNull false
        val libDir = File(candidate, "lib").takeIf { it.isDirectory } ?: candidate
        VLC_LIBRARY_NAMES.any { File(libDir, it).isFile }
    }
}

/**
 * VLC 播放器封装（桌面端）。
 *
 * 功能：用 libVLC 播放 HLS / mp4 等地址。画面通过 VLCJ 的「回调渲染」绘制到 Swing 面板上，
 *      再由 Compose 的 SwingPanel 承载——不使用 AWT 原生子窗口，避免窗口缩放/最大化后画面覆盖界面。
 *      初始化时优先使用安装包内置的 libVLC（无需用户安装 VLC），未内置时回退探测本机 VLC；
 *      两者都不可用则返回可读提示，由界面展示而不是白屏。
 * 参数：无（构造时仅创建面板，引擎在 [initialize] 中启动）。
 * 返回值：无（服务类）。
 */
class VlcPlayerController {

    /**
     * 播放器初始化结果。
     *
     * 参数：available 是否可用；message 不可用时的说明。
     * 返回值：无（数据类）。
     */
    data class InitResult(val available: Boolean, val message: String = "")

    /** 承载视频画面的 Swing 面板（引用稳定，供 Compose SwingPanel 承载）。 */
    val videoSurface: JPanel = JPanel(BorderLayout()).apply {
        background = java.awt.Color.BLACK
        isOpaque = true
    }

    /** VLCJ 的回调渲染组件（Windows / macOS）：解码后把帧画到 Swing 面板上。 */
    private var component: CallbackMediaPlayerComponent? = null

    /** VLCJ 的嵌入式组件（Linux）：使用 xcb_x11 原生窗口输出。
     *  实测 VLC 3.0.9.2 不会触发回调渲染的 lock/unlock，只有原生窗口能出画面。 */
    private var embeddedComponent: EmbeddedMediaPlayerComponent? = null

    /** 底层播放器实例。 */
    private var player: EmbeddedMediaPlayer? = null

    /** 是否已成功初始化。 */
    var isAvailable: Boolean = false
        private set

    /** 当前使用的内置 libVLC 目录；为空表示使用本机安装的 VLC。 */
    var bundledDirectory: File? = null
        private set

    /**
     * 初始化播放引擎。
     *
     * 功能：优先加载安装包内置的 libVLC 并创建播放器，失败时回退到本机 VLC；重复调用直接返回成功。
     * 参数：无。
     * 返回值：InitResult —— 成功或失败说明。
     */
    fun initialize(): InitResult {
        if (isAvailable) return InitResult(true)

        val bundled = resolveBundledVlcDirectory()
        if (bundled != null) {
            val result = runCatching {
                val libDir = File(bundled, "lib").takeIf { it.isDirectory } ?: bundled
                startEngine(libDir, File(bundled, "plugins").takeIf { it.isDirectory })
                println("libVLC 使用内置运行库：${loadedLibraryPath() ?: libDir.absolutePath}")
                InitResult(true)
            }.getOrElse { error ->
                InitResult(false, "内置播放组件启动失败：${error.message ?: "未知错误"}")
            }
            if (result.available) {
                bundledDirectory = bundled
                return result
            }
        }

        val discovered = runCatching { NativeDiscovery().discover() }.getOrDefault(false)
        if (!discovered) {
            return InitResult(
                false,
                "未找到可用的播放组件：安装包内置运行库缺失，且本机未安装 VLC。" +
                    "请重新安装应用，或安装 VLC（videolan.org）后重启本程序。"
            )
        }
        return runCatching {
            startEngine(null, null)
            println("libVLC 使用系统安装：${loadedLibraryPath() ?: "未知路径"}")
            InitResult(true)
        }.getOrElse { error ->
            InitResult(false, "播放引擎启动失败：${error.message ?: "未知错误"}")
        }
    }

    /**
     * 创建 libVLC 实例并把画面接入 [videoSurface]。
     *
     * 参数：libDir 动态库目录（为 null 表示交给 VLCJ 自行探测）；
     *      pluginsDir 插件目录（为 null 表示使用 VLC 默认插件路径）。
     * 返回值：无。
     */
    private fun startEngine(libDir: File?, pluginsDir: File?) {
        if (libDir != null) {
            System.setProperty("jna.library.path", libDir.absolutePath)
        }
        val vlcArgs = buildList {
            if (pluginsDir != null) {
                // 插件目录只能通过 VLC_PLUGIN_PATH 指定：
                // VLC 3.0.9.x 已移除 --plugin-path 选项，传了会让 libvlc_new 直接返回 NULL。
                applyEnv("VLC_PLUGIN_PATH", pluginsDir.absolutePath)
                // Linux 内置包把 lua 等运行数据放在 <vlc>/share/vlc 下
                val dataDir = File(pluginsDir.parentFile, "share/vlc")
                if (dataDir.isDirectory) {
                    applyEnv("VLC_DATA_PATH", dataDir.absolutePath)
                }
            }
            add("--no-video-title-show")
            if (isLinux()) {
                // 与 Compose/Skiko 同进程时避免 OpenGL 上下文冲突：强制 X11 原生窗口输出 + 软件解码
                add("--vout=xcb_x11")
                add("--avcodec-hw=none")
            }
            // 诊断开关：设置 TV_VLC_DEBUG=1 时输出 libVLC 详细日志（控制台，带对象地址，便于区分并发输入）
            if (System.getenv("TV_VLC_DEBUG") != null) {
                add("--verbose=2")
            }
        }
        val created = if (isLinux()) {
            // Linux 用嵌入式 X11 原生窗口输出，并配合打包时移除的 GL 输出插件避免与 Compose 的 GL 上下文冲突
            EmbeddedMediaPlayerComponent(*vlcArgs.toTypedArray()).also { embedded ->
                embeddedComponent = embedded
                videoSurface.add(embedded, BorderLayout.CENTER)
                player = embedded.mediaPlayer()
            }
        } else {
            CallbackMediaPlayerComponent(*vlcArgs.toTypedArray()).also { callback ->
                component = callback
                videoSurface.add(callback.videoSurfaceComponent(), BorderLayout.CENTER)
                player = callback.mediaPlayer()
            }
        }
        videoSurface.revalidate()
        isAvailable = true
    }

    /**
     * 设置进程级环境变量。
     *
     * 功能：libVLC 在初始化时读取 VLC_PLUGIN_PATH / VLC_DATA_PATH，Java 无法通过 System.getenv 修改，
     *      因此走 libc 的 setenv。部分精简系统没有 libc.so（开发包才提供），优先按 soname 加载 libc.so.6。
     * 参数：name —— 变量名；value —— 变量值。
     * 返回值：无。
     */
    private fun applyEnv(name: String, value: String) {
        if (RuntimeUtil.isWindows()) return
        val libc = runCatching { Native.load("libc.so.6", LibC::class.java) }
            .recoverCatching { Native.load("c", LibC::class.java) }
            .getOrNull() ?: run {
            println("[vlc] 无法加载 libc，环境变量 $name 未设置")
            return
        }
        val result = runCatching { libc.setenv(name, value, 1) }.getOrDefault(-1)
        val readBack = runCatching { libc.getenv(name)?.getString(0) }.getOrNull()
        println("[vlc] $name=$value（setenv=$result，回读=$readBack）")
    }

    /**
     * libc 环境变量接口（仅 macOS / Linux 使用）。
     *
     * 参数：无。
     * 返回值：无（JNA 映射接口）。
     */
    private interface LibC : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int

        fun getenv(name: String): com.sun.jna.Pointer?
    }

    /**
     * 判断当前是否 Linux 平台。
     *
     * 参数：无。
     * 返回值：Boolean —— 是 Linux 返回 true。
     */
    private fun isLinux(): Boolean = System.getProperty("os.name").lowercase().contains("linux")

    /**
     * 读取当前实际加载的 libVLC 动态库路径。
     *
     * 参数：无。
     * 返回值：String? —— 路径；读取失败返回 null。
     */
    private fun loadedLibraryPath(): String? = runCatching {
        NativeLibrary.getInstance(RuntimeUtil.getLibVlcLibraryName()).file.absolutePath
    }.getOrNull()

    /**
     * 播放指定地址。
     *
     * 参数：url —— 可播放地址（m3u8 / mp4）；referer —— 可选的 Referer，用于通过部分源站的防盗链校验。
     * 返回值：Boolean —— 是否已提交播放。
     */
    fun play(url: String, referer: String? = null): Boolean {
        val current = player ?: return false
        println("[play] 媒体地址=$url")
        println("[play] 画面面板=${videoSurface.width}x${videoSurface.height}，子组件数=${videoSurface.componentCount}")
        return runCatching {
            // 部分源站会按 UA 拦截播放器默认标识（VLC/3.0.x LibVLC/3.0.x），统一改用浏览器 UA
            val withOptions = System.getenv("TV_NO_MEDIA_OPTS") == null
            val options = buildList {
                if (withOptions) {
                    add(":http-user-agent=$BROWSER_USER_AGENT")
                    if (!referer.isNullOrBlank()) add(":http-referrer=$referer")
                }
            }
            // Linux 内置 libVLC 解析 HLS 相对密钥地址时会拼出 https://host: 导致解复用失败，
            // 先把清单里的相对地址改写成绝对地址；非 m3u8 或失败时原样播放。
            val target = if (isLinux() && System.getenv("TV_NO_HLS_REWRITE") == null && url.contains(".m3u8")) {
                HlsManifestRewriter.rewrite(url, referer) ?: url
            } else {
                url
            }
            println("[play] 实际播放=$target")
            println("[play] 媒体选项=$options")
            current.media().play(target, *options.toTypedArray())
            true
        }.getOrDefault(false)
    }

    /** 暂停播放。 */
    fun pause() {
        runCatching { player?.controls()?.setPause(true) }
    }

    /** 继续播放。 */
    fun resume() {
        runCatching { player?.controls()?.setPause(false) }
    }

    /**
     * 判断是否正在播放。
     *
     * 参数：无。
     * 返回值：Boolean —— 播放中返回 true。
     */
    fun isPlaying(): Boolean = runCatching { player?.status()?.isPlaying == true }.getOrDefault(false)

    /** 停止播放。 */
    fun stop() {
        runCatching { player?.controls()?.stop() }
    }

    /**
     * 读取当前播放进度。
     *
     * 参数：无。
     * 返回值：Pair<Long, Long> —— 已播放毫秒数与总时长毫秒数（未知时为 0）。
     */
    fun progress(): Pair<Long, Long> {
        val current = player ?: return 0L to 0L
        return runCatching { current.status().time() to current.status().length() }
            .getOrDefault(0L to 0L)
    }

    /**
     * 拖动到指定进度。
     *
     * 参数：position —— 0f~1f 的播放位置。
     * 返回值：无。
     */
    fun seekTo(position: Float) {
        runCatching { player?.controls()?.setPosition(position.coerceIn(0f, 1f)) }
    }

    /**
     * 按毫秒跳转。
     *
     * 参数：ms —— 目标时间（毫秒，负数按 0 处理）。
     * 返回值：无。
     */
    fun seekToMillis(ms: Long) {
        runCatching { player?.controls()?.setTime(ms.coerceAtLeast(0L)) }
    }

    /**
     * 读取当前视频分辨率。
     *
     * 功能：供界面判断画幅（竖屏短剧需要按 9:16 展示），未出画面时返回 null。
     * 参数：无。
     * 返回值：Pair<Int, Int>? —— 宽高；未知时返回 null。
     */
    fun videoSize(): Pair<Int, Int>? = runCatching {
        val dimension = player?.video()?.videoDimension()
        if (dimension == null || dimension.width <= 0 || dimension.height <= 0) {
            null
        } else {
            dimension.width to dimension.height
        }
    }.getOrNull()

    /**
     * 设置音量。
     *
     * 参数：volume —— 0~100。
     * 返回值：无。
     */
    fun setVolume(volume: Int) {
        runCatching { player?.audio()?.setVolume(volume.coerceIn(0, 100)) }
    }

    /**
     * 注册播放停止回调。
     *
     * 功能：播放结束与播放出错都会回调，由界面根据当前进度判断是「正常结束」还是「播放失败」，
     *      避免把线路短暂重试误当成播放完毕而自动跳到下一集。
     * 参数：onStopped —— 播放结束或出错时触发。
     * 返回值：无。
     */
    fun setOnPlaybackStopped(onStopped: () -> Unit) {
        player?.events()?.addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) = onStopped()
                override fun error(mediaPlayer: MediaPlayer) = onStopped()
            }
        )
    }

    /** 释放播放器资源。 */
    fun release() {
        runCatching { component?.release() }
        runCatching { embeddedComponent?.release() }
        videoSurface.removeAll()
        component = null
        embeddedComponent = null
        player = null
        isAvailable = false
    }
}

/**
 * 判断播放组件是否就绪。
 *
 * 功能：内置 libVLC 或本机安装的 VLC 任一可用即返回 true，供界面提前给出提示。
 * 参数：无。
 * 返回值：Boolean —— 可用返回 true。
 */
fun isVlcAvailable(): Boolean =
    resolveBundledVlcDirectory() != null ||
        runCatching { NativeDiscovery().discover() }.getOrDefault(false)
