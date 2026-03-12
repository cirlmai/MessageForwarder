package com.example.messageforwarder.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.messageforwarder.model.HttpMethod
import com.example.messageforwarder.model.ForwarderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Stores outbound API configuration in encrypted shared preferences.
 */
class SecureSettingsStore(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun snapshot(): ForwarderSettings = ForwarderSettings(
        apiUrl = sharedPreferences.getString(KEY_API_URL, "").orEmpty(),
        httpMethod = sharedPreferences.getString(KEY_HTTP_METHOD, HttpMethod.POST.name)
            ?.let(HttpMethod::valueOf)
            ?: HttpMethod.POST,
        useBearerToken = sharedPreferences.getBoolean(KEY_USE_BEARER_TOKEN, false),
        bearerToken = sharedPreferences.getString(KEY_BEARER_TOKEN, "").orEmpty(),
        additionalHeadersJson = sharedPreferences.getString(KEY_ADDITIONAL_HEADERS_JSON, "").orEmpty(),
        additionalPayloadJson = sharedPreferences.getString(KEY_ADDITIONAL_PAYLOAD_JSON, "").orEmpty(),
        appEnabled = sharedPreferences.getBoolean(KEY_APP_ENABLED, false),
        lastBootRestoreAt = sharedPreferences.getLong(KEY_LAST_BOOT_RESTORE_AT, 0L).takeIf { it > 0L },
    )

    fun observeSettings(): Flow<ForwarderSettings> = callbackFlow {
        // Emit immediately so the UI can render the current state without waiting for a change.
        trySend(snapshot())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(snapshot())
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    suspend fun saveSettings(settings: ForwarderSettings) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_API_URL, settings.apiUrl.trim())
                .putString(KEY_HTTP_METHOD, settings.httpMethod.name)
                .putBoolean(KEY_USE_BEARER_TOKEN, settings.useBearerToken)
                .putString(KEY_BEARER_TOKEN, settings.bearerToken.trim())
                .putString(KEY_ADDITIONAL_HEADERS_JSON, settings.additionalHeadersJson.trim())
                .putString(KEY_ADDITIONAL_PAYLOAD_JSON, settings.additionalPayloadJson.trim())
                .putBoolean(KEY_APP_ENABLED, settings.appEnabled)
                .apply()
        }
    }

    suspend fun markBootRestore(timestamp: Long) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putLong(KEY_LAST_BOOT_RESTORE_AT, timestamp)
                .apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "forwarder_secure_settings"
        private const val KEY_API_URL = "api_url"
        private const val KEY_HTTP_METHOD = "http_method"
        private const val KEY_USE_BEARER_TOKEN = "use_bearer_token"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_ADDITIONAL_HEADERS_JSON = "additional_headers_json"
        private const val KEY_ADDITIONAL_PAYLOAD_JSON = "additional_payload_json"
        private const val KEY_APP_ENABLED = "app_enabled"
        private const val KEY_LAST_BOOT_RESTORE_AT = "last_boot_restore_at"
    }
}
