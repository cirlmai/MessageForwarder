package com.example.messageforwarder.util

import android.content.Context
import android.provider.Settings
import com.example.messageforwarder.model.ForwardRequestPayload
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 產生穩定的簡訊指紋，用來去除重複廣播或多段訊息重入。
 */
object SmsFingerprint {
    fun create(
        sender: String,
        body: String,
        receivedAt: Long,
        subscriptionId: Int?,
    ): String {
        val raw = listOf(sender, body, receivedAt.toString(), subscriptionId?.toString().orEmpty())
            .joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

/**
 * 在不顯示完整簡訊的前提下，保留部分可辨識內容供畫面預覽。
 */
object MessageMasker {
    fun maskSmsBody(body: String, emptyLabel: String): String {
        if (body.isBlank()) return emptyLabel

        val trimmed = body.trim()
        if (trimmed.length <= 6) {
            return "•".repeat(trimmed.length)
        }

        val visiblePrefix = trimmed.take(4)
        val visibleSuffix = trimmed.takeLast(2)
        return buildString(trimmed.length) {
            append(visiblePrefix)
            append("•".repeat(trimmed.length - visiblePrefix.length - visibleSuffix.length))
            append(visibleSuffix)
        }
    }
}

/**
 * App 僅允許轉送到 HTTPS 端點。
 */
object HttpsUrlValidator {
    fun isValid(url: String): Boolean {
        val candidate = url.trim()
        if (candidate.isEmpty()) return false

        return runCatching {
            val uri = URI(candidate)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}

/**
 * 驗證使用者輸入的自訂 header / payload 是否為合法 JSON 物件。
 */
object JsonConfigValidator {
    fun isValidJsonObject(rawValue: String): Boolean {
        val candidate = rawValue.trim()
        if (candidate.isEmpty()) return true

        return runCatching { JSONObject(candidate) }
            .fold(
                onSuccess = { true },
                onFailure = { false },
            )
    }
}

/**
 * 紀錄已收訊但未轉傳的原因。
 */
enum class ForwardingRuleMismatch {
    SENDER,
    KEYWORD,
    SENDER_AND_KEYWORD,
}

data class ForwardingRuleDecision(
    val shouldForward: Boolean,
    val mismatch: ForwardingRuleMismatch? = null,
)

/**
 * 在簡訊進入待送佇列前，先依寄件人與內容規則判斷是否允許轉傳。
 */
object ForwardingRuleMatcher {
    private val entrySeparator = "[,;\\n]+".toRegex()

    fun parseEntries(rawValue: String): List<String> = rawValue
        .split(entrySeparator)
        .map(String::trim)
        .filter(String::isNotEmpty)

    fun evaluate(
        sender: String,
        body: String,
        allowedSendersRaw: String,
        requiredKeywordsRaw: String,
    ): ForwardingRuleDecision {
        val allowedSenders = parseEntries(allowedSendersRaw)
        val requiredKeywords = parseEntries(requiredKeywordsRaw)
        val senderMatches = allowedSenders.isEmpty() || matchesSender(sender, allowedSenders)
        val keywordMatches = requiredKeywords.isEmpty() || matchesKeyword(body, requiredKeywords)

        val mismatch = when {
            senderMatches && keywordMatches -> null
            !senderMatches && !keywordMatches -> ForwardingRuleMismatch.SENDER_AND_KEYWORD
            !senderMatches -> ForwardingRuleMismatch.SENDER
            else -> ForwardingRuleMismatch.KEYWORD
        }
        return ForwardingRuleDecision(
            shouldForward = mismatch == null,
            mismatch = mismatch,
        )
    }

    private fun matchesSender(sender: String, allowedSenders: List<String>): Boolean {
        val senderForms = normalizedSenderForms(sender)
        return allowedSenders.any { candidate ->
            normalizedSenderForms(candidate).any(senderForms::contains)
        }
    }

    private fun matchesKeyword(body: String, requiredKeywords: List<String>): Boolean =
        requiredKeywords.any { keyword -> body.contains(keyword, ignoreCase = true) }

    private fun normalizedSenderForms(value: String): Set<String> {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return emptySet()

        val forms = linkedSetOf(
            trimmed.lowercase(Locale.ROOT),
            trimmed.replace("\\s+".toRegex(), "").lowercase(Locale.ROOT),
            trimmed.replace("[\\s()\\-]".toRegex(), "").lowercase(Locale.ROOT),
        )

        val digitsOnly = trimmed.filter(Char::isDigit)
        if (digitsOnly.isNotBlank()) {
            forms += digitsOnly
            normalizeTaiwanPhone(digitsOnly)?.let(forms::add)
        }

        val plusDigits = buildString {
            trimmed.forEachIndexed { index, char ->
                if (char.isDigit() || (index == 0 && char == '+')) {
                    append(char)
                }
            }
        }
        if (plusDigits.length > 1) {
            forms += plusDigits
        }

        return forms.filter(String::isNotBlank).toSet()
    }

    private fun normalizeTaiwanPhone(digitsOnly: String): String? = when {
        digitsOnly.startsWith("886") && digitsOnly.length >= 11 -> "0${digitsOnly.removePrefix("886")}"
        digitsOnly.startsWith("0") -> digitsOnly
        else -> null
    }
}

/**
 * 將自訂 header / payload 內的 {{placeholder}} 替換為實際簡訊欄位值。
 */
object JsonTemplateResolver {
    private val placeholderPattern = "\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}".toRegex()

    fun resolveStringTemplate(template: String, payload: ForwardRequestPayload): String {
        val replacements = mapOf(
            "messageId" to payload.messageId,
            "sender" to payload.sender,
            "body" to payload.body,
            "text" to payload.body,
            "receivedAt" to payload.receivedAt.toString(),
            "subscriptionId" to payload.subscriptionId?.toString().orEmpty(),
            "simSlot" to payload.simSlot?.toString().orEmpty(),
            "deviceId" to payload.deviceId,
            "appVersion" to payload.appVersion,
            "isTest" to payload.isTest.toString(),
        )

        return placeholderPattern.replace(template) { match ->
            replacements[match.groupValues[1]] ?: match.value
        }
    }

    fun resolveJsonObjectTemplate(rawValue: String, payload: ForwardRequestPayload): JSONObject {
        val candidate = rawValue.trim()
        if (candidate.isEmpty()) return JSONObject()
        return resolveJsonObject(JSONObject(candidate), payload)
    }

    private fun resolveJsonObject(source: JSONObject, payload: ForwardRequestPayload): JSONObject {
        val resolved = JSONObject()
        source.keys().forEach { key ->
            resolved.put(key, resolveValue(source.opt(key), payload))
        }
        return resolved
    }

    private fun resolveJsonArray(source: JSONArray, payload: ForwardRequestPayload): JSONArray {
        val resolved = JSONArray()
        for (index in 0 until source.length()) {
            resolved.put(resolveValue(source.opt(index), payload))
        }
        return resolved
    }

    private fun resolveValue(value: Any?, payload: ForwardRequestPayload): Any? = when (value) {
        is JSONObject -> resolveJsonObject(value, payload)
        is JSONArray -> resolveJsonArray(value, payload)
        is String -> resolveStringTemplate(value, payload)
        else -> value
    }
}

/**
 * 收集每次轉送事件都需要附帶的裝置與 App 資訊。
 */
object DeviceMetadata {
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
    }

    fun getAppVersion(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName ?: packageInfo.longVersionCode.toString()
    }

    fun maskDeviceId(rawValue: String): String {
        if (rawValue.length <= 6) return rawValue
        return "${rawValue.take(4)}...${rawValue.takeLast(2)}"
    }
}

/**
 * 儀表板與紀錄頁共用的時間格式化工具。
 */
object AppTimeFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun format(timestamp: Long?, neverLabel: String): String {
        if (timestamp == null) return neverLabel
        return formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }
}
