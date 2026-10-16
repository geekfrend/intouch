package com.geekfrend.intouch.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.ui.components.DiamondDot
import com.geekfrend.intouch.ui.components.GhostButton
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.Panel
import com.geekfrend.intouch.ui.components.SignalButton
import com.geekfrend.intouch.ui.theme.InTouchTheme

data class SetupState(
    val contacts: Boolean,
    val callLog: Boolean,
    val phone: Boolean,
    val sms: Boolean,
    /** Permission to post the "Did you connect?" notification. Always true below Android 13. */
    val notifications: Boolean,
    val notificationAccess: Boolean,
    val batteryExempt: Boolean,
) {
    val permissionsDone: Boolean get() = contacts && callLog && phone && sms && notifications
}

/** First-time setup: permissions, WhatsApp call detection (on by default) and background refresh. */
@Composable
fun SetupScreen(
    state: SetupState,
    onGrantPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onBattery: () -> Unit,
    onDone: () -> Unit,
) {
    val c = InTouchTheme.colors
    Scaffold(containerColor = c.night) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DiamondDot(lit = true, size = 12.dp)
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.displaySmall, color = c.text)
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge, color = c.muted)

            Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
                val rows = listOf(
                    Triple(state.contacts, R.string.setup_perm_contacts_short, R.string.setup_why_contacts),
                    Triple(state.callLog, R.string.setup_perm_call_log, R.string.setup_why_call_log),
                    Triple(state.sms, R.string.setup_perm_sms_short, R.string.setup_why_sms),
                    Triple(state.phone, R.string.setup_perm_phone, R.string.setup_why_phone),
                    Triple(state.notifications, R.string.setup_perm_notifications_short, R.string.setup_why_notifications),
                )
                rows.forEachIndexed { i, (done, label, why) ->
                    if (i > 0) HorizontalDivider(color = c.line)
                    CheckRow(done, label, why, onGrantPermissions)
                }
            }

            SettingsCard(stringResource(R.string.settings_wa_title)) {
                if (state.notificationAccess) {
                    StatusLine(stringResource(R.string.setup_wa_on), ok = true)
                } else {
                    Text(stringResource(R.string.setup_wa_body), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalButton(stringResource(R.string.setup_turn_on), null, onOpenNotificationAccess, height = 44.dp)
                        TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.open_app_info), color = c.signal) }
                    }
                }
            }

            SettingsCard(stringResource(R.string.settings_background_title)) {
                if (state.batteryExempt) {
                    StatusLine(stringResource(R.string.setup_battery_done), ok = true)
                } else {
                    Text(stringResource(R.string.settings_background_body), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    GhostButton(stringResource(R.string.open_battery_settings), null, onBattery)
                }
            }

            Spacer(Modifier.height(4.dp))
            if (state.permissionsDone) {
                SignalButton(stringResource(R.string.setup_done), null, onDone, Modifier.fillMaxWidth(), height = 56.dp)
            } else {
                SignalButton(stringResource(R.string.setup_done), null, onDone, Modifier.fillMaxWidth(), height = 56.dp, enabled = false)
                GhostButton(stringResource(R.string.setup_skip), null, onDone, Modifier.fillMaxWidth(), height = 48.dp)
            }
        }
    }
}

/** One permission: diamond (lit when granted), name and why, then ON or an Allow button. */
@Composable
private fun CheckRow(done: Boolean, @StringRes label: Int, @StringRes why: Int, onAllow: () -> Unit) {
    val c = InTouchTheme.colors
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(c.panel).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiamondDot(lit = done)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(stringResource(why), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (done) {
            Label(stringResource(R.string.setup_on), color = c.signal)
        } else {
            GhostButton(stringResource(R.string.setup_grant), null, onAllow, height = 36.dp)
        }
    }
}
