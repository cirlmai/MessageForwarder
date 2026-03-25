package com.example.messageforwarder

import com.example.messageforwarder.model.ForwardRequestPayload
import com.example.messageforwarder.util.ForwardingRuleMatcher
import com.example.messageforwarder.util.ForwardingRuleMismatch
import com.example.messageforwarder.util.HttpsUrlValidator
import com.example.messageforwarder.util.JsonTemplateResolver
import com.example.messageforwarder.util.MessageMasker
import com.example.messageforwarder.util.SmsFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 核心工具邏輯的單元測試，覆蓋網址驗證、模板替換、遮罩與規則判斷。
 */
class ExampleUnitTest {
    @Test
    fun `https validator accepts secure urls only`() {
        assertTrue(HttpsUrlValidator.isValid("https://api.example.com/inbox"))
        assertFalse(HttpsUrlValidator.isValid("http://api.example.com/inbox"))
        assertFalse(HttpsUrlValidator.isValid("not a url"))
    }

    @Test
    fun `sms masker hides the center of long messages`() {
        assertEquals("1234••••90", MessageMasker.maskSmsBody("1234567890", "(empty)"))
        assertEquals("••••", MessageMasker.maskSmsBody("1234", "(empty)"))
    }

    @Test
    fun `sms fingerprint is stable for identical payloads`() {
        val first = SmsFingerprint.create(
            sender = "+886912345678",
            body = "Use 654321 to sign in",
            receivedAt = 1_725_000_000_000,
            subscriptionId = 2,
        )
        val second = SmsFingerprint.create(
            sender = "+886912345678",
            body = "Use 654321 to sign in",
            receivedAt = 1_725_000_000_000,
            subscriptionId = 2,
        )
        val third = SmsFingerprint.create(
            sender = "+886912345678",
            body = "Use 999999 to sign in",
            receivedAt = 1_725_000_000_000,
            subscriptionId = 2,
        )

        assertEquals(first, second)
        assertNotEquals(first, third)
    }

    @Test
    fun `json template resolver injects sms body into text field`() {
        val resolved = JsonTemplateResolver.resolveStringTemplate(
            template = "{{body}}",
            payload = ForwardRequestPayload(
                messageId = "msg-1",
                sender = "Bank",
                body = "Your OTP is 123456",
                receivedAt = 1_725_000_000_000,
                subscriptionId = 1,
                simSlot = 0,
                deviceId = "device-1",
                appVersion = "1.0.0",
            ),
        )

        assertEquals("Your OTP is 123456", resolved)
    }

    @Test
    fun `forwarding rules allow all senders and bodies when filters are empty`() {
        val decision = ForwardingRuleMatcher.evaluate(
            sender = "Bank",
            body = "Your OTP is 123456",
            allowedSendersRaw = "",
            requiredKeywordsRaw = "",
        )

        assertTrue(decision.shouldForward)
        assertEquals(null, decision.mismatch)
    }

    @Test
    fun `forwarding rules match Taiwan phone variants and comma separated keywords`() {
        val decision = ForwardingRuleMatcher.evaluate(
            sender = "+886 912-345-678",
            body = "Use this verification code to continue",
            allowedSendersRaw = "0912345678,MyBank",
            requiredKeywordsRaw = "OTP,verification code",
        )

        assertTrue(decision.shouldForward)
    }

    @Test
    fun `forwarding rules require sender and keyword when both filters are configured`() {
        val decision = ForwardingRuleMatcher.evaluate(
            sender = "PromoSender",
            body = "Weekly sale update",
            allowedSendersRaw = "BankAlert",
            requiredKeywordsRaw = "OTP\nverification code",
        )

        assertFalse(decision.shouldForward)
        assertEquals(ForwardingRuleMismatch.SENDER_AND_KEYWORD, decision.mismatch)
    }
}
