package com.example.messageforwarder.model

import com.example.messageforwarder.data.local.DeliveryLogEntity

/**
 * Supported request methods for the outbound webhook.
 */
enum class HttpMethod(val supportsRequestBody: Boolean) {
    POST(true),
    PUT(true),
    PATCH(true),
    GET(false),
}

/**
 * Persisted operator-controlled settings that define how messages are forwarded.
 */
data class ForwarderSettings(
    val apiUrl: String = "",
    val httpMethod: HttpMethod = HttpMethod.POST,
    val useBearerToken: Boolean = false,
    val bearerToken: String = "",
    val additionalHeadersJson: String = "",
    val additionalPayloadJson: String = "",
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
 * Internal normalized SMS event used everywhere after the Android broadcast is parsed.
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
 * Payload eventually sent to the remote API after optional template expansion.
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
 * Small dashboard aggregate so the status screen does not compose multiple flows itself.
 */
data class DashboardSnapshot(
    val pendingCount: Int = 0,
    val latestLog: DeliveryLogEntity? = null,
    val lastDeliveredAt: Long? = null,
)
