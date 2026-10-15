package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.geekfrend.intouch.R
import com.geekfrend.intouch.ui.theme.InTouchTheme

data class SetupState(
    val contacts: Boolean,
    val callLog: Boolean,
    val phone: Boolean,
    val notificationAccess: Boolean,
    val batteryExempt: Boolean,
) {
    val permissionsDone: Boolean get() = contacts && callLog && phone
}

/** First-time setup: permissions, WhatsApp call detection (on by default) and background refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    state: SetupState,
    onGrantPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onBattery: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = InTouchTheme.spacing
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)

            SettingsCard(stringResource(R.string.setup_permissions_title)) {
                Text(stringResource(R.string.setup_permissions_body), style = MaterialTheme.typography.bodyMedium)
                Check(state.contacts, R.string.setup_perm_contacts)
                Check(state.callLog, R.string.setup_perm_call_log)
                Check(state.phone, R.string.setup_perm_phone)
                if (!state.permissionsDone) {
                    Button(onClick = onGrantPermissions) { Text(stringResource(R.string.setup_grant)) }
                }
            }

            SettingsCard(stringResource(R.string.settings_wa_title)) {
                Text(stringResource(R.string.setup_wa_body), style = MaterialTheme.typography.bodyMedium)
                Check(state.notificationAccess, R.string.setup_wa_access)
                if (!state.notificationAccess) {
                    Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        Button(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.open_notification_access)) }
                        TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.open_app_info)) }
                    }
                }
            }

            SettingsCard(stringResource(R.string.settings_background_title)) {
                Text(stringResource(R.string.settings_background_body), style = MaterialTheme.typography.bodyMedium)
                Check(state.batteryExempt, R.string.setup_battery_done)
                if (!state.batteryExempt) {
                    OutlinedButton(onClick = onBattery) { Text(stringResource(R.string.open_battery_settings)) }
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.permissionsDone) R.string.setup_done else R.string.setup_skip))
            }
        }
    }
}

@Composable
private fun Check(done: Boolean, label: Int) {
    Text(
        (if (done) "✓ " else "○ ") + stringResource(label),
        color = if (done) InTouchTheme.status.onTrack else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
