package com.example.messageforwarder

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.messageforwarder.ui.MainViewModel
import com.example.messageforwarder.ui.MessageForwarderApp
import com.example.messageforwarder.ui.theme.MessageForwarderTheme

/**
 * Hosts the single-activity Compose UI and bridges runtime permission / settings intents.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessageForwarderTheme {
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
                val smsPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        viewModel.onPermissionResult(
                            label = getString(R.string.permission_sms_access),
                            granted = granted,
                        )
                    },
                )
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        viewModel.onPermissionResult(
                            label = getString(R.string.permission_notifications),
                            granted = granted,
                        )
                    },
                )

                MessageForwarderApp(
                    viewModel = viewModel,
                    requestSmsPermission = {
                        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    },
                    requestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    openBatterySettings = { openExternal(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                    openAppSettings = {
                        openExternal(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:$packageName",
                        )
                    },
                    openNotificationSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            openExternal(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                extras = mapOf(Settings.EXTRA_APP_PACKAGE to packageName),
                            )
                        } else {
                            openExternal(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:$packageName",
                            )
                        }
                    },
                )
            }
        }
    }

    private fun openExternal(
        action: String,
        dataUri: String? = null,
        extras: Map<String, String> = emptyMap(),
    ) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            dataUri?.let { data = android.net.Uri.parse(it) }
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        startActivity(intent)
    }
}
