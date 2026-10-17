package com.geekfrend.intouch.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.selected
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
import com.geekfrend.intouch.device.Photos
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

/** A bordered panel on the night background (settings sections, setup checklist, info boxes). */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = InTouchTheme.colors.line,
    padding: Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = InTouchTheme.colors
    val shape = RoundedCornerShape(InTouchTheme.sizes.corner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, borderColor, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A small rotated square: lit (cyan, glowing) or an empty outline. */
@Composable
fun DiamondDot(lit: Boolean, size: Dp = 9.dp, color: Color = InTouchTheme.colors.signal) {
    val c = InTouchTheme.colors
    Box(
        Modifier
            .size(size)
            .rotate(45f)
            .then(if (lit) Modifier.background(color) else Modifier.border(1.5.dp, c.lineStrong))
    )
}

/** A selectable tile: cyan outline and text when selected. Optional icon above the label. */
@Composable
fun SelectTile(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    height: Dp = if (icon != null) 64.dp else 44.dp,
    background: Color = InTouchTheme.colors.panel,
    dashed: Boolean = false,
) {
    val c = InTouchTheme.colors
    val shape = RoundedCornerShape(InTouchTheme.sizes.buttonCorner)
    val color = if (selected) c.signal else c.text
    Column(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(background)
            .then(
                if (dashed && !selected) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = c.lineStrong,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                } else {
                    Modifier.border(1.dp, if (selected) c.signal else c.line, shape)
                }
            )
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A joined row of choices; the chosen one is filled cyan. */
@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = InTouchTheme.colors
    val shape = RoundedCornerShape(InTouchTheme.sizes.buttonCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .border(1.dp, c.lineStrong, shape),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.lineStrong))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) c.signal else Color.Transparent)
                    .clickable { onSelect(i) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) c.night else c.text,
                    maxLines = 1,
                )
            }
        }
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
    enabled: Boolean = true,
) {
    val c = InTouchTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(InTouchTheme.sizes.buttonCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.signal,
            contentColor = c.night,
            disabledContainerColor = c.line,
            disabledContentColor = c.muted,
        ),
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
fun DashedButton(text: String, @DrawableRes icon: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = c.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
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
    val level = signalLevel(item.status, contact.frequencyDays)
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
            .clickable(onClick = onOpen),
    ) {
        PhotoBanner(contact.displayName, contact.photoUri, Modifier.fillMaxWidth().height(if (compact) 48.dp else 76.dp))
        // Signal strip under the photo: its length is the signal level, in the status color.
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(level / 5f).fillMaxHeight().background(color))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                SignalButton(label, channelIcon(contact.preferredChannel), { onCall(contact.preferredChannel) }, Modifier.fillMaxWidth(), height = 36.dp)
            } else {
                GhostButton(label, channelIcon(contact.preferredChannel), { onCall(contact.preferredChannel) }, Modifier.fillMaxWidth(), height = 36.dp)
            }
        }
    }
}

/** Loads a contact photo off the main thread, downsampled to about [maxPx]. */
@Composable
fun rememberContactPhoto(photoUri: String?, maxPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photoUri, maxPx) {
        value = withContext(Dispatchers.IO) { Photos.load(context, photoUri, maxPx)?.asImageBitmap() }
    }
    return bitmap
}

/** The contact's photo cropped to fill the space, or their initials on their own muted color. */
@Composable
fun PhotoBanner(name: String, photoUri: String?, modifier: Modifier = Modifier, initialsStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall) {
    val c = InTouchTheme.colors
    val photo = rememberContactPhoto(photoUri, 480)
    Box(modifier.background(Color(Photos.fallbackColor(name))), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(bitmap = photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(Photos.initials(name), style = initialsStyle, color = c.text)
        }
    }
}

/** Round contact photo, or initials (add and edit screens). */
@Composable
fun Avatar(name: String, photoUri: String?, size: Dp) {
    val c = InTouchTheme.colors
    val photo = rememberContactPhoto(photoUri, 240)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(Photos.fallbackColor(name)))
            .border(1.dp, c.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(bitmap = photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(Photos.initials(name), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
    }
}

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
