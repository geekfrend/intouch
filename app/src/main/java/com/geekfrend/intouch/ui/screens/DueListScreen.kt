package com.geekfrend.intouch.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueItem
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.components.DueRow
import com.geekfrend.intouch.ui.components.EmptyState
import com.geekfrend.intouch.ui.components.SectionHeader
import com.geekfrend.intouch.ui.theme.InTouchTheme

/** FR-6 and FR-18: the app's main screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueListScreen(
    overview: DueOverview?,
    compact: Boolean,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onCall: (TrackedContact, Channel) -> Unit,
    onSettings: () -> Unit,
) {
    val res = LocalContext.current.resources
    val spacing = InTouchTheme.spacing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!compact) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_person))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            overview == null -> Box(Modifier.fillMaxSize().padding(padding))
            overview.isEmpty -> EmptyState(
                text = stringResource(if (compact) R.string.empty_compact else R.string.empty_text),
                buttonLabel = if (compact) null else stringResource(R.string.add_person),
                onClick = onAdd,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = if (compact) spacing.l else InTouchTheme.sizes.listBottomPadding),
            ) {
                if (!compact) {
                    item(key = "summary") {
                        Text(
                            Format.summary(res, overview.summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.s),
                        )
                    }
                }
                section(R.string.section_overdue, overview.overdue, DueState.OVERDUE, compact, onOpen, onCall)
                section(R.string.section_due_soon, overview.dueSoon, DueState.DUE_SOON, compact, onOpen, onCall)
                section(R.string.section_on_track, overview.onTrack, DueState.ON_TRACK, compact, onOpen, onCall)
            }
        }
    }
}

private fun LazyListScope.section(
    @StringRes title: Int,
    list: List<DueItem>,
    state: DueState,
    compact: Boolean,
    onOpen: (String) -> Unit,
    onCall: (TrackedContact, Channel) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "header-$state") { SectionHeader(stringResource(title), list.size, state) }
    items(list, key = { it.contact.id }) { item ->
        DueRow(
            item = item,
            compact = compact,
            onOpen = { onOpen(item.contact.id) },
            onCall = { channel -> onCall(item.contact, channel) },
        )
    }
}
