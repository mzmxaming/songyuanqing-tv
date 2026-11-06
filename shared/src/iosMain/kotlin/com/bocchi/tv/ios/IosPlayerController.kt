package com.bocchi.tv.ios

import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * iOS 播放器封装。
 *
 * 功能：用系统 AVPlayer 播放 HLS / MP4，画面通过 AVPlayerLayer 渲染到原生 UIView 上，
 *      再由 Compose 的 UIKitView 承载；进度、时长、音量与暂停均由这里统一暴露给界面。
 * 参数：无（构造时仅创建承载视图，调用 [play] 时才真正建立播放项）。
 * 返回值：无（服务类）。
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlayerController {

    /** 承载 AVPlayerLayer 的原生视图。 */
    val view: UIView = PlayerLayerView()

    /** 底层播放器；每次播放新地址时重建，避免旧媒体状态残留。 */
    private var player: AVPlayer? = null

    /** 当前视频层，随播放器一起重建。 */
    private var playerLayer: AVPlayerLayer? = null

    /**
     * 播放指定地址。
     *
     * 功能：地址非法或创建失败时返回 false，由界面给出可读提示。
     * 参数：url —— 可播放地址（m3u8 / mp4）。
     * 返回值：Boolean —— 是否已提交播放。
     */
    fun play(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        releasePlayer()
        val created = AVPlayer(uRL = nsUrl)
        player = created
        (view as PlayerLayerView).attach(created)
        created.play()
        return true
    }

    /** 暂停播放。 */
    fun pause() {
        player?.pause()
    }

    /** 继续播放。 */
    fun resume() {
        player?.play()
    }

    /** 停止并释放播放项。 */
    fun stop() {
        releasePlayer()
    }

    /**
     * 读取当前播放进度。
     *
     * 参数：无。
     * 返回值：Pair<Long, Long> —— 已播放毫秒数与总时长毫秒数（未知时为 0）。
     */
    fun progress(): Pair<Long, Long> {
        val current = player ?: return 0L to 0L
        val position = current.currentTime().secondsToMillis()
        val length = current.currentItem?.duration?.secondsToMillis() ?: 0L
        return position to length
    }

    /**
     * 判断是否正在播放。
     *
     * 参数：无。
     * 返回值：Boolean —— 播放中返回 true。
     */
    fun isPlaying(): Boolean = (player?.rate ?: 0f) > 0f

    /**
     * 拖动到指定进度。
     *
     * 参数：position —— 0f~1f 的播放位置。
     * 返回值：无。
     */
    fun seekTo(position: Float) {
        val current = player ?: return
        val length = current.currentItem?.duration ?: return
        val totalSeconds = CMTimeGetSeconds(length)
        if (totalSeconds <= 0.0) return
        current.seekToTime(CMTimeMakeWithSeconds(totalSeconds * position.coerceIn(0f, 1f), 1000))
    }

    /**
     * 设置音量。
     *
     * 参数：volume —— 0~100。
     * 返回值：无。
     */
    fun setVolume(volume: Int) {
        player?.volume = (volume.coerceIn(0, 100)) / 100f
    }

    /** 释放播放器。 */
    fun release() {
        releasePlayer()
    }

    /**
     * 释放当前播放器并清空视频层。
     *
     * 参数：无。
     * 返回值：无。
     */
    private fun releasePlayer() {
        player?.pause()
        player = null
        playerLayer = null
        (view as PlayerLayerView).detach()
    }

    /**
     * 把 CMTime 转成毫秒。
     *
     * 参数：无（扩展在 CValue<CMTime> 上）。
     * 返回值：Long —— 毫秒数；非有限值时返回 0。
     */
    private fun CValue<CMTime>.secondsToMillis(): Long {
        val seconds = CMTimeGetSeconds(this)
        if (seconds.isNaN() || seconds <= 0.0) return 0L
        return (seconds * 1000).toLong()
    }
}

/**
 * 承载视频画面的原生视图。
 *
 * 功能：持有 AVPlayerLayer，并在自身尺寸变化时同步图层大小，避免旋转或布局变化后画面变形。
 * 参数：无。
 * 返回值：无（视图类）。
 */
@OptIn(ExperimentalForeignApi::class)
private class PlayerLayerView : UIView(frame = CGRectZero.readValue()) {

    /** 当前视频层。 */
    private var currentLayer: AVPlayerLayer? = null

    init {
        backgroundColor = platform.UIKit.UIColor.blackColor
    }

    /**
     * 绑定播放器。
     *
     * 参数：player —— 新建的 AVPlayer。
     * 返回值：无。
     */
    fun attach(player: AVPlayer) {
        detach()
        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        currentLayer = layer
        this.layer.addSublayer(layer)
        syncLayerFrame()
    }

    /**
     * 解绑并移除视频层。
     *
     * 参数：无。
     * 返回值：无。
     */
    fun detach() {
        currentLayer?.removeFromSuperlayer()
        currentLayer = null
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        syncLayerFrame()
    }

    /**
     * 同步视频层尺寸到当前视图。
     *
     * 参数：无。
     * 返回值：无。
     */
    private fun syncLayerFrame() {
        val layer = currentLayer ?: return
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.setFrame(bounds)
        CATransaction.commit()
    }
}

/**
 * Compose 侧的播放画面。
 *
 * 参数：controller —— 播放器实例；modifier —— 布局修饰。
 * 返回值：无。
 */
@Composable
fun PlayerSurface(controller: IosPlayerController, modifier: Modifier = Modifier) {
    UIKitView(factory = { controller.view }, modifier = modifier)
}
