package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.device.PhoneOption
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.components.Avatar
import com.geekfrend.intouch.ui.components.GhostButton
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.Panel
import com.geekfrend.intouch.ui.components.REACH_CHANNELS
import com.geekfrend.intouch.ui.components.SelectTile
import com.geekfrend.intouch.ui.components.SignalButton
import com.geekfrend.intouch.ui.components.SquareIconButton
import com.geekfrend.intouch.ui.components.channelIcon
import com.geekfrend.intouch.ui.dialogs.pickPastDate
import com.geekfrend.intouch.ui.theme.InTouchTheme
import java.time.LocalDate

val FREQUENCY_PRESETS = listOf(3, 7, 14, 30, 90)

/** FR-1, FR-2, FR-4: choose a number, how often to talk, the usual channel, and (when adding) when you last talked. */
@Composable
fun EditContactScreen(
    title: String,
    name: String,
    photoUri: String?,
    numbers: List<PhoneOption>,
    initialNumber: String,
    initialFrequency: Int,
    initialChannel: Channel,
    saveLabel: String,
    onSave: (number: String, frequencyDays: Int, channel: Channel, lastTalked: LocalDate?) -> Unit,
    onCancel: () -> Unit,
    /** Shows "Last talked" (when adding someone). */
    showLastTalked: Boolean = false,
) {
    val context = LocalContext.current
    val res = context.resources
    val c = InTouchTheme.colors
    var number by remember { mutableStateOf(initialNumber) }
    var frequency by remember { mutableIntStateOf(initialFrequency) }
    var custom by remember { mutableStateOf(initialFrequency !in FREQUENCY_PRESETS) }
    var customText by remember { mutableStateOf(if (initialFrequency in FREQUENCY_PRESETS) "" else initialFrequency.toString()) }
    var channel by remember { mutableStateOf(initialChannel) }
    var lastTalked by remember { mutableStateOf<LocalDate?>(null) }
    val effective: Int? = if (custom) customText.toIntOrNull()?.takeIf { it in 1..365 } else frequency

    Scaffold(containerColor = c.night) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                SquareIconButton(R.drawable.ic_close, stringResource(R.string.cancel), onCancel)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(title) }
                Spacer(Modifier.width(44.dp))
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, photoUri, InTouchTheme.sizes.avatarMedium)
                    Spacer(Modifier.width(14.dp))
                    Text(name, style = MaterialTheme.typography.headlineMedium, color = c.text)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(stringResource(R.string.edit_number))
                    numbers.forEach { option ->
                        NumberOption(option, selected = number == option.number) { number = option.number }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(stringResource(R.string.edit_frequency))
                    val choices: List<Int?> = FREQUENCY_PRESETS + listOf(null)
                    choices.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { d ->
                                if (d == null) {
                                    SelectTile(stringResource(R.string.freq_custom_short), custom, { custom = true }, Modifier.weight(1f), dashed = true)
                                } else {
                                    SelectTile(Format.frequencyShort(res, d), !custom && frequency == d, { custom = false; frequency = d }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (custom) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { v -> customText = v.filter { it.isDigit() }.take(3) },
                            label = { Text(stringResource(R.string.freq_custom_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(stringResource(R.string.edit_channel))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REACH_CHANNELS.forEach { ch ->
                            SelectTile(Format.channel(res, ch), channel == ch, { channel = ch }, Modifier.weight(1f), icon = channelIcon(ch))
                        }
                    }
                }

                if (showLastTalked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label(stringResource(R.string.add_last_talk_title))
                        val auto = numbers.firstOrNull { it.number == number }?.lastCallAt
                        val picked = lastTalked
                        Panel(padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        picked != null -> stringResource(R.string.add_last_talk_set, Format.date(picked))
                                        auto != null -> stringResource(R.string.add_last_talk_auto, Format.date(Format.localDate(auto)))
                                        else -> stringResource(R.string.add_last_talk_none)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.text,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                GhostButton(stringResource(R.string.add_last_talk_change), null, {
                                    val start = picked ?: auto?.let { Format.localDate(it) } ?: LocalDate.now()
                                    pickPastDate(context, start) { lastTalked = it }
                                }, height = 36.dp)
                            }
                            if (picked != null) {
                                TextButton(onClick = { lastTalked = null }) { Text(stringResource(R.string.add_last_talk_clear), color = c.signal) }
                            }
                        }
                        Text(stringResource(R.string.add_last_talk_note), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }

            SignalButton(
                text = saveLabel,
                icon = null,
                onClick = { effective?.let { onSave(number, it, channel, lastTalked) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                height = 56.dp,
                enabled = effective != null && number.isNotBlank(),
            )
        }
    }
}

/** One phone number with its label (Mobile, Work…), chosen like a radio button. */
@Composable
private fun NumberOption(option: PhoneOption, selected: Boolean, onClick: () -> Unit) {
    val c = InTouchTheme.colors
    val shape = RoundedCornerShape(InTouchTheme.sizes.corner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, if (selected) c.signal else c.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).border(2.dp, if (selected) c.signal else c.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(c.signal))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(option.number, style = MaterialTheme.typography.bodyLarge, color = c.text)
            option.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
        }
    }
}
