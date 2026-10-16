package com.geekfrend.intouch.ui.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.Direction
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.Source
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.components.DashedButton
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.SelectTile
import com.geekfrend.intouch.ui.components.SignalButton
import com.geekfrend.intouch.ui.components.channelIcon
import com.geekfrend.intouch.ui.theme.InTouchTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LogDraft(val time: Long, val channel: Channel, val direction: Direction, val outcome: Outcome)

private enum class When { NOW, YESTERDAY, PICKED }

/** FR-13 (log a call) and editing an entry, as a bottom sheet. Defaults: they reached out, WhatsApp, now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCallDialog(
    title: String,
    initial: LogDraft,
    showOutcome: Boolean,
    onDismiss: () -> Unit,
    onSave: (LogDraft) -> Unit,
    /** Shown under the title, e.g. "with Maya". */
    subtitle: String? = null,
) {
    val context = LocalContext.current
    val res = context.resources
    val c = InTouchTheme.colors
    var draft by remember { mutableStateOf(initial) }
    // Editing an entry starts on its own date; logging a new one starts on "now".
    var whenMode by remember { mutableStateOf(if (showOutcome) When.PICKED else When.NOW) }
    Sheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        }
        Section(stringResource(R.string.log_who)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Direction.INCOMING to R.string.log_they, Direction.OUTGOING to R.string.log_me).forEach { (d, label) ->
                    SelectTile(stringResource(label), draft.direction == d, { draft = draft.copy(direction = d) }, Modifier.weight(1f), background = c.night)
                }
            }
        }
        Section(stringResource(R.string.log_channel)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Channel.WHATSAPP, Channel.PHONE, Channel.TEXT, Channel.OTHER).forEach { ch ->
                    SelectTile(
                        Format.channel(res, ch), draft.channel == ch, { draft = draft.copy(channel = ch) }, Modifier.weight(1f),
                        icon = channelIcon(ch), background = c.night,
                    )
                }
            }
        }
        if (showOutcome) {
            Section(stringResource(R.string.log_outcome)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Outcome.CONNECTED, Outcome.NO_ANSWER, Outcome.LEFT_MESSAGE).forEach { o ->
                        SelectTile(Format.outcome(res, o), draft.outcome == o, { draft = draft.copy(outcome = o) }, Modifier.weight(1f), background = c.night)
                    }
                }
            }
        }
        Section(stringResource(R.string.log_when)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectTile(stringResource(R.string.log_now), whenMode == When.NOW, { whenMode = When.NOW }, Modifier.weight(1f), background = c.night)
                SelectTile(stringResource(R.string.log_yesterday), whenMode == When.YESTERDAY, { whenMode = When.YESTERDAY }, Modifier.weight(1f), background = c.night)
                SelectTile(
                    if (whenMode == When.PICKED) Format.shortDate(draft.time) + " · " + Format.time(draft.time) else stringResource(R.string.log_pick),
                    whenMode == When.PICKED,
                    {
                        pickDate(context, draft.time) { d ->
                            pickTime(context, d) { t -> draft = draft.copy(time = t); whenMode = When.PICKED }
                        }
                    },
                    Modifier.weight(if (whenMode == When.PICKED) 1.6f else 1f),
                    background = c.night,
                    dashed = whenMode != When.PICKED,
                )
            }
        }
        SignalButton(
            text = stringResource(R.string.log_save),
            icon = null,
            onClick = {
                val now = System.currentTimeMillis()
                val time = when (whenMode) {
                    When.NOW -> now
                    When.YESTERDAY -> now - 24L * 60 * 60 * 1000
                    When.PICKED -> minOf(draft.time, now)
                }
                onSave(draft.copy(time = time))
            },
            modifier = Modifier.fillMaxWidth(),
            height = 56.dp,
        )
    }
}

/** InTouch's bottom sheet: panel colour, square-ish top corners, a short handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = InTouchTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.panel,
        contentColor = c.text,
        scrimColor = Color(0x8C080614),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title)
        content()
    }
}

private fun pickDate(context: Context, time: Long, onPicked: (Long) -> Unit) {
    val z = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    val dialog = DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(z.with(LocalDate.of(y, m + 1, d)).toInstant().toEpochMilli()) },
        z.year,
        z.monthValue - 1,
        z.dayOfMonth,
    )
    dialog.datePicker.maxDate = System.currentTimeMillis()
    dialog.show()
}

/** A date picker that doesn't allow future dates. */
fun pickPastDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    val dialog = DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    )
    dialog.datePicker.maxDate = System.currentTimeMillis()
    dialog.show()
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
fun SnoozeDialog(onDismiss: () -> Unit, onSnooze: (LocalDate) -> Unit, name: String? = null) {
    val context = LocalContext.current
    val res = context.resources
    val c = InTouchTheme.colors
    val today = LocalDate.now()
    Sheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(if (name != null) stringResource(R.string.snooze_with, name) else stringResource(R.string.snooze_title))
            Text(stringResource(R.string.snooze_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(1, "1", res.getString(R.string.unit_day)),
                Triple(3, "3", res.getString(R.string.unit_days)),
                Triple(7, "1", res.getString(R.string.unit_week)),
            ).forEach { (days, big, unit) ->
                BigTile(big, unit, { onSnooze(today.plusDays(days.toLong())) }, Modifier.weight(1f))
            }
        }
        DashedButton(stringResource(R.string.snooze_pick_date) + "…", null, {
            val dialog = DatePickerDialog(
                context,
                { _, y, m, d -> onSnooze(LocalDate.of(y, m + 1, d)) },
                today.year,
                today.monthValue - 1,
                today.dayOfMonth,
            )
            dialog.datePicker.minDate = System.currentTimeMillis()
            dialog.show()
        }, Modifier.fillMaxWidth())
    }
}

/** A large thin number over a small spaced-caps unit ("3 / DAYS"). */
@Composable
private fun BigTile(number: String, unit: String, onClick: () -> Unit, modifier: Modifier) {
    val c = InTouchTheme.colors
    val shape = RoundedCornerShape(InTouchTheme.sizes.buttonCorner)
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(shape)
            .background(c.night)
            .border(1.dp, c.line, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(number, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = c.text)
        Text(unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
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
    val automatic = entry.source == Source.CALL_HISTORY || entry.source == Source.WHATSAPP_NOTIFICATION ||
        entry.source == Source.SMS_HISTORY
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
