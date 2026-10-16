package com.geekfrend.intouch.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueItem
import com.geekfrend.intouch.domain.ContactStatus
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.theme.InTouchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Reusable building blocks for the "Drifter × Monument" look. Each takes plain data and callbacks
 * and holds no app state.
 */

/** The ways to reach someone, in button order. */
val REACH_CHANNELS = listOf(Channel.WHATSAPP, Channel.PHONE, Channel.TEXT)

fun otherChannels(c: Channel): List<Channel> = REACH_CHANNELS - c

@DrawableRes
fun channelIcon(c: Channel): Int = when (c) {
    Channel.WHATSAPP -> R.drawable.ic_whatsapp
    Channel.TEXT -> R.drawable.ic_text
    else -> R.drawable.ic_call
}

@Composable
fun statusColor(state: DueState): Color = when (state) {
    DueState.OVERDUE -> InTouchTheme.status.overdue
    DueState.DUE_SOON -> InTouchTheme.status.dueSoon
    DueState.ON_TRACK -> InTouchTheme.status.onTrack
}

/**
 * Signal strength from 1 (weakest) to 5. Overdue: 1, or 2 when only a day late. Due soon: 3.
 * On track: 5 in the first half of the rhythm, 4 after.
 */
fun signalLevel(s: ContactStatus, frequencyDays: Int): Int = when (s.state) {
    DueState.OVERDUE -> if (s.daysUntilDue >= -1) 2 else 1
    DueState.DUE_SOON -> 3
    DueState.ON_TRACK -> if (s.snoozedUntil != null || s.daysUntilDue * 2 >= frequencyDays) 5 else 4
}

/** Flat segmented bar; [ascending] makes each segment taller than the last (the contact page meter). */
@Composable
fun SignalBar(
    level: Int,
    color: Color,
    modifier: Modifier = Modifier,
    segments: Int = 5,
    ascending: Boolean = false,
    height: Dp = 6.dp,
) {
    val off = InTouchTheme.colors.line
    Row(
        modifier = modifier.semantics { contentDescription = "Signal $level of $segments" },
        horizontalArrangement = Arrangement.spacedBy(if (ascending) 5.dp else 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until segments) {
            val h = if (ascending) height * (0.25f + 0.75f * (i + 1) / segments) else height
            Box(
                Modifier
                    .weight(1f)
                    .height(h)
                    .background(if (i < level) color else off)
            )
        }
    }
}

/** Spaced-capital label, e.g. "SIGNAL · WEAKEST FIRST". */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = InTouchTheme.colors.muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Square-cornered icon button with a panel background (header buttons). */
@Composable
fun SquareIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    val c = InTouchTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(InTouchTheme.sizes.buttonCorner))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(InTouchTheme.sizes.buttonCorner))
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
    }
}

/** The main call button: filled signal color. */
@Composable
fun SignalButton(
    text: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    val c = InTouchTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(InTouchTheme.sizes.buttonCorner),
        colors = ButtonDefaults.buttonColors(containerColor = c.signal, contentColor = c.night),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary button: outlined, transparent. */
@Composable
fun GhostButton(
    text: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val c = InTouchTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(InTouchTheme.sizes.buttonCorner),
        border = BorderStroke(1.dp, c.lineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small panel button with a spaced-capital label (LOG / SNOOZE / SKIP). */
@Composable
fun PanelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = InTouchTheme.colors
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(InTouchTheme.sizes.buttonCorner))
            .background(c.panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.text)
    }
}

/** A dashed-outline button (Add someone). */
@Composable
fun DashedButton(text: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = InTouchTheme.colors
    val corner = InTouchTheme.sizes.corner
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                drawRoundRect(
                    color = c.lineStrong,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = CornerRadius(corner.toPx()),
                )
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = c.text)
    }
}

/** A counter in the summary strip: big light number over a spaced label. */
@Composable
fun Counter(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    val c = InTouchTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value.toString(), style = MaterialTheme.typography.displaySmall, color = color)
        Label(label)
    }
}

/**
 * One person on the home screen: name, signal bar, status and a call button for their usual
 * channel. Fading people get a glowing magenta outline and a filled button.
 */
@Composable
fun ContactTile(item: DueItem, compact: Boolean, onOpen: () -> Unit, onCall: (Channel) -> Unit) {
    val res = LocalContext.current.resources
    val c = InTouchTheme.colors
    val sizes = InTouchTheme.sizes
    val shape = RoundedCornerShape(sizes.corner)
    val contact = item.contact
    val fading = item.status.state == DueState.OVERDUE
    val color = statusColor(item.status.state)
    val statusText = if (item.status.state == DueState.ON_TRACK && item.status.snoozedUntil == null && item.status.lastConnectedAt != null) {
        Format.lastCallShort(res, item.status)
    } else {
        Format.due(res, item.status)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) sizes.tileHeightCompact else sizes.tileHeight)
            .then(if (fading) Modifier.shadow(14.dp, shape, ambientColor = c.fading, spotColor = c.fading) else Modifier)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, if (fading) c.fading else c.line, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            contact.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SignalBar(signalLevel(item.status, contact.frequencyDays), color, Modifier.fillMaxWidth())
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (item.status.state == DueState.ON_TRACK) FontWeight.Normal else FontWeight.Bold,
            color = if (item.status.state == DueState.ON_TRACK) c.muted else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        val label = Format.channel(res, contact.preferredChannel)
        if (fading) {
            SignalButton(label, channelIcon(contact.preferredChannel), { onCall(contact.preferredChannel) }, Modifier.fillMaxWidth(), height = 40.dp)
        } else {
            GhostButton(label, channelIcon(contact.preferredChannel), { onCall(contact.preferredChannel) }, Modifier.fillMaxWidth(), height = 40.dp)
        }
    }
}

/** Contact photo from the address book, or initials (used on the add and edit screens). */
@Composable
fun Avatar(name: String, photoUri: String?, size: Dp) {
    val context = LocalContext.current
    val c = InTouchTheme.colors
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { s ->
                        BitmapFactory.decodeStream(s)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.panel)
            .border(1.dp, c.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initials(name), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
fun EmptyState(text: String, buttonLabel: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = InTouchTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = InTouchTheme.colors.text)
        if (buttonLabel != null) {
            Spacer(Modifier.size(spacing.l))
            DashedButton(buttonLabel, R.drawable.ic_add, onClick, Modifier.fillMaxWidth())
        }
    }
}
