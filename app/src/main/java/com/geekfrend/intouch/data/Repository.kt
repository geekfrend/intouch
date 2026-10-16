package com.geekfrend.intouch.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.geekfrend.intouch.device.CallLogReader
import com.geekfrend.intouch.device.ContactsReader
import com.geekfrend.intouch.device.PhoneOption
import com.geekfrend.intouch.device.PickedContact
import com.geekfrend.intouch.device.SmsReader
import com.geekfrend.intouch.domain.ContactStats
import com.geekfrend.intouch.domain.ContactStatus
import com.geekfrend.intouch.domain.DAY_MS
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.domain.EndedWaCall
import com.geekfrend.intouch.domain.HOUR_MS
import com.geekfrend.intouch.domain.PhoneNumbers
import com.geekfrend.intouch.domain.StatusCalculator
import com.geekfrend.intouch.domain.Summary
import com.geekfrend.intouch.notify.PromptNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class DueItem(val contact: TrackedContact, val status: ContactStatus)

data class DueOverview(
    val overdue: List<DueItem>,
    val dueSoon: List<DueItem>,
    val onTrack: List<DueItem>,
    val summary: Summary,
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && dueSoon.isEmpty() && onTrack.isEmpty()
}

data class ContactDetail(
    val contact: TrackedContact,
    val status: ContactStatus,
    val stats: ContactStats,
    val history: List<Interaction>,
)

