package com.example.messageforwarder.worker

import android.content.Context
import com.example.messageforwarder.R
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messageforwarder.data.appContainer
import com.example.messageforwarder.data.remote.ApiCallResult
import com.example.messageforwarder.model.ForwardRequestPayload

/**
 * 逐筆清空本機待送佇列，直到送完或遇到需要稍後重試的情況。
 */
class SmsForwardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val container = appContext.appContainer
    private val repository = container.forwardingRepository

    override suspend fun doWork(): Result {
        while (true) {
            val pending = repository.getNextPendingForward() ?: return Result.success()
            val settings = repository.currentSettings()
            if (!settings.canForward) {
                repository.markFailure(
                    pending = pending,
                    attemptCount = pending.attemptCount + 1,
                    attemptedAt = System.currentTimeMillis(),
                    failureMessage = applicationContext.getString(
                        R.string.worker_error_forwarding_disabled_or_incomplete,
                    ),
                    statusCode = null,
                )
                // 保留佇列資料，但在設定修正前暫停重試，避免背景無限失敗。
                return Result.success()
            }

            val nextAttempt = pending.attemptCount + 1
            val attemptedAt = System.currentTimeMillis()
            repository.markSending(pending, nextAttempt, attemptedAt)

            when (
                val result = container.forwardingApiClient.forward(
                    settings = settings,
                    payload = ForwardRequestPayload(
                        messageId = pending.messageFingerprint,
                        sender = pending.sender,
                        body = pending.body,
                        receivedAt = pending.receivedAt,
                        subscriptionId = pending.subscriptionId,
                        simSlot = pending.simSlot,
                        deviceId = pending.deviceId,
                        appVersion = pending.appVersion,
                    ),
                )
            ) {
                is ApiCallResult.Success -> {
                    repository.markDelivered(
                        pending = pending,
                        attemptCount = nextAttempt,
                        deliveredAt = System.currentTimeMillis(),
                        statusCode = result.statusCode,
                    )
                }

                is ApiCallResult.Failure -> {
                    repository.markFailure(
                        pending = pending,
                        attemptCount = nextAttempt,
                        attemptedAt = System.currentTimeMillis(),
                        failureMessage = result.message,
                        statusCode = result.statusCode,
                    )
                    // 受管裝置未必有人持續盯著畫面，因此保留明顯失敗通知有助於排查。
                    container.forwardingNotifier.showDeliveryFailure(
                        sender = pending.sender,
                        message = result.message,
                    )
                    return Result.retry()
                }
            }
        }
    }
}
