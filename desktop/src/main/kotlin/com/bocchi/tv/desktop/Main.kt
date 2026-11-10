package com.bocchi.tv.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bocchi.tv.desktop.player.VlcEngine

/**
 * 桌面端程序入口。
 *
 * 功能：创建主窗口并挂载 [DesktopApp]；窗口状态（含全屏切换）由应用内部管理，关闭窗口时退出进程。
 *      在创建窗口前先预热 libVLC，避免 Skiko 的原生库抢先绑定后破坏 HLS 地址解析。
 * 参数：无（由 JVM 启动）。
 * 返回值：无。
 */
fun main() {
    VlcEngine.prewarm()
    application {
        val windowState = rememberWindowState(size = DpSize(1320.dp, 860.dp))
        Window(
            onCloseRequest = ::exitApplication,
            title = "松源情·TV",
            state = windowState
        ) {
            DesktopApp(windowState = windowState)
        }
    }
}
