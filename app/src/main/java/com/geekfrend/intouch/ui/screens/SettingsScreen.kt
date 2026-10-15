package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.ui.theme.InTouchTheme

data class SettingsState(
    val whatsappDetection: Boolean,
    val notificationAccess: Boolean,
    val lastWhatsApp: String?,
    val minCallSeconds: Int,
    val countryCode: Int,
    val archived: List<TrackedContact>,
    val version: String,
)

val MIN_CALL_OPTIONS = listOf(10, 20, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onToggleWhatsApp: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onMinSeconds: (Int) -> Unit,
    onCountryCode: (Int) -> Unit,
    onBattery: () -> Unit,
    onSetup: () -> Unit,
    onUnarchive: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val spacing = InTouchTheme.spacing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            SettingsCard(stringResource(R.string.settings_wa_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_wa_body), modifier = Modifier.weight(1f))
                    Switch(checked = state.whatsappDetection, onCheckedChange = onToggleWhatsApp)
                }
                if (state.whatsappDetection && !state.notificationAccess) {
                    Text(stringResource(R.string.settings_wa_needs_access), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        OutlinedButton(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.open_notification_access)) }
                        TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.open_app_info)) }
                    }
                }
                Text(
                    state.lastWhatsApp ?: stringResource(R.string.settings_wa_last_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(stringResource(R.string.settings_min_title)) {
                Text(stringResource(R.string.settings_min_body), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    MIN_CALL_OPTIONS.forEach { s ->
                        FilterChip(
                            selected = state.minCallSeconds == s,
                            onClick = { onMinSeconds(s) },
                            label = { Text(stringResource(R.string.seconds_short, s)) },
                        )
                    }
                }
            }

            SettingsCard(stringResource(R.string.settings_country_title)) {
                var text by remember(state.countryCode) { mutableStateOf(state.countryCode.toString()) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
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
                Text(stringResource(R.string.settings_background_body), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onBattery) { Text(stringResource(R.string.open_battery_settings)) }
            }

            SettingsCard(stringResource(R.string.settings_permissions_title)) {
                OutlinedButton(onClick = onSetup) { Text(stringResource(R.string.settings_permissions_open)) }
            }

            if (state.archived.isNotEmpty()) {
                SettingsCard(stringResource(R.string.settings_archived_title)) {
                    state.archived.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(c.displayName, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUnarchive(c.id) }) { Text(stringResource(R.string.unarchive)) }
                        }
                    }
                }
            }

            SettingsCard(stringResource(R.string.settings_backup_title)) {
                Text(stringResource(R.string.settings_backup_body), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    OutlinedButton(onClick = onExport) { Text(stringResource(R.string.export)) }
                    OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_label)) }
                }
            }

            Text(
                stringResource(R.string.settings_version, state.version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val spacing = InTouchTheme.spacing
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
