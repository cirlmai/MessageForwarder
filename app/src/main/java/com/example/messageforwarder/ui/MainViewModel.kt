package com.example.messageforwarder.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.example.messageforwarder.MessageForwarderApplication
import com.example.messageforwarder.R
import com.example.messageforwarder.data.local.DeliveryLogEntity
import com.example.messageforwarder.data.remote.ApiCallResult
import com.example.messageforwarder.model.DashboardSnapshot
import com.example.messageforwarder.model.ForwardRequestPayload
import com.example.messageforwarder.model.ForwarderSettings
import com.example.messageforwarder.util.DeviceMetadata
import com.example.messageforwarder.util.HttpsUrlValidator
import com.example.messageforwarder.util.JsonConfigValidator
import com.example.messageforwarder.worker.SmsForwardWorkScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 主畫面需要的長生命週期 UI 狀態，會隨著設定、紀錄與儀表板資料更新。
 */
data class MainUiState(
    val settings: ForwarderSettings = ForwarderSettings(),
    val dashboard: DashboardSnapshot = DashboardSnapshot(),
    val logs: List<DeliveryLogEntity> = emptyList(),
    val isTestingConnection: Boolean = false,
)

/**
 * 僅存在畫面生命週期內的暫態 UI 狀態，不需要持久化。
 */
private data class TransientState(
    val isTestingConnection: Boolean = false,
)

/**
 * 管理畫面狀態、驗證操作人員輸入，並在需要時排程背景同步工作。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MessageForwarderApplication
    private val repository = app.appContainer.forwardingRepository
    private val _transientState = MutableStateFlow(TransientState())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        repository.observeSettings(),
        repository.observeDashboardSnapshot(),
        repository.observeDeliveryLogs(),
        _transientState,
    ) { settings, dashboard, logs, transient ->
        MainUiState(
            settings = settings,
            dashboard = dashboard,
            logs = logs,
            isTestingConnection = transient.isTestingConnection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun saveSettings(draftSettings: ForwarderSettings) {
        val normalizedSettings = draftSettings.normalized()
        validateSettings(normalizedSettings)?.let { errorMessage ->
            _messages.tryEmit(errorMessage)
            return
        }

        viewModelScope.launch {
            repository.saveSettings(normalizedSettings)
            if (normalizedSettings.canForward) {
                // 有效設定一旦存檔，就可能立刻解除既有待送訊息的阻塞。
                SmsForwardWorkScheduler.enqueue(getApplication())
            }
            _messages.emit(string(R.string.message_settings_saved))
        }
    }

    fun setAppEnabled(enabled: Boolean) {
        val current = uiState.value.settings
        saveSettings(current.copy(appEnabled = enabled))
    }

    fun syncNow() {
        SmsForwardWorkScheduler.enqueue(getApplication())
        _messages.tryEmit(string(R.string.message_forwarding_sync_scheduled))
    }

    fun retryMessage(messageFingerprint: String) {
        viewModelScope.launch {
            val requeued = repository.queueMessageForResend(messageFingerprint)
            if (requeued) {
                SmsForwardWorkScheduler.enqueue(getApplication())
                _messages.emit(string(R.string.message_requeued))
            } else {
                _messages.emit(string(R.string.message_requeue_failed))
            }
        }
    }

    fun testConnection(draftSettings: ForwarderSettings) {
        val normalizedSettings = draftSettings.normalized().copy(appEnabled = true)
        validateSettings(normalizedSettings)?.let { errorMessage ->
            _messages.tryEmit(errorMessage)
            return
        }

        // 測試路徑直接重用正式 HTTP client，讓 UI 驗證結果與實際轉送行為一致。
        _transientState.update { it.copy(isTestingConnection = true) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = repository.sendTestPayload(
                settings = normalizedSettings,
                payload = ForwardRequestPayload(
                    messageId = "test-${System.currentTimeMillis()}",
                    sender = "system:test",
                    body = string(R.string.message_connection_test_body),
                    receivedAt = System.currentTimeMillis(),
                    subscriptionId = null,
                    simSlot = null,
                    deviceId = DeviceMetadata.getAndroidId(context),
                    appVersion = DeviceMetadata.getAppVersion(context),
                    isTest = true,
                ),
            )
            _transientState.update { it.copy(isTestingConnection = false) }
            val message = when (result) {
                is ApiCallResult.Success ->
                    string(R.string.message_connection_test_succeeded, result.statusCode)

                is ApiCallResult.Failure ->
                    string(R.string.message_connection_test_failed, result.message)
            }
            _messages.emit(message)
        }
    }

    fun onPermissionResult(label: String, granted: Boolean) {
        _messages.tryEmit(
            if (granted) {
                string(R.string.message_permission_granted, label)
            } else {
                string(R.string.message_permission_denied, label)
            },
        )
    }

    private fun validateSettings(settings: ForwarderSettings): String? {
        if (settings.apiUrl.isBlank()) {
            return string(R.string.error_api_url_required)
        }
        if (!HttpsUrlValidator.isValid(settings.apiUrl)) {
            return string(R.string.error_api_url_https)
        }
        if (settings.useBearerToken && settings.bearerToken.isBlank()) {
            return string(R.string.error_bearer_token_required)
        }
        if (!JsonConfigValidator.isValidJsonObject(settings.additionalHeadersJson)) {
            return string(
                R.string.error_json_object,
                string(R.string.field_additional_headers),
            )
        }
        if (!JsonConfigValidator.isValidJsonObject(settings.additionalPayloadJson)) {
            return string(
                R.string.error_json_object,
                string(R.string.field_additional_payload),
            )
        }
        return null
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    // 在持久化前統一裁掉空白，確保所有呼叫端看到的都是同一份正規化結果。
    private fun ForwarderSettings.normalized(): ForwarderSettings = copy(
        apiUrl = apiUrl.trim(),
        bearerToken = bearerToken.trim(),
        additionalHeadersJson = additionalHeadersJson.trim(),
        additionalPayloadJson = additionalPayloadJson.trim(),
        allowedSendersRaw = allowedSendersRaw.trim(),
        requiredKeywordsRaw = requiredKeywordsRaw.trim(),
    )

    companion object {
        /**
         * 讓 Compose 與 Activity 端都能用同一套方式建立 ViewModel。
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
            }
        }
    }
}
