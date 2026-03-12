package com.example.messageforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.messageforwarder.data.appContainer
import com.example.messageforwarder.sms.SmsParser
import com.example.messageforwarder.worker.SmsForwardWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives new incoming SMS broadcasts, persists them quickly, and hands network work to WorkManager.
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = appContext.appContainer
                val settings = container.forwardingRepository.currentSettings()
                if (!settings.canForward) return@launch

                val event = SmsParser.fromIntent(appContext, intent) ?: return@launch
                val inserted = container.forwardingRepository.enqueueIncomingMessage(event)
                if (inserted) {
                    // The receiver stays fast: database first, HTTP work later.
                    SmsForwardWorkScheduler.enqueue(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
