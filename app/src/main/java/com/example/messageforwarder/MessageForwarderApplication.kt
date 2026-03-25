package com.example.messageforwarder

import android.app.Application
import com.example.messageforwarder.data.AppContainer

/**
 * Application 入口，於整個 app 行程存活期間只建立一次依賴容器。
 */
class MessageForwarderApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        // 失敗通知必須先建立通知頻道，背景 worker 才能正常發送提醒。
        appContainer.forwardingNotifier.ensureChannels()
    }
}
