package com.example.messageforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.messageforwarder.data.appContainer
import com.example.messageforwarder.worker.SmsForwardWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 裝置重開機後恢復背景轉送能力，但不回頭掃描歷史收件匣。
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appContext.appContainer.forwardingRepository.markBootRestore(System.currentTimeMillis())
                // 只要保留既有待送列，就足以在開機後恢復重試流程。
                SmsForwardWorkScheduler.enqueue(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
