package com.bocchi.tv.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS 入口控制器。
 *
 * 功能：把 Compose 界面包装成 UIViewController，供 SwiftUI / UIKit 宿主加载。
 * 参数：无。
 * 返回值：UIViewController —— 承载完整应用界面的控制器。
 */
fun MainViewController(): UIViewController = ComposeUIViewController { IosRoot() }
