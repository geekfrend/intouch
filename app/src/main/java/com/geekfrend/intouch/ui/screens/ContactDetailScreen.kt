package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.ContactDetail
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.components.Avatar
import com.geekfrend.intouch.ui.components.CallButtons
import com.geekfrend.intouch.ui.components.statusColor
import com.geekfrend.intouch.ui.theme.InTouchTheme
import kotlin.math.roundToInt

/** FR-16 and FR-17: one person, their call buttons, actions and call log. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactDetailScreen(
    detail: ContactDetail?,
    compact: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCall: (Channel) -> Unit,
    onLogCall: () -> Unit,
    onSnooze: () -> Unit,
    onCancelSnooze: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onRelink: () -> Unit,
    onEntry: (Interaction) -> Unit,
) {
    val res = LocalContext.current.resources
    val spacing = InTouchTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.contact?.displayName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.archive)) }, onClick = { menuOpen = false; onArchive() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; onDelete() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val c = detail.contact
        val s = detail.status
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.displayName, c.photoUri, if (compact) InTouchTheme.sizes.avatarSmall else InTouchTheme.sizes.avatarLarge)
                    Spacer(Modifier.width(spacing.l))
                    Column {
                        Text(Format.due(res, s), style = MaterialTheme.typography.titleMedium, color = statusColor(s.state))
                        Text(Format.lastCall(res, s), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Format.frequency(res, c.frequencyDays) + " · " + Format.nextDue(res, s),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!compact) {
                            Text(c.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (c.missingFromContacts) {
                item(key = "missing") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(spacing.m)) {
                            Text(stringResource(R.string.detail_missing), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRelink) { Text(stringResource(R.string.detail_relink)) }
                        }
                    }
                }
            }
            item(key = "calls") { CallButtons(c.preferredChannel, onCall) }
            item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    OutlinedButton(onClick = onLogCall) { Text(stringResource(R.string.action_log_call)) }
                    if (s.snoozedUntil != null) {
                        OutlinedButton(onClick = onCancelSnooze) { Text(stringResource(R.string.action_cancel_snooze)) }
                    } else {
                        OutlinedButton(onClick = onSnooze) { Text(stringResource(R.string.action_snooze)) }
                    }
                    OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
                }
            }
            if (!compact) {
                item(key = "stats") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleSmall)
                            val n = detail.stats.callsLast90Days
                            Text(res.getQuantityString(R.plurals.stats_calls_90, n, n))
                            val gap = detail.stats.averageGapDays
                            Text(
                                if (gap != null) {
                                    res.getString(R.string.stats_average_gap, gap.roundToInt().coerceAtLeast(1), c.frequencyDays)
                                } else {
                                    res.getString(R.string.stats_gap_none)
                                }
                            )
                        }
                    }
                }
            }
            item(key = "history-title") {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleSmall)
            }
            if (detail.history.isEmpty()) {
                item(key = "history-empty") {
                    Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(detail.history, key = { it.id }) { entry ->
                HistoryRow(entry, onClick = { onEntry(entry) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: Interaction, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = InTouchTheme.spacing.s)
    ) {
        Text(
            Format.entryTitle(res, entry),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (entry.dismissed) TextDecoration.LineThrough else TextDecoration.None,
        )
        Text(
            Format.entrySubtitle(res, entry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