/** All reads and writes go through here. Screens, the widget, the worker and the notification listener share it. */
class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
) {
    private val contacts = db.contactDao()
    private val interactions = db.interactionDao()
    private val today = MutableStateFlow(LocalDate.now())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun newId() = UUID.randomUUID().toString()

    /** Call when the app comes to the front or a day may have passed. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    // ---- Reading -------------------------------------------------------------------------------

    val archived: Flow<List<TrackedContact>> = contacts.observeArchived()

    val dueOverview: Flow<DueOverview> =
        combine(contacts.observeActive(), interactions.observeAll(), today) { cs, all, day -> overview(cs, all, day) }

    suspend fun currentOverview(): DueOverview = withContext(Dispatchers.IO) {
        overview(contacts.allActive(), interactions.all(), LocalDate.now())
    }

    fun contactDetail(id: String): Flow<ContactDetail?> =
        combine(contacts.observe(id), interactions.observeFor(id), today) { c, list, day ->
            c?.let {
                ContactDetail(
                    contact = it,
                    status = StatusCalculator.status(it, list, day, zone),
                    stats = StatusCalculator.stats(list, System.currentTimeMillis()),
                    history = list,
                )
            }
        }

    private fun overview(cs: List<TrackedContact>, all: List<Interaction>, day: LocalDate): DueOverview {
        val byContact = all.groupBy { it.contactId }
        val items = cs.map { DueItem(it, StatusCalculator.status(it, byContact[it.id].orEmpty(), day, zone)) }
        val activeIds = cs.map { it.id }.toSet()
        return DueOverview(
            overdue = items.filter { it.status.state == DueState.OVERDUE }.sortedBy { it.status.daysUntilDue },
            dueSoon = items.filter { it.status.state == DueState.DUE_SOON }.sortedBy { it.status.daysUntilDue },
            onTrack = items.filter { it.status.state == DueState.ON_TRACK }.sortedBy { it.status.daysUntilDue },
            summary = StatusCalculator.summary(
                items.map { it.status },
                all.filter { it.contactId in activeIds },
                day,
                zone,
            ),
        )
    }

    // ---- Contacts ------------------------------------------------------------------------------

    /** Fills in each number's last connected phone call or text in the past 90 days, for the add screen. */
    suspend fun withLastPhoneCalls(p: PickedContact): PickedContact = withContext(Dispatchers.IO) {
        val region = prefs.region
        val minSeconds = prefs.minCallSeconds
        val since = System.currentTimeMillis() - 90 * DAY_MS
        val calls = mutableListOf<Pair<String, Long>>()
        if (granted(Manifest.permission.READ_CALL_LOG)) {
            runCatching { CallLogReader.since(context, since) }.getOrElse { emptyList() }
                .filter { (it.type == CallLog.Calls.INCOMING_TYPE || it.type == CallLog.Calls.OUTGOING_TYPE) && it.durationSec >= minSeconds }
                .forEach { calls.add(PhoneNumbers.normalize(it.number, region) to it.date) }
        }
        if (granted(Manifest.permission.READ_SMS)) {
            runCatching { SmsReader.since(context, since, p.numbers.map { it.number }) }.getOrElse { emptyList() }
                .forEach { calls.add(PhoneNumbers.normalize(it.address, region) to it.date) }
        }
        if (calls.isEmpty()) return@withContext p
        p.copy(
            numbers = p.numbers.map { option ->
                val n = PhoneNumbers.normalize(option.number, region)
                option.copy(lastCallAt = calls.filter { PhoneNumbers.matches(it.first, n) }.maxOfOrNull { it.second })
            }
        )
    }

    /**
     * FR-1 to FR-3: adds the person, then pulls their last 90 days from the call history.
     * [lastTalked] is the date you set yourself, e.g. for a WhatsApp call the app couldn't see.
     */
    suspend fun addContact(
        p: PickedContact,
        number: String,
        frequencyDays: Int,
        channel: Channel,
        lastTalked: LocalDate?,
    ): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val c = TrackedContact(
                id = newId(),
                lookupKey = p.lookupKey,
                systemContactId = p.contactId,
                displayName = p.name,
                photoUri = p.photoUri,
                phoneNumber = PhoneNumbers.normalize(number, prefs.region),
                preferredChannel = channel,
                frequencyDays = frequencyDays,
                snoozedUntilEpochDay = null,
                createdAt = now,
                archived = false,
                missingFromContacts = false,
            )
            contacts.upsert(c)
            importCallHistory(listOf(c), now - 90 * DAY_MS)
            importTexts(listOf(c), now - 90 * DAY_MS)
            if (lastTalked != null) {
                val at = lastTalked.atTime(12, 0).atZone(zone).toInstant().toEpochMilli().coerceAtMost(now)
                interactions.insert(
                    Interaction(newId(), c.id, at, channel, Direction.UNKNOWN, Outcome.CONNECTED,
                        Source.MANUAL, null, null, false)
                )
            }
            c.id
        }

    suspend fun updateContact(id: String, number: String, frequencyDays: Int, channel: Channel) =
        withContext(Dispatchers.IO) {
            val c = contacts.get(id) ?: return@withContext
            val normalized = PhoneNumbers.normalize(number, prefs.region)
            val updated = c.copy(phoneNumber = normalized, frequencyDays = frequencyDays, preferredChannel = channel)
            contacts.upsert(updated)
            if (!PhoneNumbers.matches(normalized, c.phoneNumber)) {
                importCallHistory(listOf(updated), System.currentTimeMillis() - 90 * DAY_MS)
                importTexts(listOf(updated), System.currentTimeMillis() - 90 * DAY_MS)
            }
        }

    /** FR-5: points a contact whose address-book entry disappeared at a newly picked one. */
    suspend fun relink(id: String, p: PickedContact) = withContext(Dispatchers.IO) {
        val c = contacts.get(id) ?: return@withContext
        val region = prefs.region
        val keepNumber = p.numbers.any { PhoneNumbers.same(it.number, c.phoneNumber, region) }
        val number = if (keepNumber || p.numbers.isEmpty()) c.phoneNumber else PhoneNumbers.normalize(p.numbers.first().number, region)
        contacts.upsert(
            c.copy(
                lookupKey = p.lookupKey,
                systemContactId = p.contactId,
                displayName = p.name,
                photoUri = p.photoUri,
                phoneNumber = number,
                missingFromContacts = false,
            )
        )
    }

    suspend fun setArchived(id: String, archived: Boolean) = withContext(Dispatchers.IO) {
        contacts.get(id)?.let { contacts.upsert(it.copy(archived = archived)) }
    }

    suspend fun deleteContact(id: String) = withContext(Dispatchers.IO) { contacts.delete(id) }

    suspend fun snooze(id: String, until: LocalDate?) = withContext(Dispatchers.IO) {
        contacts.get(id)?.let { contacts.upsert(it.copy(snoozedUntilEpochDay = until?.toEpochDay())) }
    }

    /** Reads names and photos again from the address book (FR-5). */
    suspend fun refreshFromAddressBook() = withContext(Dispatchers.IO) {
        if (!granted(Manifest.permission.READ_CONTACTS)) return@withContext
        for (c in contacts.all()) {
            val r = ContactsReader.resolve(context, c.lookupKey, c.systemContactId)
            val updated = if (r == null) {
                c.copy(missingFromContacts = true)
            } else {
                c.copy(
                    displayName = r.name.ifBlank { c.displayName },
                    systemContactId = r.contactId,
                    lookupKey = r.lookupKey ?: c.lookupKey,
                    photoUri = r.photoUri,
                    missingFromContacts = false,
                )
            }
            if (updated != c) contacts.upsert(updated)
        }
    }

    fun numbersFor(c: TrackedContact): List<PhoneOption> =
        c.systemContactId?.let { ContactsReader.numbers(context, it) }.orEmpty()

    // ---- Call log entries ----------------------------------------------------------------------

    /** FR-15: resets the timer without counting as a call. */
    suspend fun skip(contactId: String) = withContext(Dispatchers.IO) {
        interactions.insert(
            Interaction(newId(), contactId, System.currentTimeMillis(), Channel.OTHER, Direction.UNKNOWN,
                Outcome.SKIPPED, Source.MANUAL, null, null, false)
        )
    }

    /** FR-13: a call made outside the app's view, saved as connected. */
    suspend fun logCall(contactId: String, time: Long, channel: Channel, direction: Direction) =
        withContext(Dispatchers.IO) {
            interactions.insert(
                Interaction(newId(), contactId, time, channel, direction, Outcome.CONNECTED, Source.MANUAL, null, null, false)
            )
        }

    suspend fun updateInteraction(i: Interaction) = withContext(Dispatchers.IO) { interactions.update(i) }

    suspend fun deleteInteraction(id: String) = withContext(Dispatchers.IO) { interactions.delete(id) }

    /** FR-14: "Not a real call". The entry stays so the call history doesn't add it again. */
    suspend fun setDismissed(id: String, dismissed: Boolean) = withContext(Dispatchers.IO) {
        interactions.get(id)?.let { interactions.update(it.copy(dismissed = dismissed)) }
    }

    // ---- Automatic tracking --------------------------------------------------------------------

    /** FR-10: reads new call-history and SMS entries for tracked numbers. Returns how many were added. */
    suspend fun syncCallLog(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val active = contacts.allActive()
        var added = 0
        if (granted(Manifest.permission.READ_CALL_LOG)) {
            val last = prefs.lastCallLogSync
            added += importCallHistory(active, if (last == 0L) now - 90 * DAY_MS else last - HOUR_MS)
            prefs.lastCallLogSync = now
        }
        if (granted(Manifest.permission.READ_SMS)) {
            val last = prefs.lastSmsSync
            added += importTexts(active, if (last == 0L) now - 90 * DAY_MS else last - HOUR_MS)
            prefs.lastSmsSync = now
        }
        added
    }

    /** One text entry per person per day, so a long conversation doesn't flood the call log. */
    private fun textKey(contactId: String, time: Long): String =
        "text:$contactId:${java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay()}"

    /** Texts sent or received with tracked numbers, from the SMS history. Message text is never read. */
    private suspend fun importTexts(cs: List<TrackedContact>, since: Long): Int {
        if (cs.isEmpty() || !granted(Manifest.permission.READ_SMS)) return 0
        val region = prefs.region
        val keyed = cs.map { it to PhoneNumbers.normalize(it.phoneNumber, region) }
        val rows = try {
            // Only tracked numbers are asked for, so texts with anyone else never reach InTouch.
            SmsReader.since(context, since, cs.map { it.phoneNumber })
        } catch (e: SecurityException) {
            return 0
        }
        var added = 0
        for (r in rows) {
            val number = PhoneNumbers.normalize(r.address, region)
            val contact = keyed.firstOrNull { PhoneNumbers.matches(it.second, number) }?.first ?: continue
            val id = interactions.insert(
                Interaction(newId(), contact.id, r.date, Channel.TEXT,
                    if (r.sent) Direction.OUTGOING else Direction.INCOMING,
                    Outcome.CONNECTED, Source.SMS_HISTORY, null, textKey(contact.id, r.date), false)
            )
            if (id != -1L) added++
        }
        return added
    }

    /** A text started from InTouch. Returns the entry's ID (for Undo), or null if today already has one. */
    suspend fun logTextFromApp(contactId: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = newId()
        val row = interactions.insert(
            Interaction(id, contactId, now, Channel.TEXT, Direction.OUTGOING, Outcome.CONNECTED,
                Source.MANUAL, null, textKey(contactId, now), false)
        )
        if (row == -1L) null else id
    }

    private suspend fun importCallHistory(cs: List<TrackedContact>, since: Long): Int {
        if (cs.isEmpty() || !granted(Manifest.permission.READ_CALL_LOG)) return 0
        val region = prefs.region
        val minSeconds = prefs.minCallSeconds
        val keyed = cs.map { it to PhoneNumbers.normalize(it.phoneNumber, region) }
        val rows = try {
            CallLogReader.since(context, since)
        } catch (e: SecurityException) {
            return 0
        }
        var added = 0
        for (r in rows) {
            val number = PhoneNumbers.normalize(r.number, region)
            val contact = keyed.firstOrNull { PhoneNumbers.matches(it.second, number) }?.first ?: continue
            val direction: Direction
            val outcome: Outcome
            when (r.type) {
                CallLog.Calls.OUTGOING_TYPE -> {
                    direction = Direction.OUTGOING
                    outcome = if (r.durationSec >= minSeconds) Outcome.CONNECTED else Outcome.NO_ANSWER
                }
                CallLog.Calls.INCOMING_TYPE -> {
                    if (r.durationSec < minSeconds) continue
                    direction = Direction.INCOMING
                    outcome = Outcome.CONNECTED
                }
                else -> continue // missed, rejected, voicemail, blocked
            }
            val id = interactions.insert(
                Interaction(newId(), contact.id, r.date, Channel.PHONE, direction, outcome,
                    Source.CALL_HISTORY, r.durationSec, "calllog:${r.id}", false)
            )
            if (id != -1L) added++
        }
        return added
    }

    /** FR-11: a finished WhatsApp call from the notification listener. */
    suspend fun recordWhatsAppCall(call: EndedWaCall) = withContext(Dispatchers.IO) {
        val minSeconds = prefs.minCallSeconds
        val contact = contacts.allActive().firstOrNull {
            it.displayName.trim().equals(call.title.trim(), ignoreCase = true)
        }
        if (call.durationSec >= minSeconds) {
            prefs.lastWhatsAppDetectedAt = call.start + call.durationSec * 1000
            // Only a tracked person's name is kept; other people's names are never stored.
            prefs.lastWhatsAppDetectedName = contact?.displayName ?: ""
        }
        if (contact == null) return@withContext
        val outcome = when {
            call.durationSec >= minSeconds -> Outcome.CONNECTED
            call.outgoing -> Outcome.NO_ANSWER
            else -> return@withContext
        }
        interactions.insert(
            Interaction(newId(), contact.id, call.start, Channel.WHATSAPP,
                if (call.outgoing) Direction.OUTGOING else Direction.INCOMING,
                outcome, Source.WHATSAPP_NOTIFICATION, call.durationSec, "wa:${contact.id}:${call.start}", false)
        )
        if (prefs.pending()?.contactId == contact.id) {
            prefs.clearPending()
            PromptNotifier.cancel(context, contact.id)
        }
    }

    /**
     * FR-12: the person to ask "Did you connect?" about, or null. Asks only for a WhatsApp call
     * started from InTouch in the last 2 hours that detection hasn't logged and isn't still going.
     */
    suspend fun pendingPrompt(): TrackedContact? = withContext(Dispatchers.IO) {
        val p = prefs.pending() ?: return@withContext null
        val now = System.currentTimeMillis()
        if (p.channel != Channel.WHATSAPP || now - p.startedAt > 2 * HOUR_MS) {
            prefs.clearPending()
            PromptNotifier.cancel(context, p.contactId)
            return@withContext null
        }
        val c = contacts.get(p.contactId)
        if (c == null || interactions.countWhatsAppSince(c.id, p.startedAt - 60_000) > 0) {
            prefs.clearPending()
            PromptNotifier.cancel(context, p.contactId)
            return@withContext null
        }
        if (prefs.whatsappDetection && prefs.isWaCallActive(c.displayName)) return@withContext null
        c
    }

    fun hasPendingWhatsAppCall(): Boolean {
        val p = prefs.pending() ?: return false
        return p.channel == Channel.WHATSAPP && System.currentTimeMillis() - p.startedAt <= 2 * HOUR_MS
    }

    /** Posts the "Did you connect?" notification if one is due. Returns false when not due or notifications are off. */
    suspend fun notifyPromptIfDue(): Boolean {
        val c = pendingPrompt() ?: return false
        if (!PromptNotifier.canPost(context)) return false
        PromptNotifier.show(context, c, prefs.pending()?.startedAt ?: System.currentTimeMillis())
        return true
    }

    suspend fun answerPrompt(contactId: String, outcome: Outcome) = withContext(Dispatchers.IO) {
        PromptNotifier.cancel(context, contactId)
        val p = prefs.pending()
        if (p == null || p.contactId != contactId) return@withContext
        prefs.clearPending()
        interactions.insert(
            Interaction(newId(), contactId, p.startedAt, Channel.WHATSAPP, Direction.OUTGOING, outcome,
                Source.APP_PROMPT, null, "prompt:${p.startedAt}", false)
        )
    }

    // ---- Backup (FR-22) ------------------------------------------------------------------------

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val cs = JSONArray()
        contacts.all().forEach { c ->
            cs.put(
                JSONObject()
                    .put("id", c.id)
                    .putOpt("lookupKey", c.lookupKey)
                    .putOpt("systemContactId", c.systemContactId)
                    .put("displayName", c.displayName)
                    .put("phoneNumber", c.phoneNumber)
                    .put("preferredChannel", c.preferredChannel.name)
                    .put("frequencyDays", c.frequencyDays)
                    .putOpt("snoozedUntilEpochDay", c.snoozedUntilEpochDay)
                    .put("createdAt", c.createdAt)
                    .put("archived", c.archived)
            )
        }
        val ins = JSONArray()
        interactions.all().forEach { i ->
            ins.put(
                JSONObject()
                    .put("id", i.id)
                    .put("contactId", i.contactId)
                    .put("occurredAt", i.occurredAt)
                    .put("channel", i.channel.name)
                    .put("direction", i.direction.name)
                    .put("outcome", i.outcome.name)
                    .put("source", i.source.name)
                    .putOpt("durationSec", i.durationSec)
                    .putOpt("externalId", i.externalId)
                    .put("dismissed", i.dismissed)
            )
        }
        JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "settings",
                JSONObject()
                    .put("minCallSeconds", prefs.minCallSeconds)
                    .put("countryCode", prefs.countryCode)
                    .put("whatsappDetection", prefs.whatsappDetection),
            )
            .put("contacts", cs)
            .put("interactions", ins)
            .toString(2)
    }

    /** Replaces everything with the backup's contents. Returns the number of contacts imported. */
    suspend fun importJson(text: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        require(root.optString("format") == BACKUP_FORMAT) { "Not an InTouch backup" }
        val cArr = root.getJSONArray("contacts")
        val cs = (0 until cArr.length()).map { n ->
            val o = cArr.getJSONObject(n)
            TrackedContact(
                id = o.getString("id"),
                lookupKey = o.str("lookupKey"),
                systemContactId = o.lng("systemContactId"),
                displayName = o.getString("displayName"),
                photoUri = null,
                phoneNumber = o.getString("phoneNumber"),
                preferredChannel = Channel.valueOf(o.getString("preferredChannel")),
                frequencyDays = o.getInt("frequencyDays"),
                snoozedUntilEpochDay = o.lng("snoozedUntilEpochDay"),
                createdAt = o.getLong("createdAt"),
                archived = o.optBoolean("archived", false),
                missingFromContacts = false,
            )
        }
        val ids = cs.map { it.id }.toSet()
        val iArr = root.getJSONArray("interactions")
        val ins = (0 until iArr.length()).map { n ->
            val o = iArr.getJSONObject(n)
            Interaction(
                id = o.getString("id"),
                contactId = o.getString("contactId"),
                occurredAt = o.getLong("occurredAt"),
                channel = Channel.valueOf(o.getString("channel")),
                direction = Direction.valueOf(o.getString("direction")),
                outcome = Outcome.valueOf(o.getString("outcome")),
                source = Source.valueOf(o.getString("source")),
                durationSec = o.lng("durationSec"),
                externalId = o.str("externalId"),
                dismissed = o.optBoolean("dismissed", false),
            )
        }.filter { it.contactId in ids }
        db.withTransaction {
            interactions.deleteAll()
            contacts.deleteAll()
            contacts.insertAll(cs)
            interactions.insertAll(ins)
        }
        root.optJSONObject("settings")?.let { s ->
            prefs.minCallSeconds = s.optInt("minCallSeconds", prefs.minCallSeconds)
            prefs.countryCode = s.optInt("countryCode", prefs.countryCode)
            prefs.whatsappDetection = s.optBoolean("whatsappDetection", prefs.whatsappDetection)
        }
        prefs.lastCallLogSync = 0L
        prefs.lastSmsSync = 0L
        cs.size
    }

    private fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

    private fun JSONObject.lng(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null

    private companion object {
        const val BACKUP_FORMAT = "intouch-backup"
    }
}
