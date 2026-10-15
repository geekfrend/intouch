package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.device.PhoneOption
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.dialogs.pickPastDate
import java.time.LocalDate
import com.geekfrend.intouch.ui.components.Avatar
import com.geekfrend.intouch.ui.theme.InTouchTheme

val FREQUENCY_PRESETS = listOf(3, 7, 14, 30, 90)

/** FR-1, FR-2, FR-4: choose a number, how often to call, and the preferred channel. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    /** Shows "When did you last talk?" (when adding someone). */
    showLastTalked: Boolean = false,
) {
    val context = LocalContext.current
    val res = context.resources
    var lastTalked by remember { mutableStateOf<LocalDate?>(null) }
    val spacing = InTouchTheme.spacing
    var number by remember { mutableStateOf(initialNumber) }
    var frequency by remember { mutableIntStateOf(initialFrequency) }
    var custom by remember { mutableStateOf(initialFrequency !in FREQUENCY_PRESETS) }
    var customText by remember { mutableStateOf(if (initialFrequency in FREQUENCY_PRESETS) "" else initialFrequency.toString()) }
    var channel by remember { mutableStateOf(initialChannel) }
    val effective: Int? = if (custom) customText.toIntOrNull()?.takeIf { it in 1..365 } else frequency

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, photoUri, InTouchTheme.sizes.avatarMedium)
                Spacer(Modifier.width(spacing.m))
                Text(name, style = MaterialTheme.typography.headlineSmall)
            }

            Text(stringResource(R.string.edit_number), style = MaterialTheme.typography.titleSmall)
            numbers.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { number = option.number },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = number == option.number, onClick = { number = option.number })
                    Column {
                        Text(option.number, style = MaterialTheme.typography.bodyLarge)
                        option.label?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Text(stringResource(R.string.edit_frequency), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                FREQUENCY_PRESETS.forEach { d ->
                    FilterChip(
                        selected = !custom && frequency == d,
                        onClick = { custom = false; frequency = d },
                        label = { Text(Format.frequency(res, d)) },
                    )
                }
                FilterChip(selected = custom, onClick = { custom = true }, label = { Text(stringResource(R.string.freq_custom)) })
            }
            if (custom) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { v -> customText = v.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.freq_custom_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            Text(stringResource(R.string.edit_channel), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                listOf(Channel.WHATSAPP, Channel.PHONE, Channel.TEXT).forEach { ch ->
                    FilterChip(selected = channel == ch, onClick = { channel = ch }, label = { Text(Format.channel(res, ch)) })
                }
            }

            if (showLastTalked) {
                Text(stringResource(R.string.add_last_talk_title), style = MaterialTheme.typography.titleSmall)
                val auto = numbers.firstOrNull { it.number == number }?.lastCallAt
                val picked = lastTalked
                Text(
                    when {
                        picked != null -> stringResource(R.string.add_last_talk_set, Format.date(picked))
                        auto != null -> stringResource(R.string.add_last_talk_auto, Format.date(Format.localDate(auto)))
                        else -> stringResource(R.string.add_last_talk_none)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    OutlinedButton(onClick = {
                        val start = picked ?: auto?.let { Format.localDate(it) } ?: LocalDate.now()
                        pickPastDate(context, start) { lastTalked = it }
                    }) { Text(stringResource(R.string.add_last_talk_pick)) }
                    if (picked != null) {
                        TextButton(onClick = { lastTalked = null }) { Text(stringResource(R.string.add_last_talk_clear)) }
                    }
                }
                Text(
                    stringResource(R.string.add_last_talk_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { effective?.let { onSave(number, it, channel, lastTalked) } },
                enabled = effective != null && number.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(saveLabel) }
        }
    }
}
