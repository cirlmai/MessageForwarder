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
 * Builds a stable id for deduplicating repeated SMS broadcasts.
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
 * Keeps sensitive message previews readable without showing the full SMS on screen.
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
 * The app intentionally forwards only to HTTPS endpoints.
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
 * Guards the free-form header/payload editors from invalid JSON object input.
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
 * Replaces {{placeholders}} inside custom headers or payload fragments with SMS values.
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
 * Device/app values that become part of each outbound event payload.
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
 * Shared timestamp formatter for dashboard and logs.
 */
object AppTimeFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun format(timestamp: Long?, neverLabel: String): String {
        if (timestamp == null) return neverLabel
        return formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }
}
