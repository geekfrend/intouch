package com.geekfrend.intouch.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.Direction
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.device.PhoneOption
import com.geekfrend.intouch.ui.dialogs.ConfirmDialog
import com.geekfrend.intouch.ui.dialogs.EntryActionsDialog
import com.geekfrend.intouch.ui.dialogs.LogCallDialog
import com.geekfrend.intouch.ui.dialogs.LogDraft
import com.geekfrend.intouch.ui.dialogs.PromptDialog
import com.geekfrend.intouch.ui.dialogs.SnoozeDialog
import com.geekfrend.intouch.ui.screens.ContactDetailScreen
import com.geekfrend.intouch.ui.screens.DueListScreen
import com.geekfrend.intouch.ui.screens.EditContactScreen
import com.geekfrend.intouch.ui.screens.SettingsScreen
import com.geekfrend.intouch.ui.screens.SetupScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

private object Routes {
    const val SETUP = "setup"
    const val DUE = "due"
    const val CONTACT = "contact/{id}"
    const val ADD = "add"
    const val EDIT = "edit/{id}"
    const val SETTINGS = "settings"
    fun contact(id: String) = "contact/$id"
    fun edit(id: String) = "edit/$id"
}

/** Below this height the layout switches to the compact cover-screen version (FR-21). */
private const val COMPACT_MAX_HEIGHT_DP = 480

private val PERMISSIONS: Array<String> = buildList {
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openSystem(activity: Activity, intent: Intent) {
    try {
        activity.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some phones hide these screens; nothing else to do.
    }
}

private fun openNotificationAccess(activity: Activity) =
    openSystem(activity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

private fun openAppInfo(activity: Activity) =
    openSystem(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))

private fun openBattery(activity: Activity) =
    openSystem(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

private fun openNotificationSettings(activity: Activity) =
    openSystem(
        activity,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
    )

/** Navigation between screens. Each route connects a stateless screen to the view model. */
@Composable
fun InTouchNav(vm: InTouchViewModel, openContact: StateFlow<String?>, onOpenHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val compact = LocalConfiguration.current.screenHeightDp < COMPACT_MAX_HEIGHT_DP
    val activity = LocalContext.current as Activity
    val start = remember { if (vm.prefs.setupDone) Routes.DUE else Routes.SETUP }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            SetupRoute(vm, activity) {
                vm.finishSetup()
                if (nav.previousBackStackEntry != null) {
                    nav.popBackStack()
                } else {
                    nav.navigate(Routes.DUE) { popUpTo(Routes.SETUP) { inclusive = true } }
                }
            }
        }
        composable(Routes.DUE) { DueRoute(vm, nav, activity, compact, snackbar) }
        composable(Routes.CONTACT) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            ContactRoute(vm, nav, activity, id, compact, snackbar)
        }
        composable(Routes.ADD) { AddRoute(vm, nav) }
        composable(Routes.EDIT) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            EditRoute(vm, id) { nav.popBackStack() }
        }
        composable(Routes.SETTINGS) { SettingsRoute(vm, nav, activity, snackbar) }
    }

    // Opening a contact from the widget.
    val request by openContact.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val id = request ?: return@LaunchedEffect
        nav.navigate(Routes.contact(id)) { launchSingleTop = true }
        onOpenHandled()
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val result = snackbar.showSnackbar(m.text, actionLabel = m.actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val prompt by vm.prompt.collectAsStateWithLifecycle()
    prompt?.let { c -> PromptDialog(c.displayName) { vm.answerPrompt(it) } }

    // People who finished setup before notifications and texts were added are asked once for each.
    val newPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }
    LaunchedEffect(Unit) {
        if (!vm.prefs.setupDone) return@LaunchedEffect
        val ask = buildList {
            if (Build.VERSION.SDK_INT >= 33 && !vm.prefs.askedNotifications &&
                !granted(activity, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!vm.prefs.askedSms && !granted(activity, Manifest.permission.READ_SMS)) add(Manifest.permission.READ_SMS)
        }
        vm.prefs.askedNotifications = true
        vm.prefs.askedSms = true
        if (ask.isNotEmpty()) newPermissions.launch(ask.toTypedArray())
    }
}

