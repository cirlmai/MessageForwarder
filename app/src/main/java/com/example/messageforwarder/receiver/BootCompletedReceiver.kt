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
 * Restores background delivery after a reboot without rescanning historical inbox messages.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appContext.appContainer.forwardingRepository.markBootRestore(System.currentTimeMillis())
                // Existing pending rows are enough to resume retries after boot.
                SmsForwardWorkScheduler.enqueue(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
