package com.bocchi.tv.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 创建跨平台 HTTP 客户端。
 *
 * 功能：按平台选择网络引擎（桌面/安卓用 OkHttp，iOS 用 Darwin），并统一装配超时与 JSON 解析。
 * 参数：无。
 * 返回值：HttpClient —— 已配置好的客户端实例（调用方负责复用，不要每次请求都新建）。
 */
internal expect fun createHttpClient(): HttpClient

/** 采集接口与网页抓取的统一 JSON 解析器：宽松模式，容忍字段缺失与类型不一致。 */
internal val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * 应用通用请求超时（毫秒）。
 *
 * 说明：聚合搜索会并发请求多个源，单个源超时不应拖住整体，因此统一限制在 12 秒。
 */
internal const val REQUEST_TIMEOUT_MS: Long = 12_000L

/**
 * 装配客户端公共配置。
 *
 * 功能：为各平台引擎套用相同的超时与内容协商配置，避免两端行为不一致。
 * 参数：client —— 平台引擎构建出的客户端。
 * 返回值：HttpClient —— 配置后的客户端。
 */
internal fun HttpClient.applyCommonConfig(): HttpClient = config {
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = REQUEST_TIMEOUT_MS
        socketTimeoutMillis = REQUEST_TIMEOUT_MS
    }
    install(ContentNegotiation) {
        json(AppJson)
    }
}
