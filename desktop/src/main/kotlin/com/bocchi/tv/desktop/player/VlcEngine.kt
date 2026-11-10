package com.bocchi.tv.desktop.player

/**
 * 全局播放引擎持有者。
 *
 * 功能：在 Compose（Skiko）加载原生库之前创建并初始化 libVLC。
 *      实测 Linux 下 Skiko 先加载会让 libVLC 插件绑定到错误的依赖库，
 *      导致 HLS 清单里的相对地址被拼成 `https://host:`，解复用失败（黑屏、线路失败）。
 *      因此在程序入口先预热引擎，界面层复用同一个实例。
 * 参数：无（单例对象）。
 * 返回值：无。
 */
object VlcEngine {

    /** 共享的播放器控制器（进程内唯一）。 */
    val controller: VlcPlayerController by lazy { VlcPlayerController() }

    /** 是否已预热。 */
    private var warmed = false

    /**
     * 预热播放引擎：在创建 Compose 窗口之前调用。
     *
     * 参数：无。
     * 返回值：无。
     */
    fun prewarm() {
        if (warmed) return
        warmed = true
        runCatching { controller.initialize() }
            .onSuccess { println("[vlc] 预热完成 available=${it.available} ${it.message}") }
            .onFailure { println("[vlc] 预热失败：${it.message}") }
    }
}
