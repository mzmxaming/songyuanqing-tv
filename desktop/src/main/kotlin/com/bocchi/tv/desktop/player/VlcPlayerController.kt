package com.bocchi.tv.desktop.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Canvas

/**
 * VLC 播放器封装（桌面端）。
 *
 * 功能：用 libVLC 播放 HLS / mp4 等地址，画面直接渲染到 AWT Canvas 上，再由 Compose 的 SwingPanel 承载。
 *      初始化时会自动探测本机 VLC 安装位置；探测失败返回可读提示，由界面展示而不是白屏。
 * 参数：无（构造时仅创建画布，引擎在 [initialize] 中启动）。
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

    /** 承载视频画面的 AWT 画布。 */
    val canvas: Canvas = Canvas().apply {
        background = java.awt.Color.BLACK
        isVisible = true
    }

    /** VLC 媒体工厂（播放器与视频输出的创建入口）。 */
    private var factory: MediaPlayerFactory? = null

    /** 底层播放器实例。 */
    private var player: EmbeddedMediaPlayer? = null

    /** 是否已成功初始化。 */
    var isAvailable: Boolean = false
        private set

    /**
     * 初始化播放引擎。
     *
     * 功能：探测本机 libVLC 并创建播放器，把视频输出绑定到 [canvas]；重复调用直接返回成功。
     * 参数：无。
     * 返回值：InitResult —— 成功或失败说明。
     */
    fun initialize(): InitResult {
        if (isAvailable) return InitResult(true)
        val discovered = runCatching { NativeDiscovery().discover() }.getOrDefault(false)
        if (!discovered) {
            return InitResult(
                false,
                "未找到 VLC 运行库。请先安装 VLC（videolan.org），安装后重启本程序即可播放。"
            )
        }
        return runCatching {
            val createdFactory = MediaPlayerFactory()
            val createdPlayer = createdFactory.mediaPlayers().newEmbeddedMediaPlayer()
            createdPlayer.videoSurface().set(
                createdFactory.videoSurfaces().newVideoSurface(canvas)
            )
            factory = createdFactory
            player = createdPlayer
            isAvailable = true
            InitResult(true)
        }.getOrElse { error ->
            InitResult(false, "播放引擎启动失败：${error.message ?: "未知错误"}")
        }
    }

    /**
     * 播放指定地址。
     *
     * 参数：url —— 可播放地址（m3u8 / mp4）。
     * 返回值：Boolean —— 是否已提交播放。
     */
    fun play(url: String): Boolean {
        val current = player ?: return false
        return runCatching {
            current.media().play(url)
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
     * 设置音量。
     *
     * 参数：volume —— 0~100。
     * 返回值：无。
     */
    fun setVolume(volume: Int) {
        runCatching { player?.audio()?.setVolume(volume.coerceIn(0, 100)) }
    }

    /**
     * 注册播放结束回调。
     *
     * 功能：用于实现「自动播放下一集」；播放失败也走同一回调，避免界面停在黑屏。
     * 参数：onFinished —— 播放结束或出错时触发。
     * 返回值：无。
     */
    fun setOnFinished(onFinished: () -> Unit) {
        player?.events()?.addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun finished(mediaPlayer: MediaPlayer) = onFinished()
                override fun error(mediaPlayer: MediaPlayer) = onFinished()
            }
        )
    }

    /** 释放播放器资源。 */
    fun release() {
        runCatching { player?.release() }
        runCatching { factory?.release() }
        player = null
        factory = null
        isAvailable = false
    }
}

/**
 * 判断 libVLC 是否已就绪。
 *
 * 功能：在不创建播放器的情况下探测本机是否安装 VLC，供界面提前给出提示。
 * 参数：无。
 * 返回值：Boolean —— 找到原生库返回 true。
 */
fun isVlcAvailable(): Boolean = runCatching { NativeDiscovery().discover() }.getOrDefault(false)
