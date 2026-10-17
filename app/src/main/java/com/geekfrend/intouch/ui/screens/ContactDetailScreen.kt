package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.ContactDetail
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.components.GhostButton
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.PhotoBanner
import com.geekfrend.intouch.ui.components.PanelButton
import com.geekfrend.intouch.ui.components.SignalBar
import com.geekfrend.intouch.ui.components.SignalButton
import com.geekfrend.intouch.ui.components.SquareIconButton
import com.geekfrend.intouch.ui.components.channelIcon
import com.geekfrend.intouch.ui.components.otherChannels
import com.geekfrend.intouch.ui.components.signalLevel
import com.geekfrend.intouch.ui.components.statusColor
import com.geekfrend.intouch.ui.theme.InTouchTheme
import kotlin.math.roundToInt

/** FR-16 and FR-17: one person — signal meter, ways to reach them, quick actions and their log. */
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
    val c = InTouchTheme.colors
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(containerColor = c.night, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val contact = detail.contact
        val s = detail.status
        val color = statusColor(s.state)
        val level = signalLevel(s, contact.frequencyDays)
        val firstName = contact.displayName.substringBefore(' ').ifBlank { contact.displayName }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    SquareIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Label(Format.frequency(res, contact.frequencyDays))
                    }
                    Box {
                        SquareIconButton(R.drawable.ic_more, stringResource(R.string.more)) { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menuOpen = false; onEdit() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.archive)) }, onClick = { menuOpen = false; onArchive() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; onDelete() })
                        }
                    }
                }
            }

            item(key = "photo") {
                val shape = RoundedCornerShape(InTouchTheme.sizes.corner)
                Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, if (s.state == DueState.OVERDUE) c.fading else c.line, shape)) {
                    PhotoBanner(
                        contact.displayName,
                        contact.photoUri,
                        Modifier.fillMaxWidth().height(if (compact) 104.dp else 200.dp),
                        initialsStyle = MaterialTheme.typography.displayMedium,
                    )
                    Box(Modifier.fillMaxWidth().height(4.dp).background(c.line)) {
                        Box(Modifier.fillMaxWidth(level / 5f).fillMaxHeight().background(color))
                    }
                }
            }

            item(key = "name") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        contact.displayName,
                        style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Format.due(res, s) + " · " + Format.lastCall(res, s).replaceFirstChar { it.lowercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.state == DueState.ON_TRACK) c.muted else color,
                    )
                }
            }

            if (contact.missingFromContacts) {
                item(key = "missing") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(InTouchTheme.sizes.corner))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .border(1.dp, c.fading, RoundedCornerShape(InTouchTheme.sizes.corner))
                            .padding(14.dp)
                    ) {
                        Text(stringResource(R.string.detail_missing), color = c.text, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRelink) { Text(stringResource(R.string.detail_relink), color = c.signal) }
                    }
                }
            }

            if (!compact) {
                item(key = "signal") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(InTouchTheme.sizes.corner))
                            .background(c.panel)
                            .border(1.dp, c.line, RoundedCornerShape(InTouchTheme.sizes.corner))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Label(stringResource(R.string.detail_signal), Modifier.weight(1f))
                            Label(
                                stringResource(
                                    when {
                                        level <= 2 -> R.string.signal_weak
                                        level == 3 -> R.string.signal_fair
                                        else -> R.string.signal_strong
                                    }
                                ),
                                color = color,
                            )
                        }
                        SignalBar(level * 2, color, Modifier.fillMaxWidth(), segments = 10, ascending = true, height = 48.dp)
                        val n = detail.stats.callsLast90Days
                        val gap = detail.stats.averageGapDays
                        Text(
                            listOfNotNull(
                                gap?.let { res.getString(R.string.detail_rhythm, it.roundToInt().coerceAtLeast(1)) },
                                res.getQuantityString(R.plurals.stats_calls_90, n, n),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                    }
                }
            }

            item(key = "reach") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignalButton(
                        text = res.getString(R.string.reach_format, Format.callLabel(res, contact.preferredChannel), firstName),
                        icon = channelIcon(contact.preferredChannel),
                        onClick = { onCall(contact.preferredChannel) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        otherChannels(contact.preferredChannel).forEach { ch ->
                            GhostButton(Format.callLabel(res, ch), channelIcon(ch), { onCall(ch) }, Modifier.weight(1f), height = 48.dp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelButton(stringResource(R.string.action_log_short), onLogCall, Modifier.weight(1f))
                        if (s.snoozedUntil != null) {
                            PanelButton(stringResource(R.string.action_cancel_snooze), onCancelSnooze, Modifier.weight(1f))
                        } else {
                            PanelButton(stringResource(R.string.action_snooze), onSnooze, Modifier.weight(1f))
                        }
                        PanelButton(stringResource(R.string.action_skip_short), onSkip, Modifier.weight(1f))
                    }
                }
            }

            item(key = "log-title") { Label(stringResource(R.string.history_title)) }
            if (detail.history.isEmpty()) {
                item(key = "log-empty") {
                    Text(stringResource(R.string.history_empty), color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(detail.history, key = { it.id }) { entry ->
                Column {
                    LogRow(entry, onClick = { onEntry(entry) })
                    HorizontalDivider(color = c.line)
                }
            }
        }
    }
}

/** One log line: date | what happened | how long. */
@Composable
private fun LogRow(entry: Interaction, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val c = InTouchTheme.colors
    val faded = entry.dismissed || entry.outcome != com.geekfrend.intouch.data.Outcome.CONNECTED
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(Format.shortDate(entry.occurredAt), Modifier.width(72.dp))
        Text(
            Format.logLine(res, entry),
            style = MaterialTheme.typography.bodyMedium,
            color = if (faded) c.muted else c.text,
            textDecoration = if (entry.dismissed) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.durationSec?.let { Format.duration(res, it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )
    }
}
