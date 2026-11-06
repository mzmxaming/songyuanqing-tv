package com.bocchi.tv.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS 平台的 HTTP 引擎。
 *
 * 功能：使用系统原生 Darwin 引擎创建客户端，并套用与其它平台一致的公共配置。
 * 参数：无。
 * 返回值：HttpClient —— 已配置的客户端。
 */
internal actual fun createHttpClient(): HttpClient =
    HttpClient(Darwin) { }.applyCommonConfig()
