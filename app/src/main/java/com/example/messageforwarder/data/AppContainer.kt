package com.example.messageforwarder.data

import android.content.Context
import com.example.messageforwarder.MessageForwarderApplication
import com.example.messageforwarder.data.local.MessageForwarderDatabase
import com.example.messageforwarder.data.remote.ForwardingApiClient
import com.example.messageforwarder.data.settings.SecureSettingsStore
import com.example.messageforwarder.notification.ForwardingNotifier

/**
 * 簡單的手動 DI 容器，讓 receiver、worker 與 UI 取得同一批 app 級服務實例。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // 使用 lazy 可延後初始化，避免 app 啟動時一次建立所有單例服務。
    val database: MessageForwarderDatabase by lazy {
        MessageForwarderDatabase.create(appContext)
    }

    val settingsStore: SecureSettingsStore by lazy {
        SecureSettingsStore(appContext)
    }

    val forwardingApiClient: ForwardingApiClient by lazy {
        ForwardingApiClient(appContext)
    }

    val forwardingNotifier: ForwardingNotifier by lazy {
        ForwardingNotifier(appContext)
    }

    val forwardingRepository: ForwardingRepository by lazy {
        ForwardingRepository(
            appContext = appContext,
            database = database,
            settingsStore = settingsStore,
            apiClient = forwardingApiClient,
        )
    }
}

/**
 * 讓任何 Context 都能安全取回 Application 內的依賴容器。
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as MessageForwarderApplication).appContainer
