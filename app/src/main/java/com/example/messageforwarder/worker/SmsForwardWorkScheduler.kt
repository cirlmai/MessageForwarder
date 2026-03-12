package com.example.messageforwarder.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Ensures there is at most one active background flush job for the pending queue.
 */
object SmsForwardWorkScheduler {
    private const val UNIQUE_WORK_NAME = "forward_pending_sms"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SmsForwardWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                // KEEP prevents multiple broadcasts from spawning duplicate workers for the same queue.
                ExistingWorkPolicy.KEEP,
                request,
            )
    }
}
