package com.example.messageforwarder.data

import android.content.Context
import com.example.messageforwarder.MessageForwarderApplication
import com.example.messageforwarder.data.local.MessageForwarderDatabase
import com.example.messageforwarder.data.remote.ForwardingApiClient
import com.example.messageforwarder.data.settings.SecureSettingsStore
import com.example.messageforwarder.notification.ForwardingNotifier

/**
 * Small manual DI container so receivers, workers, and UI all resolve the same app services.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Lazies keep startup cheap while still exposing process-wide singletons.
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

val Context.appContainer: AppContainer
    get() = (applicationContext as MessageForwarderApplication).appContainer
