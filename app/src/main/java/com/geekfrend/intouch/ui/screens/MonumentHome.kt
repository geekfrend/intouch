package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueItem
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.TileInfo
import com.geekfrend.intouch.ui.components.Avatar
import com.geekfrend.intouch.ui.components.GhostButton
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.SignalButton
import com.geekfrend.intouch.ui.components.channelIcon
import com.geekfrend.intouch.ui.components.otherChannels
import com.geekfrend.intouch.ui.components.statusColor
import com.geekfrend.intouch.ui.theme.InTouchTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Monument home (canvas row A): one person at a time. A large card for whoever is most due,
 * then "Up next", then everyone on track folded away at the bottom.
 */
@Composable
fun MonumentHome(
    overview: DueOverview,
    onOpen: (String) -> Unit,
    onCall: (TrackedContact, Channel) -> Unit,
    onNotToday: (String) -> Unit,
    info: TileInfo = TileInfo.TALKED,
) {
    val c = InTouchTheme.colors
    val res = LocalContext.current.resources
    val due = overview.overdue + overview.dueSoon
    val hero = due.firstOrNull()
    val upNext = due.drop(1)
    var showOnTrack by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
    ) {
        item(key = "intro") {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMM d")))
                    Text(
                        if (due.isEmpty()) stringResource(R.string.monument_all_good)
                        else res.getQuantityString(R.plurals.monument_to_reach, due.size, due.size),
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.text,
                    )
                }
                Cubes(Modifier.size(width = 104.dp, height = 84.dp))
            }
        }

        if (hero != null) {
            item(key = "hero") { HeroCard(hero, onOpen, onCall, onNotToday) }
        }

        if (upNext.isNotEmpty()) {
            item(key = "upnext") { Label(stringResource(R.string.monument_up_next), Modifier.padding(start = 4.dp, top = 6.dp)) }
            items(upNext, key = { it.contact.id }) { PersonRow(it, onOpen, onCall, info) }
        }

        if (overview.onTrack.isNotEmpty()) {
            item(key = "ontrack") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.line)
                        .clickable { showOnTrack = !showOnTrack }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Diamond(c.signal, 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        res.getQuantityString(R.plurals.monument_on_track, overview.onTrack.size, overview.onTrack.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (showOnTrack) "⌃" else "›", style = MaterialTheme.typography.titleLarge, color = c.text)
                }
            }
            if (showOnTrack) {
                items(overview.onTrack, key = { "t" + it.contact.id }) { PersonRow(it, onOpen, onCall, info) }
            }
        }
    }
}

@Composable
private fun HeroCard(
    item: DueItem,
    onOpen: (String) -> Unit,
    onCall: (TrackedContact, Channel) -> Unit,
    onNotToday: (String) -> Unit,
) {
    val c = InTouchTheme.colors
    val res = LocalContext.current.resources
    val contact = item.contact
    val first = contact.displayName.substringBefore(' ').ifBlank { contact.displayName }
    val ch = contact.preferredChannel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(28.dp))
            .clickable { onOpen(contact.id) }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(contact.displayName, contact.photoUri, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.due(res, item.status), style = MaterialTheme.typography.bodyMedium, color = statusColor(item.status.state), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(Format.lastCall(res, item.status), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        SignalButton(
            text = stringResource(R.string.monument_reach, Format.channel(res, ch), first),
            icon = channelIcon(ch),
            onClick = { onCall(contact, ch) },
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            otherChannels(ch).take(2).forEach { other ->
                GhostButton(Format.channel(res, other), channelIcon(other), { onCall(contact, other) }, Modifier.weight(1f))
            }
            TextButton(onClick = { onNotToday(contact.id) }) {
                Text(stringResource(R.string.monument_not_today), color = c.muted, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PersonRow(item: DueItem, onOpen: (String) -> Unit, onCall: (TrackedContact, Channel) -> Unit, info: TileInfo) {
    val c = InTouchTheme.colors
    val res = LocalContext.current.resources
    val contact = item.contact
    val color = statusColor(item.status.state)
    val ch = contact.preferredChannel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen(contact.id) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Diamond(color, 12.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (info) {
                TileInfo.TALKED -> Format.lastCallShort(res, item.status)
                TileInfo.DUE -> Format.due(res, item.status)
                TileInfo.BOTH -> Format.due(res, item.status) + " · " + Format.lastCallShort(res, item.status).replaceFirstChar { it.lowercase() }
            }
            Text(
                status + " · " + stringResource(R.string.monument_usually, Format.channel(res, ch).lowercase()),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status.state == DueState.ON_TRACK) c.muted else color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val label = Format.callLabel(res, ch) + " " + contact.displayName
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(c.panel)
                .clickable { onCall(contact, ch) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(channelIcon(ch)), contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Diamond(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).rotate(45f).clip(RoundedCornerShape(2.dp)).background(color))
}

/** Three small isometric blocks, after Monument Valley's architecture. */
@Composable
private fun Cubes(modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 104f
        cube(Offset(60 * u, 30 * u), 22 * u, Color(0xFFF7C9B6), Color(0xFFE9967E), Color(0xFFC86E58))
        cube(Offset(20 * u, 58 * u), 11 * u, Color(0xFFB9DDD2), Color(0xFF86BFAF), Color(0xFF5E9E8E))
        cube(Offset(86 * u, 58 * u), 13 * u, Color(0xFFCDBEE6), Color(0xFFA592CF), Color(0xFF8673B4))
    }
}

/** An isometric cube centred on [c] (top face centre) with edge [s]. */
private fun DrawScope.cube(c: Offset, s: Float, top: Color, left: Color, right: Color) {
    val h = s * 0.58f
    fun path(vararg xy: Float) = Path().apply { moveTo(xy[0], xy[1]); var i = 2; while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }; close() }
    val n = Offset(c.x, c.y - h); val e = Offset(c.x + s, c.y); val so = Offset(c.x, c.y + h); val w = Offset(c.x - s, c.y)
    drawPath(path(n.x, n.y, e.x, e.y, so.x, so.y, w.x, w.y), top)
    drawPath(path(w.x, w.y, so.x, so.y, so.x, so.y + s, w.x, w.y + s), left)
    drawPath(path(so.x, so.y, e.x, e.y, e.x, e.y + s, so.x, so.y + s), right)
}
