package com.geekfrend.intouch.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geekfrend.intouch.InTouchApp
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.ContactDetail
import com.geekfrend.intouch.data.Direction
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.PendingCall
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.device.CallLauncher
import com.geekfrend.intouch.device.ContactsReader
import com.geekfrend.intouch.device.PhoneOption
import com.geekfrend.intouch.device.PickedContact
import com.geekfrend.intouch.domain.PhoneNumbers
import com.geekfrend.intouch.notify.PromptNotifier
import com.geekfrend.intouch.work.PromptWork
import com.geekfrend.intouch.ui.dialogs.LogDraft
import com.geekfrend.intouch.ui.screens.SettingsState
import com.geekfrend.intouch.ui.theme.AppTheme
import com.geekfrend.intouch.widget.WidgetUpdater
import com.geekfrend.intouch.ui.screens.SetupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** Screen state and actions. Screens get plain data from here and call back into it; they hold no logic. */
class InTouchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InTouchApp
    private val repo = app.repository
    val prefs = app.prefs

    val overview: StateFlow<DueOverview?> =
        repo.dueOverview.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val archived: StateFlow<List<TrackedContact>> =
        repo.archived.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settingsVersion: StateFlow<Int> = prefs.version

    private val _resumeTick = MutableStateFlow(0)
    val resumeTick: StateFlow<Int> = _resumeTick

    private val _prompt = MutableStateFlow<TrackedContact?>(null)
    val prompt: StateFlow<TrackedContact?> = _prompt

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    /** The contact just chosen in the picker, waiting for the Add screen. */
    var pendingPick: PickedContact? = null
        private set

    private fun str(@StringRes id: Int, vararg args: Any): String = app.resources.getString(id, *args)

    fun say(@StringRes id: Int) {
        _messages.tryEmit(UiMessage(str(id)))
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    fun detail(id: String): Flow<ContactDetail?> = repo.contactDetail(id)

    // ---- Lifecycle -----------------------------------------------------------------------------

    fun onResume() {
        _resumeTick.value = _resumeTick.value + 1
        repo.refreshToday()
        viewModelScope.launch {
            runCatching { repo.refreshFromAddressBook() }
            runCatching { repo.syncCallLog() }
            // Give WhatsApp detection a moment to log a call that just ended before asking.
            delay(1_500)
            // Ask in a notification; fall back to a pop-up only when notifications are off.
            _prompt.value = if (PromptNotifier.canPost(app)) {
                repo.notifyPromptIfDue()
                null
            } else {
                repo.pendingPrompt()
            }
        }
    }

    fun onPermissionsChanged() {
        viewModelScope.launch { runCatching { repo.syncCallLog() } }
    }

    fun answerPrompt(outcome: Outcome) {
        val c = _prompt.value ?: return
        _prompt.value = null
        viewModelScope.launch { repo.answerPrompt(c.id, outcome) }
    }

    // ---- Calling -------------------------------------------------------------------------------

    fun startCall(activity: Activity, contact: TrackedContact, channel: Channel) {
        if (channel == Channel.WHATSAPP) {
            val intent = CallLauncher.whatsAppIntent(activity, contact, prefs.region)
            if (intent == null) {
                _messages.tryEmit(
                    UiMessage(str(R.string.msg_whatsapp_missing), str(R.string.action_phone_call)) {
                        startCall(activity, contact, Channel.PHONE)
                    }
                )
                return
            }
            prefs.savePending(PendingCall(contact.id, Channel.WHATSAPP, System.currentTimeMillis()))
            PromptWork.schedule(app)
            if (!launch(activity, intent) && !launch(activity, CallLauncher.whatsAppChatIntent(contact.phoneNumber, prefs.region))) {
                prefs.clearPending()
                say(R.string.msg_call_failed)
            }
        } else if (channel == Channel.TEXT) {
            if (!launch(activity, CallLauncher.textIntent(contact.phoneNumber))) {
                say(R.string.msg_text_failed)
                return
            }
            // Logged right away, so chat-style messages the SMS history can't see still count.
            viewModelScope.launch {
                val id = repo.logTextFromApp(contact.id) ?: return@launch
                _messages.tryEmit(
                    UiMessage(str(R.string.msg_text_logged, contact.displayName), str(R.string.undo)) { deleteEntry(id) }
                )
            }
        } else if (!launch(activity, CallLauncher.phoneIntent(activity, contact.phoneNumber))) {
            say(R.string.msg_call_failed)
        }
    }

    private fun launch(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    // ---- Contacts ------------------------------------------------------------------------------

    suspend fun readPicked(uri: Uri): PickedContact? {
        val p = withContext(Dispatchers.IO) { runCatching { ContactsReader.readPicked(app, uri) }.getOrNull() }
            ?.let { repo.withLastPhoneCalls(it) }
        pendingPick = p?.takeIf { it.numbers.isNotEmpty() }
        when {
            p == null -> say(R.string.msg_contact_unreadable)
            p.numbers.isEmpty() -> say(R.string.msg_no_number)
        }
        return pendingPick
    }

    fun addContact(number: String, frequencyDays: Int, channel: Channel, lastTalked: LocalDate?, onDone: (String) -> Unit) {
        val p = pendingPick ?: return
        viewModelScope.launch {
            val id = repo.addContact(p, number, frequencyDays, channel, lastTalked)
            pendingPick = null
            onDone(id)
        }
    }

    suspend fun numbersFor(c: TrackedContact): List<PhoneOption> = withContext(Dispatchers.IO) { repo.numbersFor(c) }

    fun sameNumber(a: String, b: String): Boolean = PhoneNumbers.same(a, b, prefs.region)

    fun saveContact(id: String, number: String, frequencyDays: Int, channel: Channel) {
        viewModelScope.launch { repo.updateContact(id, number, frequencyDays, channel) }
    }

    fun relink(id: String, uri: Uri) {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { runCatching { ContactsReader.readPicked(app, uri) }.getOrNull() }
            if (p == null) say(R.string.msg_contact_unreadable) else repo.relink(id, p)
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            repo.setArchived(id, archived)
            if (archived) say(R.string.msg_archived)
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { repo.deleteContact(id) }
    }

    fun snooze(id: String, until: LocalDate?) {
        viewModelScope.launch { repo.snooze(id, until) }
    }

    fun skip(id: String) {
        viewModelScope.launch { repo.skip(id) }
    }

    fun logCall(id: String, draft: LogDraft) {
        viewModelScope.launch { repo.logCall(id, draft.time, draft.channel, draft.direction) }
    }

    fun updateEntry(entry: Interaction, draft: LogDraft) {
        viewModelScope.launch {
            repo.updateInteraction(
                entry.copy(occurredAt = draft.time, channel = draft.channel, direction = draft.direction, outcome = draft.outcome)
            )
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { repo.deleteInteraction(id) }
    }

    fun setDismissed(id: String, dismissed: Boolean) {
        viewModelScope.launch { repo.setDismissed(id, dismissed) }
    }

    // ---- Settings and setup --------------------------------------------------------------------

    fun settingsState(archived: List<TrackedContact>): SettingsState = SettingsState(
        whatsappDetection = prefs.whatsappDetection,
        notificationAccess = hasNotificationAccess(),
        notificationsAllowed = PromptNotifier.canPost(app),
        lastWhatsApp = prefs.lastWhatsAppDetectedAt.takeIf { it > 0 }?.let {
            Format.lastWhatsApp(app.resources, it, prefs.lastWhatsAppDetectedName)
        },
        minCallSeconds = prefs.minCallSeconds,
        countryCode = prefs.countryCode,
        archived = archived,
        version = versionName(),
        theme = theme(),
    )

    fun theme(): AppTheme = runCatching { AppTheme.valueOf(prefs.theme) }.getOrDefault(AppTheme.DRIFTER)

    fun setTheme(theme: AppTheme) {
        prefs.theme = theme.name
        viewModelScope.launch { WidgetUpdater.refresh(app) }
    }

    fun setupState(): SetupState = SetupState(
        contacts = granted(Manifest.permission.READ_CONTACTS),
        callLog = granted(Manifest.permission.READ_CALL_LOG),
        phone = granted(Manifest.permission.CALL_PHONE),
        sms = granted(Manifest.permission.READ_SMS),
        notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
        notificationAccess = hasNotificationAccess(),
        batteryExempt = (app.getSystemService(PowerManager::class.java))?.isIgnoringBatteryOptimizations(app.packageName) == true,
    )

    fun finishSetup() {
        prefs.setupDone = true
        onPermissionsChanged()
    }

    fun setWhatsAppDetection(on: Boolean) {
        prefs.whatsappDetection = on
    }

    fun setMinCallSeconds(seconds: Int) {
        prefs.minCallSeconds = seconds
    }

    fun setCountryCode(code: Int) {
        prefs.countryCode = code
        say(R.string.msg_saved)
    }

    private fun versionName(): String = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrElse { "" }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("Can't write file")
                }
                say(R.string.msg_exported)
            } catch (e: Exception) {
                say(R.string.msg_export_failed)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("Can't read file")
                val n = repo.importJson(text)
                _messages.tryEmit(UiMessage(app.resources.getQuantityString(R.plurals.msg_imported, n, n)))
                runCatching { repo.syncCallLog() }
            } catch (e: Exception) {
                say(R.string.msg_import_failed)
            }
        }
    }
}

/** Direction for a log entry; unknown directions are shown as incoming when editing. */
fun Direction.forEditing(): Direction = if (this == Direction.UNKNOWN) Direction.INCOMING else this