@Composable
private fun SetupRoute(vm: InTouchViewModel, activity: Activity, onDone: () -> Unit) {
    val tick by vm.resumeTick.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    val state = remember(tick, refresh) { vm.setupState() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        vm.onPermissionsChanged()
    }
    SetupScreen(
        state = state,
        onGrantPermissions = { permissions.launch(PERMISSIONS) },
        onOpenNotificationAccess = { openNotificationAccess(activity) },
        onOpenAppInfo = { openAppInfo(activity) },
        onBattery = { openBattery(activity) },
        onDone = onDone,
    )
}

@Composable
private fun DueRoute(
    vm: InTouchViewModel,
    nav: NavHostController,
    activity: Activity,
    compact: Boolean,
    snackbar: SnackbarHostState,
) {
    val overview by vm.overview.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) scope.launch { if (vm.readPicked(uri) != null) nav.navigate(Routes.ADD) }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) picker.launch(null) else vm.say(R.string.msg_contacts_permission)
    }
    DueListScreen(
        overview = overview,
        compact = compact,
        snackbar = snackbar,
        onAdd = {
            if (granted(activity, Manifest.permission.READ_CONTACTS)) {
                picker.launch(null)
            } else {
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        },
        onOpen = { nav.navigate(Routes.contact(it)) },
        onCall = { c, ch -> vm.startCall(activity, c, ch) },
        onSettings = { nav.navigate(Routes.SETTINGS) },
    )
}

private sealed interface ContactDialog {
    data object Log : ContactDialog
    data object Snooze : ContactDialog
    data object ConfirmSkip : ContactDialog
    data object ConfirmDelete : ContactDialog
    data class Entry(val interaction: Interaction) : ContactDialog
    data class EditEntry(val interaction: Interaction) : ContactDialog
}

