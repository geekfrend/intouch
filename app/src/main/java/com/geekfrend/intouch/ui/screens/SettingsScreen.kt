package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.ui.components.DiamondDot
import com.geekfrend.intouch.ui.components.GhostButton
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.Panel
import com.geekfrend.intouch.ui.components.Segmented
import com.geekfrend.intouch.ui.components.SquareIconButton
import com.geekfrend.intouch.ui.theme.InTouchTheme

data class SettingsState(
    val whatsappDetection: Boolean,
    val notificationAccess: Boolean,
    val notificationsAllowed: Boolean,
    val lastWhatsApp: String?,
    val minCallSeconds: Int,
    val countryCode: Int,
    val archived: List<TrackedContact>,
    val version: String,
)

val MIN_CALL_OPTIONS = listOf(10, 20, 30, 45, 60)

@Composable
fun SettingsScreen(
    state: SettingsState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onToggleWhatsApp: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onMinSeconds: (Int) -> Unit,
    onCountryCode: (Int) -> Unit,
    onBattery: () -> Unit,
    onSetup: () -> Unit,
    onUnarchive: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val c = InTouchTheme.colors
    Scaffold(containerColor = c.night, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(stringResource(R.string.settings_title), R.drawable.ic_back, stringResource(R.string.back), onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsCard(
                    title = stringResource(R.string.settings_wa_title),
                    trailing = {
                        Switch(
                            checked = state.whatsappDetection,
                            onCheckedChange = onToggleWhatsApp,
                            colors = inTouchSwitchColors(),
                        )
                    },
                ) {
                    Body(stringResource(R.string.settings_wa_body))
                    if (state.whatsappDetection && !state.notificationAccess) {
                        StatusLine(stringResource(R.string.settings_wa_needs_access), ok = false)
                        Body(stringResource(R.string.restricted_hint))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(stringResource(R.string.open_notification_access), null, onOpenNotificationAccess)
                            TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.open_app_info), color = c.signal) }
                        }
                    } else if (state.whatsappDetection) {
                        StatusLine(state.lastWhatsApp ?: stringResource(R.string.settings_wa_last_none), ok = state.lastWhatsApp != null, muted = state.lastWhatsApp == null)
                    }
                }

                SettingsCard(stringResource(R.string.settings_notif_title)) {
                    Body(stringResource(R.string.settings_notif_body))
                    if (state.notificationsAllowed) {
                        StatusLine(stringResource(R.string.settings_notif_on_plain), ok = true)
                    } else {
                        StatusLine(stringResource(R.string.settings_notif_off), ok = false)
                        GhostButton(stringResource(R.string.open_notification_settings), null, onOpenNotificationSettings)
                    }
                }

                SettingsCard(stringResource(R.string.settings_min_title)) {
                    val labels = MIN_CALL_OPTIONS.map { stringResource(R.string.seconds_short, it) }
                    Segmented(labels, MIN_CALL_OPTIONS.indexOf(state.minCallSeconds), { onMinSeconds(MIN_CALL_OPTIONS[it]) })
                    Body(stringResource(R.string.settings_min_body))
                }

                SettingsCard(stringResource(R.string.settings_country_title)) {
                    var text by remember(state.countryCode) { mutableStateOf(state.countryCode.toString()) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                            prefix = { Text("+") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        val code = text.toIntOrNull()
                        TextButton(
                            onClick = { code?.let(onCountryCode) },
                            enabled = code != null && code in 1..999 && code != state.countryCode,
                        ) { Text(stringResource(R.string.save)) }
                    }
                }

                SettingsCard(stringResource(R.string.settings_background_title)) {
                    Body(stringResource(R.string.settings_background_body))
                    GhostButton(stringResource(R.string.open_battery_settings), null, onBattery)
                }

                if (state.archived.isNotEmpty()) {
                    SettingsCard(stringResource(R.string.settings_archived_title)) {
                        state.archived.forEach { a ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(a.displayName, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                                GhostButton(stringResource(R.string.unarchive), null, { onUnarchive(a.id) }, height = 36.dp)
                            }
                        }
                    }
                }

                SettingsCard(stringResource(R.string.settings_backup_title)) {
                    Body(stringResource(R.string.settings_backup_body))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(stringResource(R.string.export), null, onExport, Modifier.weight(1f))
                        GhostButton(stringResource(R.string.import_label), null, onImport, Modifier.weight(1f))
                    }
                }

                Text(
                    stringResource(R.string.settings_permissions_open) + "  →",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.signal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSetup)
                        .padding(vertical = 14.dp),
                )
                Text(stringResource(R.string.settings_version, state.version), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

/** Back/close button, centred spaced-caps title. */
@Composable
fun ScreenHeader(title: String, icon: Int, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareIconButton(icon, description, onClick)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(title, color = InTouchTheme.colors.text) }
        Spacer(Modifier.width(44.dp))
    }
}

/** One settings section: a panel with a title (and optional switch) above its content. */
@Composable
fun SettingsCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = InTouchTheme.colors.text, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = InTouchTheme.colors.muted)
}

/** A diamond and a line of status: cyan when fine, pink when something needs fixing. */
@Composable
fun StatusLine(text: String, ok: Boolean, muted: Boolean = false) {
    val c = InTouchTheme.colors
    val color = when {
        muted -> c.muted
        ok -> c.signal
        else -> c.fading
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DiamondDot(lit = !muted, size = 8.dp, color = color)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (muted) c.muted else c.text)
    }
}

@Composable
fun inTouchSwitchColors() = InTouchTheme.colors.let { c ->
    SwitchDefaults.colors(
        checkedThumbColor = c.night,
        checkedTrackColor = c.signal,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = c.muted,
        uncheckedTrackColor = c.night,
        uncheckedBorderColor = c.lineStrong,
    )
}
