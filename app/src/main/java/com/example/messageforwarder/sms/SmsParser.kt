package com.example.messageforwarder.sms

import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.provider.Telephony
import com.example.messageforwarder.R
import com.example.messageforwarder.model.ReceivedSmsEvent
import com.example.messageforwarder.util.DeviceMetadata
import com.example.messageforwarder.util.SmsFingerprint

/**
 * Converts Android's PDU-based SMS broadcast into one normalized app event.
 */
object SmsParser {
    fun fromIntent(context: Context, intent: Intent): ReceivedSmsEvent? {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return null

        val sender = messages.first().displayOriginatingAddress
            ?: messages.first().originatingAddress
            ?: context.getString(R.string.common_unknown_sender)
        val body = messages.joinToString(separator = "") { message ->
            message.displayMessageBody ?: message.messageBody.orEmpty()
        }.trim()
        if (body.isBlank()) return null

        val receivedAt = messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        val subscriptionId = intent.extras
            ?.getInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        val simSlot = when {
            // Dual-SIM devices expose the broadcast with subscription metadata when available.
            subscriptionId != null -> SubscriptionManager.getSlotIndex(subscriptionId).takeIf { it >= 0 }
            else -> intent.extras?.getInt(SubscriptionManager.EXTRA_SLOT_INDEX, -1)?.takeIf { it >= 0 }
        }

        return ReceivedSmsEvent(
            messageId = SmsFingerprint.create(sender, body, receivedAt, subscriptionId),
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            subscriptionId = subscriptionId,
            simSlot = simSlot,
            deviceId = DeviceMetadata.getAndroidId(context),
            appVersion = DeviceMetadata.getAppVersion(context),
        )
    }
}
