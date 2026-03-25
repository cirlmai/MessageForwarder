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
 * 確保同一時間最多只有一個背景 worker 在清空待送佇列。
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
                // KEEP 可避免多次廣播同時為同一批待送資料啟動重複 worker。
                ExistingWorkPolicy.KEEP,
                request,
            )
    }
}
