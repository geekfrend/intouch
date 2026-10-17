package com.geekfrend.intouch.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.ui.components.ContactTile
import com.geekfrend.intouch.ui.components.Counter
import com.geekfrend.intouch.ui.components.DashedButton
import com.geekfrend.intouch.ui.components.EmptyState
import com.geekfrend.intouch.ui.components.Label
import com.geekfrend.intouch.ui.components.SquareIconButton
import com.geekfrend.intouch.ui.theme.InTouchTheme

/**
 * FR-6 and FR-18: the home screen. A summary strip (fading / soon / strong), then everyone as
 * signal tiles, weakest first. On the Flip cover screen ([compact]) the summary is hidden.
 */
@Composable
fun DueListScreen(
    overview: DueOverview?,
    compact: Boolean,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onCall: (TrackedContact, Channel) -> Unit,
    onSettings: () -> Unit,
    onNotToday: (String) -> Unit = {},
) {
    val c = InTouchTheme.colors
    val spacing = InTouchTheme.spacing
    Scaffold(
        containerColor = c.night,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(if (compact) 52.dp else 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(13.dp)
                        .rotate(45f)
                        .shadow(10.dp, ambientColor = c.signal, spotColor = c.signal)
                        .background(c.signal)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f),
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                if (!compact) {
                    SquareIconButton(R.drawable.ic_add, stringResource(R.string.add_person), onAdd)
                    Spacer(Modifier.width(8.dp))
                }
                SquareIconButton(R.drawable.ic_settings, stringResource(R.string.settings_title), onSettings)
            }

            when {
                overview == null -> Box(Modifier.fillMaxSize())
                overview.isEmpty -> EmptyState(
                    text = stringResource(if (compact) R.string.empty_compact else R.string.empty_text),
                    buttonLabel = if (compact) null else stringResource(R.string.add_person),
                    onClick = onAdd,
                )
                // Monument has its own home: one person at a time (canvas row A). The cover screen keeps the grid.
                c.light && !compact -> MonumentHome(overview, onOpen, onCall, onNotToday)
                else -> {
                    val everyone = overview.overdue + overview.dueSoon + overview.onTrack
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = spacing.xl),
                    ) {
                        if (!compact) {
                            item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Counter(overview.overdue.size, stringResource(R.string.summary_fading), InTouchTheme.status.overdue, Modifier.weight(1f))
                                    Counter(overview.dueSoon.size, stringResource(R.string.summary_soon), InTouchTheme.status.dueSoon, Modifier.weight(1f))
                                    Counter(overview.onTrack.size, stringResource(R.string.summary_strong), InTouchTheme.status.onTrack, Modifier.weight(1f))
                                }
                            }
                            item(key = "label", span = { GridItemSpan(maxLineSpan) }) {
                                Label(stringResource(R.string.home_label), Modifier.padding(top = 8.dp))
                            }
                        }
                        items(everyone, key = { it.contact.id }) { item ->
                            ContactTile(
                                item = item,
                                compact = compact,
                                onOpen = { onOpen(item.contact.id) },
                                onCall = { ch -> onCall(item.contact, ch) },
                            )
                        }
                        if (!compact) {
                            item(key = "add", span = { GridItemSpan(maxLineSpan) }) {
                                DashedButton(stringResource(R.string.add_person), R.drawable.ic_add, onAdd, Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
