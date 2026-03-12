package com.example.messageforwarder.worker

import android.content.Context
import com.example.messageforwarder.R
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.messageforwarder.data.appContainer
import com.example.messageforwarder.data.remote.ApiCallResult
import com.example.messageforwarder.model.ForwardRequestPayload

/**
 * Drains the local queue one message at a time until nothing is left or a retry is needed.
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
                // Keep the queue entry recorded, but stop retrying until the operator fixes settings.
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
                    // A visible failure notification helps on managed devices that are not actively monitored.
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
