package com.geekfrend.intouch.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueItem
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.theme.InTouchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Reusable building blocks. Each one takes plain data and callbacks and holds no app state,
 * so the visual design can restyle them freely.
 */

@Composable
fun statusColor(state: DueState): Color = when (state) {
    DueState.OVERDUE -> InTouchTheme.status.overdue
    DueState.DUE_SOON -> InTouchTheme.status.dueSoon
    DueState.ON_TRACK -> InTouchTheme.status.onTrack
}

fun otherChannel(c: Channel): Channel = if (c == Channel.WHATSAPP) Channel.PHONE else Channel.WHATSAPP

/** Contact photo from the address book, or initials. */
@Composable
fun Avatar(name: String, photoUri: String?, size: Dp) {
    val context = LocalContext.current
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
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = initials(name),
                style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** One person in the due list. On the cover screen ([compact]) it drops the photo and second button. */
@Composable
fun DueRow(item: DueItem, compact: Boolean, onOpen: () -> Unit, onCall: (Channel) -> Unit) {
    val res = LocalContext.current.resources
    val spacing = InTouchTheme.spacing
    val c = item.contact
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = spacing.l, vertical = if (compact) spacing.s else spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            Avatar(c.displayName, c.photoUri, InTouchTheme.sizes.avatarSmall)
            Spacer(Modifier.width(spacing.m))
        }
        Column(Modifier.weight(1f)) {
            Text(c.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Format.due(res, item.status),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor(item.status.state),
                maxLines = 1,
            )
            if (!compact) {
                Text(
                    Format.lastCall(res, item.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(spacing.s))
        if (!compact) {
            val other = otherChannel(c.preferredChannel)
            OutlinedButton(onClick = { onCall(other) }, contentPadding = PaddingValues(horizontal = spacing.m)) {
                Text(Format.callLabel(res, other))
            }
            Spacer(Modifier.width(spacing.xs))
        }
        FilledTonalButton(onClick = { onCall(c.preferredChannel) }, contentPadding = PaddingValues(horizontal = spacing.m)) {
            Text(Format.callLabel(res, c.preferredChannel))
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int, state: DueState) {
    val spacing = InTouchTheme.spacing
    Text(
        text = "$title · $count",
        style = MaterialTheme.typography.labelLarge,
        color = statusColor(state),
        modifier = Modifier.padding(start = spacing.l, end = spacing.l, top = spacing.l, bottom = spacing.xs),
    )
}

/** Phone and WhatsApp buttons, preferred one filled. */
@Composable
fun CallButtons(preferred: Channel, onCall: (Channel) -> Unit) {
    val res = LocalContext.current.resources
    val other = otherChannel(preferred)
    Row(horizontalArrangement = Arrangement.spacedBy(InTouchTheme.spacing.s), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { onCall(preferred) }, modifier = Modifier.weight(1f)) { Text(Format.callLabel(res, preferred)) }
        OutlinedButton(onClick = { onCall(other) }, modifier = Modifier.weight(1f)) { Text(Format.callLabel(res, other)) }
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
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (buttonLabel != null) {
            Spacer(Modifier.size(spacing.l))
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}
