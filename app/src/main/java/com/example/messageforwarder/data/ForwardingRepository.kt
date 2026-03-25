package com.example.messageforwarder.data

import android.content.Context
import com.example.messageforwarder.R
import androidx.room.withTransaction
import com.example.messageforwarder.data.local.DeliveryLogEntity
import com.example.messageforwarder.data.local.DeliveryStatus
import com.example.messageforwarder.data.local.MessageForwarderDatabase
import com.example.messageforwarder.data.local.PendingForwardEntity
import com.example.messageforwarder.data.remote.ApiCallResult
import com.example.messageforwarder.data.remote.ForwardingApiClient
import com.example.messageforwarder.data.settings.SecureSettingsStore
import com.example.messageforwarder.model.DashboardSnapshot
import com.example.messageforwarder.model.ForwardRequestPayload
import com.example.messageforwarder.model.ForwarderSettings
import com.example.messageforwarder.model.ReceivedSmsEvent
import com.example.messageforwarder.util.ForwardingRuleMatcher
import com.example.messageforwarder.util.ForwardingRuleMismatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 集中管理佇列與送達紀錄的異動，避免 worker 與 receiver 對狀態規則各自為政。
 */
class ForwardingRepository(
    private val appContext: Context,
    private val database: MessageForwarderDatabase,
    private val settingsStore: SecureSettingsStore,
    private val apiClient: ForwardingApiClient,
) {
    private val pendingDao = database.pendingForwardDao()
    private val deliveryLogDao = database.deliveryLogDao()

    fun observeDashboardSnapshot(): Flow<DashboardSnapshot> = combine(
        pendingDao.observePendingCount(),
        deliveryLogDao.observeLatest(),
        deliveryLogDao.observeLastDeliveredAt(),
    ) { pendingCount, latestLog, lastDeliveredAt ->
        DashboardSnapshot(
            pendingCount = pendingCount,
            latestLog = latestLog,
            lastDeliveredAt = lastDeliveredAt,
        )
    }

    fun observeDeliveryLogs(limit: Int = 100): Flow<List<DeliveryLogEntity>> =
        deliveryLogDao.observeRecent(limit)

    fun currentSettings(): ForwarderSettings = settingsStore.snapshot()

    fun observeSettings(): Flow<ForwarderSettings> = settingsStore.observeSettings()

    suspend fun saveSettings(settings: ForwarderSettings) {
        settingsStore.saveSettings(settings)
    }

    suspend fun markBootRestore(timestamp: Long) {
        settingsStore.markBootRestore(timestamp)
    }

    suspend fun enqueueIncomingMessage(
        event: ReceivedSmsEvent,
        settings: ForwarderSettings,
    ): Boolean = database.withTransaction {
        // 只要紀錄表內已有相同 fingerprint，就代表這封簡訊先前已被接受處理。
        if (deliveryLogDao.getByFingerprint(event.messageId) != null) {
            return@withTransaction false
        }

        val ruleDecision = ForwardingRuleMatcher.evaluate(
            sender = event.sender,
            body = event.body,
            allowedSendersRaw = settings.allowedSendersRaw,
            requiredKeywordsRaw = settings.requiredKeywordsRaw,
        )
        deliveryLogDao.insert(
            event.toDeliveryLog(
                status = if (ruleDecision.shouldForward) {
                    DeliveryStatus.RECEIVED
                } else {
                    DeliveryStatus.FILTERED
                },
                lastError = ruleDecision.mismatch?.toMessage(),
            ),
        )
        if (ruleDecision.shouldForward) {
            pendingDao.insert(event.toPendingForward())
        }
        ruleDecision.shouldForward
    }

    suspend fun getNextPendingForward(): PendingForwardEntity? = pendingDao.getNext()

    suspend fun markSending(pending: PendingForwardEntity, attemptCount: Int, attemptedAt: Long) {
        database.withTransaction {
            pendingDao.update(
                pending.copy(
                    attemptCount = attemptCount,
                    lastAttemptAt = attemptedAt,
                    lastError = null,
                ),
            )
            val currentLog = deliveryLogDao.getByFingerprint(pending.messageFingerprint)
                ?: return@withTransaction
            deliveryLogDao.update(
                currentLog.copy(
                    status = DeliveryStatus.SENDING,
                    retryCount = (attemptCount - 1).coerceAtLeast(0),
                    lastAttemptAt = attemptedAt,
                    lastError = null,
                ),
            )
        }
    }

    suspend fun markDelivered(
        pending: PendingForwardEntity,
        attemptCount: Int,
        deliveredAt: Long,
        statusCode: Int,
    ) {
        database.withTransaction {
            pendingDao.deleteByFingerprint(pending.messageFingerprint)
            val currentLog = deliveryLogDao.getByFingerprint(pending.messageFingerprint)
                ?: return@withTransaction
            deliveryLogDao.update(
                currentLog.copy(
                    status = DeliveryStatus.DELIVERED,
                    retryCount = (attemptCount - 1).coerceAtLeast(0),
                    lastAttemptAt = deliveredAt,
                    lastError = null,
                    deliveredAt = deliveredAt,
                    httpStatusCode = statusCode,
                ),
            )
        }
    }

    suspend fun markFailure(
        pending: PendingForwardEntity,
        attemptCount: Int,
        attemptedAt: Long,
        failureMessage: String,
        statusCode: Int?,
    ) {
        database.withTransaction {
            pendingDao.update(
                pending.copy(
                    attemptCount = attemptCount,
                    lastAttemptAt = attemptedAt,
                    lastError = failureMessage,
                ),
            )
            val currentLog = deliveryLogDao.getByFingerprint(pending.messageFingerprint)
                ?: return@withTransaction
            deliveryLogDao.update(
                currentLog.copy(
                    status = DeliveryStatus.FAILED,
                    retryCount = (attemptCount - 1).coerceAtLeast(0),
                    lastAttemptAt = attemptedAt,
                    lastError = failureMessage,
                    httpStatusCode = statusCode,
                ),
            )
        }
    }

    suspend fun queueMessageForResend(messageFingerprint: String): Boolean = database.withTransaction {
        val currentLog = deliveryLogDao.getByFingerprint(messageFingerprint) ?: return@withTransaction false
        if (currentLog.status != DeliveryStatus.FAILED) {
            if (currentLog.status == DeliveryStatus.FILTERED) {
                return@withTransaction false
            }
        }
        if (pendingDao.getByFingerprint(messageFingerprint) == null) {
            // 人工補送時，必要時可直接用歷程紀錄快照重建待送佇列項目。
            pendingDao.insert(currentLog.toPendingForward())
        }
        true
    }

    suspend fun sendTestPayload(
        settings: ForwarderSettings,
        payload: ForwardRequestPayload,
    ): ApiCallResult = apiClient.forward(settings, payload)

    private fun ReceivedSmsEvent.toPendingForward(): PendingForwardEntity = PendingForwardEntity(
        messageFingerprint = messageId,
        sender = sender,
        body = body,
        receivedAt = receivedAt,
        subscriptionId = subscriptionId,
        simSlot = simSlot,
        deviceId = deviceId,
        appVersion = appVersion,
    )

    private fun ReceivedSmsEvent.toDeliveryLog(
        status: DeliveryStatus,
        lastError: String? = null,
    ): DeliveryLogEntity = DeliveryLogEntity(
        messageFingerprint = messageId,
        sender = sender,
        body = body,
        receivedAt = receivedAt,
        subscriptionId = subscriptionId,
        simSlot = simSlot,
        deviceId = deviceId,
        appVersion = appVersion,
        status = status,
        lastError = lastError,
    )

    private fun DeliveryLogEntity.toPendingForward(): PendingForwardEntity = PendingForwardEntity(
        messageFingerprint = messageFingerprint,
        sender = sender,
        body = body,
        receivedAt = receivedAt,
        subscriptionId = subscriptionId,
        simSlot = simSlot,
        deviceId = deviceId,
        appVersion = appVersion,
        attemptCount = retryCount,
        lastAttemptAt = lastAttemptAt,
        lastError = lastError,
    )

    private fun ForwardingRuleMismatch.toMessage(): String = when (this) {
        ForwardingRuleMismatch.SENDER ->
            appContext.getString(R.string.log_filtered_sender_mismatch)

        ForwardingRuleMismatch.KEYWORD ->
            appContext.getString(R.string.log_filtered_keyword_mismatch)

        ForwardingRuleMismatch.SENDER_AND_KEYWORD ->
            appContext.getString(R.string.log_filtered_sender_and_keyword_mismatch)
    }
}
