package com.example.messageforwarder

import android.app.Application
import com.example.messageforwarder.data.AppContainer

/**
 * Application entry point that wires the dependency container once for the whole process.
 */
class MessageForwarderApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        // Failure notifications need a channel before any worker can post them.
        appContainer.forwardingNotifier.ensureChannels()
    }
}
