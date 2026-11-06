package com.bocchi.tv.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * 桌面 / JVM 平台的 HTTP 引擎。
 *
 * 功能：使用 OkHttp 引擎创建客户端，并套用与其它平台一致的公共配置。
 * 参数：无。
 * 返回值：HttpClient —— 已配置的客户端。
 */
internal actual fun createHttpClient(): HttpClient =
    HttpClient(OkHttp) { }.applyCommonConfig()
