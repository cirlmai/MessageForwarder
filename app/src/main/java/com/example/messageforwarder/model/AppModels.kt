package com.example.messageforwarder.model

import com.example.messageforwarder.data.local.DeliveryLogEntity

/**
 * 轉發 API 目前支援的 HTTP 方法。
 */
enum class HttpMethod(val supportsRequestBody: Boolean) {
    POST(true),
    PUT(true),
    PATCH(true),
    GET(false),
}

/**
 * 由操作人員在 App 內維護，並決定簡訊如何轉傳的設定集合。
 */
data class ForwarderSettings(
    val apiUrl: String = "",
    val httpMethod: HttpMethod = HttpMethod.POST,
    val useBearerToken: Boolean = false,
    val bearerToken: String = "",
    val additionalHeadersJson: String = "",
    val additionalPayloadJson: String = "",
    val allowedSendersRaw: String = "",
    val requiredKeywordsRaw: String = "",
    val appEnabled: Boolean = false,
    val lastBootRestoreAt: Long? = null,
) {
    val isApiConfigured: Boolean
        get() = apiUrl.isNotBlank()

    val hasValidAuthConfiguration: Boolean
        get() = !useBearerToken || bearerToken.isNotBlank()

    val canForward: Boolean
        get() = appEnabled && isApiConfigured && hasValidAuthConfiguration
}

/**
 * Android 廣播被解析後，App 內部統一使用的標準化簡訊事件。
 */
data class ReceivedSmsEvent(
    val messageId: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int?,
    val simSlot: Int?,
    val deviceId: String,
    val appVersion: String,
)

/**
 * 套用模板替換後，最終要送到遠端 API 的 payload 模型。
 */
data class ForwardRequestPayload(
    val messageId: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subscriptionId: Int?,
    val simSlot: Int?,
    val deviceId: String,
    val appVersion: String,
    val isTest: Boolean = false,
)

/**
 * 首頁狀態卡需要的聚合資料，避免畫面層自行組合多條 Flow。
 */
data class DashboardSnapshot(
    val pendingCount: Int = 0,
    val latestLog: DeliveryLogEntity? = null,
    val lastDeliveredAt: Long? = null,
)
