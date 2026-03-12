@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.example.messageforwarder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.messageforwarder.R
import com.example.messageforwarder.data.local.DeliveryLogEntity
import com.example.messageforwarder.data.local.DeliveryStatus
import com.example.messageforwarder.model.ForwarderSettings
import com.example.messageforwarder.model.HttpMethod
import com.example.messageforwarder.util.AppTimeFormatter
import com.example.messageforwarder.util.DeviceMetadata
import com.example.messageforwarder.util.MessageMasker

private enum class AppDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SETUP(R.string.nav_setup, Icons.Outlined.TaskAlt),
    STATUS(R.string.nav_status, Icons.Outlined.Dashboard),
    LOGS(R.string.nav_logs, Icons.Outlined.History),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings),
    HEALTH(R.string.nav_health, Icons.Outlined.HealthAndSafety),
}

private data class SystemHealthSnapshot(
    val smsPermissionGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryOptimizationDisabled: Boolean,
    val appVersion: String,
    val deviceId: String,
)

@Composable
fun MessageForwarderApp(
    viewModel: MainViewModel,
    requestSmsPermission: () -> Unit,
    requestNotificationPermission: () -> Unit,
    openBatterySettings: () -> Unit,
    openAppSettings: () -> Unit,
    openNotificationSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val healthSnapshot = rememberSystemHealthSnapshot()
    val snackbarHostState = remember { SnackbarHostState() }
    val requiresSetup = !isSetupComplete(uiState.settings, healthSnapshot)
    var currentDestination by rememberSaveable {
        mutableStateOf(if (requiresSetup) AppDestination.SETUP else AppDestination.STATUS)
    }

    LaunchedEffect(requiresSetup) {
        if (requiresSetup) {
            currentDestination = AppDestination.SETUP
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // The scaffold stays fixed while each destination swaps only the content pane.
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                val label = context.getString(destination.labelRes)
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = label,
                        )
                    },
                    label = { Text(label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.title_app))
                            Text(
                                text = if (requiresSetup) {
                                    stringResource(R.string.subtitle_setup_required)
                                } else {
                                    stringResource(R.string.subtitle_background_ready)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(innerPadding),
            ) {
                when (currentDestination) {
                    AppDestination.SETUP -> SetupScreen(
                        settings = uiState.settings,
                        healthSnapshot = healthSnapshot,
                        onOpenSettings = { currentDestination = AppDestination.SETTINGS },
                        onRequestSmsPermission = requestSmsPermission,
                        onRequestNotificationsPermission = requestNotificationPermission,
                        onEnableForwarding = { viewModel.setAppEnabled(true) },
                        onOpenBatterySettings = openBatterySettings,
                    )

                    AppDestination.STATUS -> StatusScreen(
                        uiState = uiState,
                        healthSnapshot = healthSnapshot,
                        onOpenSettings = { currentDestination = AppDestination.SETTINGS },
                        onSyncNow = viewModel::syncNow,
                    )

                    AppDestination.LOGS -> LogsScreen(
                        logs = uiState.logs,
                        onRetry = viewModel::retryMessage,
                    )

                    AppDestination.SETTINGS -> SettingsScreen(
                        currentSettings = uiState.settings,
                        isTestingConnection = uiState.isTestingConnection,
                        onSave = viewModel::saveSettings,
                        onTestConnection = viewModel::testConnection,
                    )

                    AppDestination.HEALTH -> HealthScreen(
                        settings = uiState.settings,
                        healthSnapshot = healthSnapshot,
                        onRequestSmsPermission = requestSmsPermission,
                        onRequestNotificationsPermission = requestNotificationPermission,
                        onOpenBatterySettings = openBatterySettings,
                        onOpenAppSettings = openAppSettings,
                        onOpenNotificationSettings = openNotificationSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    settings: ForwarderSettings,
    healthSnapshot: SystemHealthSnapshot,
    onOpenSettings: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onEnableForwarding: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            title = stringResource(R.string.setup_title),
            subtitle = stringResource(R.string.setup_subtitle),
        )
        SetupStepCard(
            title = stringResource(R.string.setup_step_sms_title),
            description = stringResource(R.string.setup_step_sms_description),
            complete = healthSnapshot.smsPermissionGranted,
            actionLabel = stringResource(R.string.action_grant_sms_access),
            onAction = onRequestSmsPermission,
        )
        SetupStepCard(
            title = stringResource(R.string.setup_step_endpoint_title),
            description = stringResource(R.string.setup_step_endpoint_description),
            complete = settings.isApiConfigured,
            actionLabel = stringResource(R.string.action_open_settings),
            onAction = onOpenSettings,
        )
        SetupStepCard(
            title = stringResource(R.string.setup_step_forwarding_title),
            description = stringResource(R.string.setup_step_forwarding_description),
            complete = settings.appEnabled,
            actionLabel = stringResource(R.string.action_enable_forwarding),
            onAction = onEnableForwarding,
        )
        SetupStepCard(
            title = stringResource(R.string.setup_step_battery_title),
            description = stringResource(R.string.setup_step_battery_description),
            complete = healthSnapshot.batteryOptimizationDisabled,
            actionLabel = stringResource(R.string.action_open_battery_settings),
            onAction = onOpenBatterySettings,
        )
        SetupStepCard(
            title = stringResource(R.string.setup_step_notifications_title),
            description = stringResource(R.string.setup_step_notifications_description),
            complete = healthSnapshot.notificationsGranted,
            actionLabel = stringResource(R.string.action_enable_notifications),
            onAction = onRequestNotificationsPermission,
            optional = true,
        )
    }
}

@Composable
private fun StatusScreen(
    uiState: MainUiState,
    healthSnapshot: SystemHealthSnapshot,
    onOpenSettings: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val latestLog = uiState.dashboard.latestLog
    val neverLabel = stringResource(R.string.common_never)
    val emptyLabel = stringResource(R.string.common_empty)
    val apiHealthLabel = when {
        !uiState.settings.isApiConfigured -> stringResource(R.string.api_health_configuration_missing)
        latestLog?.status == DeliveryStatus.FAILED -> stringResource(R.string.api_health_retry_pending)
        uiState.dashboard.lastDeliveredAt != null -> stringResource(R.string.api_health_recently_healthy)
        else -> stringResource(R.string.api_health_waiting_first_delivery)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(
                title = if (isSetupComplete(uiState.settings, healthSnapshot)) {
                    stringResource(R.string.status_title_ready)
                } else {
                    stringResource(R.string.status_title_action_required)
                },
                subtitle = stringResource(
                    R.string.status_subtitle_summary,
                    uiState.dashboard.pendingCount,
                    apiHealthLabel,
                ),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = stringResource(R.string.metric_queue),
                    value = uiState.dashboard.pendingCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = stringResource(R.string.metric_last_delivered),
                    value = AppTimeFormatter.format(uiState.dashboard.lastDeliveredAt, neverLabel),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            StatusDetailCard(
                title = stringResource(R.string.status_latest_sms_title),
                primary = latestLog?.sender ?: stringResource(R.string.status_latest_sms_none),
                secondary = latestLog?.let { MessageMasker.maskSmsBody(it.body, emptyLabel) }
                    ?: stringResource(R.string.status_latest_sms_waiting),
                chips = listOf(
                    statusChip(
                        uiState.settings.appEnabled,
                        stringResource(R.string.status_forwarding_on),
                        stringResource(R.string.status_forwarding_off),
                    ),
                    statusChip(
                        healthSnapshot.smsPermissionGranted,
                        stringResource(R.string.status_sms_access_ready),
                        stringResource(R.string.status_sms_access_missing),
                    ),
                    apiHealthLabel,
                ),
            )
        }
        item {
            StatusDetailCard(
                title = stringResource(R.string.status_actions_title),
                primary = stringResource(R.string.status_actions_primary),
                secondary = stringResource(R.string.status_actions_secondary),
                buttons = listOf(
                    ScreenAction(stringResource(R.string.action_sync_now), onSyncNow),
                    ScreenAction(stringResource(R.string.action_open_settings), onOpenSettings),
                ),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    currentSettings: ForwarderSettings,
    isTestingConnection: Boolean,
    onSave: (ForwarderSettings) -> Unit,
    onTestConnection: (ForwarderSettings) -> Unit,
) {
    var apiUrl by remember(currentSettings.apiUrl) { mutableStateOf(currentSettings.apiUrl) }
    var httpMethod by remember(currentSettings.httpMethod) { mutableStateOf(currentSettings.httpMethod) }
    var useBearerToken by remember(currentSettings.useBearerToken) { mutableStateOf(currentSettings.useBearerToken) }
    var bearerToken by remember(currentSettings.bearerToken) { mutableStateOf(currentSettings.bearerToken) }
    var additionalHeadersJson by remember(currentSettings.additionalHeadersJson) {
        mutableStateOf(currentSettings.additionalHeadersJson)
    }
    var additionalPayloadJson by remember(currentSettings.additionalPayloadJson) {
        mutableStateOf(currentSettings.additionalPayloadJson)
    }
    var appEnabled by remember(currentSettings.appEnabled) { mutableStateOf(currentSettings.appEnabled) }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentSettings) {
        apiUrl = currentSettings.apiUrl
        httpMethod = currentSettings.httpMethod
        useBearerToken = currentSettings.useBearerToken
        bearerToken = currentSettings.bearerToken
        additionalHeadersJson = currentSettings.additionalHeadersJson
        additionalPayloadJson = currentSettings.additionalPayloadJson
        appEnabled = currentSettings.appEnabled
    }

    fun draftSettings() = ForwarderSettings(
        apiUrl = apiUrl,
        httpMethod = httpMethod,
        useBearerToken = useBearerToken,
        bearerToken = bearerToken,
        additionalHeadersJson = additionalHeadersJson,
        additionalPayloadJson = additionalPayloadJson,
        appEnabled = appEnabled,
        lastBootRestoreAt = currentSettings.lastBootRestoreAt,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_api_url_label)) },
                    placeholder = { Text(stringResource(R.string.settings_api_url_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
                Text(stringResource(R.string.settings_http_method_label), fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HttpMethod.entries.forEach { method ->
                        FilterChip(
                            selected = httpMethod == method,
                            onClick = { httpMethod = method },
                            label = { Text(method.name) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_authorization_label), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.settings_authorization_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useBearerToken,
                        onCheckedChange = { useBearerToken = it },
                    )
                }
                if (useBearerToken) {
                    OutlinedTextField(
                        value = bearerToken,
                        onValueChange = { bearerToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_bearer_token_label)) },
                        visualTransformation = if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (tokenVisible) {
                                        stringResource(R.string.settings_hide_token)
                                    } else {
                                        stringResource(R.string.settings_show_token)
                                    },
                                )
                            }
                        },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = additionalHeadersJson,
                    onValueChange = { additionalHeadersJson = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_additional_headers_label)) },
                    placeholder = { Text(stringResource(R.string.settings_additional_headers_placeholder)) },
                    minLines = 4,
                )
                OutlinedTextField(
                    value = additionalPayloadJson,
                    onValueChange = { additionalPayloadJson = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_additional_payload_label)) },
                    placeholder = { Text(stringResource(R.string.settings_additional_payload_placeholder)) },
                    minLines = 5,
                )
                Text(
                    text = if (httpMethod.supportsRequestBody) {
                        stringResource(R.string.settings_payload_with_body_description)
                    } else {
                        stringResource(R.string.settings_payload_get_description)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_forwarding_enabled_label), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.settings_forwarding_enabled_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = appEnabled,
                        onCheckedChange = { appEnabled = it },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSave(draftSettings()) }) {
                        Text(stringResource(R.string.action_save_settings))
                    }
                    OutlinedButton(
                        enabled = !isTestingConnection,
                        onClick = { onTestConnection(draftSettings()) },
                    ) {
                        Text(
                            if (isTestingConnection) {
                                stringResource(R.string.action_testing)
                            } else {
                                stringResource(R.string.action_test_connection)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsScreen(
    logs: List<DeliveryLogEntity>,
    onRetry: (String) -> Unit,
) {
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.logs_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val neverLabel = stringResource(R.string.common_never)
    val emptyLabel = stringResource(R.string.common_empty)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(logs, key = { it.messageFingerprint }) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.sender, fontWeight = FontWeight.SemiBold)
                            Text(
                                AppTimeFormatter.format(log.receivedAt, neverLabel),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(log.status)
                    }
                    Text(
                        MessageMasker.maskSmsBody(log.body, emptyLabel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.logs_retries_chip, log.retryCount)) },
                        )
                        log.simSlot?.let { slot ->
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.logs_sim_chip, slot + 1)) },
                            )
                        }
                        log.httpStatusCode?.let { code ->
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.logs_http_chip, code)) },
                            )
                        }
                    }
                    if (!log.lastError.isNullOrBlank()) {
                        Text(
                            text = log.lastError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (log.status != DeliveryStatus.DELIVERED) {
                        // Manual retry is useful when operators fix credentials or the server recovers.
                        OutlinedButton(onClick = { onRetry(log.messageFingerprint) }) {
                            Text(stringResource(R.string.action_retry_delivery))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthScreen(
    settings: ForwarderSettings,
    healthSnapshot: SystemHealthSnapshot,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            title = stringResource(R.string.health_title),
            subtitle = stringResource(R.string.health_subtitle),
        )
        HealthStatusCard(
            title = stringResource(R.string.health_sms_permission_title),
            healthy = healthSnapshot.smsPermissionGranted,
            description = stringResource(R.string.health_sms_permission_description),
            actionLabel = stringResource(R.string.action_grant_sms_access),
            onAction = onRequestSmsPermission,
        )
        HealthStatusCard(
            title = stringResource(R.string.health_notification_permission_title),
            healthy = healthSnapshot.notificationsGranted,
            description = stringResource(R.string.health_notification_permission_description),
            actionLabel = stringResource(R.string.action_open_notification_settings),
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onRequestNotificationsPermission()
                } else {
                    onOpenNotificationSettings()
                }
            },
            optional = true,
        )
        HealthStatusCard(
            title = stringResource(R.string.health_battery_title),
            healthy = healthSnapshot.batteryOptimizationDisabled,
            description = stringResource(R.string.health_battery_description),
            actionLabel = stringResource(R.string.action_open_battery_settings),
            onAction = onOpenBatterySettings,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.health_environment_title), style = MaterialTheme.typography.titleMedium)
                HealthRow(label = stringResource(R.string.health_app_version), value = healthSnapshot.appVersion)
                HealthRow(label = stringResource(R.string.health_android_id), value = healthSnapshot.deviceId)
                HealthRow(
                    label = stringResource(R.string.health_api_configured),
                    value = if (settings.isApiConfigured) {
                        stringResource(R.string.common_yes)
                    } else {
                        stringResource(R.string.common_no)
                    },
                )
                HealthRow(label = stringResource(R.string.health_http_method), value = settings.httpMethod.name)
                HealthRow(
                    label = stringResource(R.string.health_authorization),
                    value = if (settings.useBearerToken) {
                        stringResource(R.string.health_authorization_enabled)
                    } else {
                        stringResource(R.string.health_authorization_not_used)
                    },
                )
                HealthRow(
                    label = stringResource(R.string.health_last_boot_restore),
                    value = AppTimeFormatter.format(
                        settings.lastBootRestoreAt,
                        stringResource(R.string.common_never),
                    ),
                )
                OutlinedButton(onClick = onOpenAppSettings) {
                    Text(stringResource(R.string.action_open_app_details))
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B3954),
                            Color(0xFF087E8B),
                            Color(0xFFFF5A5F),
                        ),
                    ),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    title: String,
    description: String,
    complete: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (complete) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = complete,
                    onClick = {},
                    label = {
                        Text(
                            when {
                                complete -> stringResource(R.string.common_ready)
                                optional -> stringResource(R.string.common_optional)
                                else -> stringResource(R.string.common_required)
                            },
                        )
                    },
                )
            }
            if (!complete) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class ScreenAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun StatusDetailCard(
    title: String,
    primary: String,
    secondary: String,
    chips: List<String> = emptyList(),
    buttons: List<ScreenAction> = emptyList(),
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(primary, fontWeight = FontWeight.SemiBold)
            Text(
                secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { chip ->
                        AssistChip(onClick = {}, label = { Text(chip) })
                    }
                }
            }
            if (buttons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    buttons.forEach { action ->
                        OutlinedButton(onClick = action.onClick) {
                            Text(action.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthStatusCard(
    title: String,
    healthy: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                StatusDot(healthy = healthy)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (healthy) {
                    stringResource(R.string.health_status_ready)
                } else if (optional) {
                    stringResource(R.string.health_status_optional)
                } else {
                    stringResource(R.string.health_status_action_needed)
                },
                fontWeight = FontWeight.SemiBold,
            )
            if (!healthy) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusDot(healthy: Boolean) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(
                color = if (healthy) Color(0xFF2A9D8F) else Color(0xFFE76F51),
                shape = CircleShape,
            )
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
    )
}

@Composable
private fun StatusChip(status: DeliveryStatus) {
    val (containerColor, text) = when (status) {
        DeliveryStatus.RECEIVED -> Color(0xFFE9C46A) to stringResource(R.string.delivery_status_received)
        DeliveryStatus.SENDING -> Color(0xFF3D5A80) to stringResource(R.string.delivery_status_sending)
        DeliveryStatus.DELIVERED -> Color(0xFF2A9D8F) to stringResource(R.string.delivery_status_delivered)
        DeliveryStatus.FAILED -> Color(0xFFE76F51) to stringResource(R.string.delivery_status_failed)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = containerColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun rememberSystemHealthSnapshot(): SystemHealthSnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableStateOf(0) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-read permissions after bouncing out to Android settings screens.
                refreshKey += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    refreshKey

    val powerManager = context.getSystemService(PowerManager::class.java)
    val smsPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS,
    ) == PackageManager.PERMISSION_GRANTED
    val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    return SystemHealthSnapshot(
        smsPermissionGranted = smsPermissionGranted,
        notificationsGranted = notificationsGranted,
        batteryOptimizationDisabled = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
        appVersion = DeviceMetadata.getAppVersion(context),
        deviceId = DeviceMetadata.maskDeviceId(DeviceMetadata.getAndroidId(context)),
    )
}

private fun isSetupComplete(
    settings: ForwarderSettings,
    healthSnapshot: SystemHealthSnapshot,
): Boolean = settings.canForward && healthSnapshot.smsPermissionGranted

private fun statusChip(
    enabled: Boolean,
    enabledText: String,
    disabledText: String,
): String = if (enabled) enabledText else disabledText
