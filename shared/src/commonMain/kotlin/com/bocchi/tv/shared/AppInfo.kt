package com.bocchi.tv.shared

/**
 * 共享模块版本信息。
 *
 * 功能：为多平台模块提供一个可被各端引用的常量，同时验证共享模块已正确接入编译。
 * 参数：无。
 * 返回值：无（单例对象）。
 */
object AppInfo {
    /** 应用显示名称。 */
    const val APP_NAME: String = "松源情·TV"

    /** 当前版本号。 */
    const val VERSION: String = "1.2.6"

    /** 开发者署名。 */
    const val DEVELOPER: String = "APP 设计开发：陈光明"
}