@Composable
private fun ContactRoute(
    vm: InTouchViewModel,
    nav: NavHostController,
    activity: Activity,
    id: String,
    compact: Boolean,
    snackbar: SnackbarHostState,
) {
    val detailFlow = remember(id) { vm.detail(id) }
    val detail by detailFlow.collectAsStateWithLifecycle(initialValue = null)
    var dialog by remember { mutableStateOf<ContactDialog?>(null) }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) vm.relink(id, uri)
    }

    ContactDetailScreen(
        detail = detail,
        compact = compact,
        snackbar = snackbar,
        onBack = { nav.popBackStack() },
        onCall = { ch -> detail?.contact?.let { vm.startCall(activity, it, ch) } },
        onLogCall = { dialog = ContactDialog.Log },
        onSnooze = { dialog = ContactDialog.Snooze },
        onCancelSnooze = { vm.snooze(id, null) },
        onSkip = { dialog = ContactDialog.ConfirmSkip },
        onEdit = { nav.navigate(Routes.edit(id)) },
        onArchive = {
            vm.setArchived(id, true)
            nav.popBackStack()
        },
        onDelete = { dialog = ContactDialog.ConfirmDelete },
        onRelink = { relinkPicker.launch(null) },
        onEntry = { dialog = ContactDialog.Entry(it) },
    )

    val name = detail?.contact?.displayName.orEmpty()
    when (val d = dialog) {
        ContactDialog.Log -> LogCallDialog(
            title = stringResource(R.string.log_title),
            initial = LogDraft(System.currentTimeMillis(), Channel.WHATSAPP, Direction.INCOMING, Outcome.CONNECTED),
            showOutcome = false,
            onDismiss = { dialog = null },
            onSave = { vm.logCall(id, it); dialog = null },
        )
        ContactDialog.Snooze -> SnoozeDialog(
            onDismiss = { dialog = null },
            onSnooze = { vm.snooze(id, it); dialog = null },
        )
        ContactDialog.ConfirmSkip -> ConfirmDialog(
            title = stringResource(R.string.skip_title),
            text = stringResource(R.string.skip_body, name),
            confirmLabel = stringResource(R.string.action_skip),
            onConfirm = { vm.skip(id); dialog = null },
            onDismiss = { dialog = null },
        )
        ContactDialog.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.delete_title, name),
            text = stringResource(R.string.delete_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                dialog = null
                vm.deleteContact(id)
                nav.popBackStack()
            },
            onDismiss = { dialog = null },
        )
        is ContactDialog.Entry -> EntryActionsDialog(
            entry = d.interaction,
            onEdit = { dialog = ContactDialog.EditEntry(d.interaction) },
            onToggleDismissed = { vm.setDismissed(d.interaction.id, !d.interaction.dismissed); dialog = null },
            onDelete = { vm.deleteEntry(d.interaction.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is ContactDialog.EditEntry -> {
            val i = d.interaction
            LogCallDialog(
                title = stringResource(R.string.entry_edit),
                initial = LogDraft(i.occurredAt, i.channel, i.direction.forEditing(), i.outcome),
                showOutcome = true,
                onDismiss = { dialog = null },
                onSave = { vm.updateEntry(i, it); dialog = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun AddRoute(vm: InTouchViewModel, nav: NavHostController) {
    val p = vm.pendingPick
    if (p == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    EditContactScreen(
        title = stringResource(R.string.add_title),
        name = p.name,
        photoUri = p.photoUri,
        numbers = p.numbers,
        initialNumber = p.numbers.first().number,
        initialFrequency = 14,
        initialChannel = if (p.hasWhatsApp) Channel.WHATSAPP else Channel.PHONE,
        saveLabel = stringResource(R.string.add_save),
        onSave = { number, frequency, channel, lastTalked ->
            vm.addContact(number, frequency, channel, lastTalked) { id ->
                nav.popBackStack()
                nav.navigate(Routes.contact(id))
            }
        },
        onCancel = { nav.popBackStack() },
        showLastTalked = true,
    )
}

@Composable
private fun EditRoute(vm: InTouchViewModel, id: String, onDone: () -> Unit) {
    val detailFlow = remember(id) { vm.detail(id) }
    val detail by detailFlow.collectAsStateWithLifecycle(initialValue = null)
    val c = detail?.contact ?: return
    val numbers by produceState<List<PhoneOption>?>(initialValue = null, c.id) { value = vm.numbersFor(c) }
    val list = numbers ?: return
    val current = list.firstOrNull { vm.sameNumber(it.number, c.phoneNumber) }
    val all = if (current != null) list else listOf(PhoneOption(c.phoneNumber, null)) + list
    EditContactScreen(
        title = stringResource(R.string.edit_title),
        name = c.displayName,
        photoUri = c.photoUri,
        numbers = all,
        initialNumber = current?.number ?: c.phoneNumber,
        initialFrequency = c.frequencyDays,
        initialChannel = c.preferredChannel,
        saveLabel = stringResource(R.string.save),
        onSave = { number, frequency, channel, _ ->
            vm.saveContact(id, number, frequency, channel)
            onDone()
        },
        onCancel = onDone,
    )
}

@Composable
private fun SettingsRoute(vm: InTouchViewModel, nav: NavHostController, activity: Activity, snackbar: SnackbarHostState) {
    val version by vm.settingsVersion.collectAsStateWithLifecycle()
    val tick by vm.resumeTick.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val state = remember(version, tick, archived) { vm.settingsState(archived) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    SettingsScreen(
        state = state,
        snackbar = snackbar,
        onBack = { nav.popBackStack() },
        onToggleWhatsApp = vm::setWhatsAppDetection,
        onOpenNotificationAccess = { openNotificationAccess(activity) },
        onOpenAppInfo = { openAppInfo(activity) },
        onOpenNotificationSettings = { openNotificationSettings(activity) },
        onMinSeconds = vm::setMinCallSeconds,
        onCountryCode = vm::setCountryCode,
        onBattery = { openBattery(activity) },
        onSetup = { nav.navigate(Routes.SETUP) },
        onUnarchive = { vm.setArchived(it, false) },
        onExport = { exporter.launch("intouch-backup-${LocalDate.now()}.json") },
        onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )

    confirmImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.import_title),
            text = stringResource(R.string.import_body),
            confirmLabel = stringResource(R.string.import_label),
            onConfirm = {
                vm.importFrom(uri)
                confirmImport = null
            },
            onDismiss = { confirmImport = null },
        )
    }
}
