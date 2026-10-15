package com.geekfrend.intouch.ui.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.Direction
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.Source
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.theme.InTouchTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LogDraft(val time: Long, val channel: Channel, val direction: Direction, val outcome: Outcome)

/** FR-13 (log a call) and editing an entry. Defaults: incoming, WhatsApp, now. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogCallDialog(
    title: String,
    initial: LogDraft,
    showOutcome: Boolean,
    onDismiss: () -> Unit,
    onSave: (LogDraft) -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val spacing = InTouchTheme.spacing
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Label(R.string.log_direction)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    listOf(Direction.INCOMING, Direction.OUTGOING).forEach { d ->
                        FilterChip(selected = draft.direction == d, onClick = { draft = draft.copy(direction = d) }, label = { Text(Format.direction(res, d)) })
                    }
                }
                Label(R.string.log_channel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    listOf(Channel.WHATSAPP, Channel.PHONE, Channel.OTHER).forEach { c ->
                        FilterChip(selected = draft.channel == c, onClick = { draft = draft.copy(channel = c) }, label = { Text(Format.channel(res, c)) })
                    }
                }
                if (showOutcome) {
                    Label(R.string.log_outcome)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        listOf(Outcome.CONNECTED, Outcome.NO_ANSWER, Outcome.LEFT_MESSAGE).forEach { o ->
                            FilterChip(selected = draft.outcome == o, onClick = { draft = draft.copy(outcome = o) }, label = { Text(Format.outcome(res, o)) })
                        }
                    }
                }
                Label(R.string.log_when)
                Row {
                    TextButton(onClick = { pickDate(context, draft.time) { draft = draft.copy(time = it) } }) {
                        Text(Format.date(Format.localDate(draft.time)))
                    }
                    TextButton(onClick = { pickTime(context, draft.time) { draft = draft.copy(time = it) } }) {
                        Text(Format.time(draft.time))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
}

private fun pickDate(context: Context, time: Long, onPicked: (Long) -> Unit) {
    val z = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(z.with(LocalDate.of(y, m + 1, d)).toInstant().toEpochMilli()) },
        z.year,
        z.monthValue - 1,
        z.dayOfMonth,
    ).show()
}

private fun pickTime(context: Context, time: Long, onPicked: (Long) -> Unit) {
    val z = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    TimePickerDialog(
        context,
        { _, h, min -> onPicked(z.withHour(h).withMinute(min).withSecond(0).toInstant().toEpochMilli()) },
        z.hour,
        z.minute,
        DateFormat.is24HourFormat(context),
    ).show()
}

/** FR-14: snooze for 1 day, 3 days, 1 week or until a chosen date. */
@Composable
fun SnoozeDialog(onDismiss: () -> Unit, onSnooze: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val res = context.resources
    val today = LocalDate.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snooze_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                listOf(1, 3, 7).forEach { days ->
                    TextButton(onClick = { onSnooze(today.plusDays(days.toLong())) }) {
                        Text(res.getQuantityString(R.plurals.snooze_days, days, days))
                    }
                }
                TextButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> onSnooze(LocalDate.of(y, m + 1, d)) },
                        today.year,
                        today.monthValue - 1,
                        today.dayOfMonth,
                    ).show()
                }) { Text(stringResource(R.string.snooze_pick_date)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** FR-12: "Did you connect?" after a WhatsApp call started from InTouch. Dismissing counts as no answer. */
@Composable
fun PromptDialog(name: String, onAnswer: (Outcome) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(Outcome.NO_ANSWER) },
        title = { Text(stringResource(R.string.prompt_title, name)) },
        text = { Text(stringResource(R.string.prompt_body)) },
        confirmButton = { TextButton(onClick = { onAnswer(Outcome.CONNECTED) }) { Text(stringResource(R.string.prompt_yes)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onAnswer(Outcome.LEFT_MESSAGE) }) { Text(stringResource(R.string.prompt_left_message)) }
                TextButton(onClick = { onAnswer(Outcome.NO_ANSWER) }) { Text(stringResource(R.string.prompt_no_answer)) }
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** FR-14: what you can do with one call-log entry. */
@Composable
fun EntryActionsDialog(
    entry: Interaction,
    onEdit: () -> Unit,
    onToggleDismissed: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val res = LocalContext.current.resources
    val automatic = entry.source == Source.CALL_HISTORY || entry.source == Source.WHATSAPP_NOTIFICATION
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Format.entryTitle(res, entry)) },
        text = {
            Column {
                Text(Format.entrySubtitle(res, entry), style = MaterialTheme.typography.bodySmall)
                if (entry.outcome != Outcome.SKIPPED) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.entry_edit)) }
                }
                if (automatic) {
                    TextButton(onClick = onToggleDismissed) {
                        Text(stringResource(if (entry.dismissed) R.string.entry_undo_not_real else R.string.entry_mark_not_real))
                    }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
