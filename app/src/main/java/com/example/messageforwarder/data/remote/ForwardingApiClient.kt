package com.example.messageforwarder.data.remote

import android.content.Context
import com.example.messageforwarder.R
import com.example.messageforwarder.model.ForwardRequestPayload
import com.example.messageforwarder.model.ForwarderSettings
import com.example.messageforwarder.util.JsonConfigValidator
import com.example.messageforwarder.util.HttpsUrlValidator
import com.example.messageforwarder.util.JsonTemplateResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 封裝 HTTP 呼叫結果，統一成功與失敗時要回傳給上層的資訊。
 */
sealed interface ApiCallResult {
    data class Success(val statusCode: Int) : ApiCallResult
    data class Failure(val message: String, val statusCode: Int? = null) : ApiCallResult
}

/**
 * 將 app 設定組裝成單次 HTTP JSON 請求的輕量客戶端。
 */
class ForwardingApiClient(context: Context) {
    private val appContext = context.applicationContext

    suspend fun forward(
        settings: ForwarderSettings,
        payload: ForwardRequestPayload,
    ): ApiCallResult = withContext(Dispatchers.IO) {
        if (!HttpsUrlValidator.isValid(settings.apiUrl)) {
            return@withContext ApiCallResult.Failure(appContext.getString(R.string.error_api_url_https))
        }
        if (!JsonConfigValidator.isValidJsonObject(settings.additionalHeadersJson)) {
            return@withContext ApiCallResult.Failure(
                appContext.getString(
                    R.string.error_json_object,
                    appContext.getString(R.string.field_additional_headers),
                ),
            )
        }
        if (!JsonConfigValidator.isValidJsonObject(settings.additionalPayloadJson)) {
            return@withContext ApiCallResult.Failure(
                appContext.getString(
                    R.string.error_json_object,
                    appContext.getString(R.string.field_additional_payload),
                ),
            )
        }

        val connection = (URL(settings.apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = settings.httpMethod.name
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // GET 不送 body，避免不同伺服器或代理對 GET body 的行為不一致。
            doOutput = settings.httpMethod.supportsRequestBody
        }
        buildHeaders(settings, payload).forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        try {
            buildRequestBody(settings, payload)?.let { requestBody ->
                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(requestBody)
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                ApiCallResult.Success(responseCode)
            } else {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                ApiCallResult.Failure(
                    message = errorBody?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.error_http_status, responseCode),
                    statusCode = responseCode,
                )
            }
        } catch (exception: IOException) {
            ApiCallResult.Failure(
                exception.message ?: appContext.getString(R.string.error_network_request_failed),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun buildHeaders(
        settings: ForwarderSettings,
        payload: ForwardRequestPayload,
    ): Map<String, String> {
        val customHeaders = JsonTemplateResolver.resolveJsonObjectTemplate(
            settings.additionalHeadersJson,
            payload,
        )
        val headers = linkedMapOf(
            "Accept" to "application/json",
            "X-Message-Forwarder" to "android",
        )
        if (settings.httpMethod.supportsRequestBody) {
            headers["Content-Type"] = "application/json"
        }
        if (settings.useBearerToken && settings.bearerToken.isNotBlank()) {
            headers["Authorization"] = "Bearer ${settings.bearerToken}"
        }
        if (payload.isTest) {
            headers["X-Message-Forwarder-Test"] = "true"
        }
        customHeaders.keys().forEach { key ->
            headers[key] = customHeaders.opt(key)?.toString().orEmpty()
        }
        return headers
    }

    private fun buildRequestBody(
        settings: ForwarderSettings,
        payload: ForwardRequestPayload,
    ): String? {
        if (!settings.httpMethod.supportsRequestBody) return null

        val requestJson = JSONObject().apply {
            put("messageId", payload.messageId)
            put("sender", payload.sender)
            put("body", payload.body)
            put("receivedAt", payload.receivedAt)
            put("subscriptionId", payload.subscriptionId ?: JSONObject.NULL)
            put("simSlot", payload.simSlot ?: JSONObject.NULL)
            put("deviceId", payload.deviceId)
            put("appVersion", payload.appVersion)
            put("isTest", payload.isTest)
        }
        val additionalPayload = JsonTemplateResolver.resolveJsonObjectTemplate(
            settings.additionalPayloadJson,
            payload,
        )
        // 自訂 payload 欄位刻意覆蓋預設欄位，讓呼叫方可重塑最終 API 契約。
        additionalPayload.keys().forEach { key ->
            requestJson.put(key, additionalPayload.opt(key))
        }
        return requestJson.toString()
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
    }
}
