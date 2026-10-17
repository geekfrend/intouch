package com.geekfrend.intouch.data

import android.content.Context
import com.geekfrend.intouch.domain.ActiveWaCall
import com.geekfrend.intouch.domain.PhoneNumbers
import com.geekfrend.intouch.domain.WhatsAppCallTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** A WhatsApp call started from InTouch, waiting for "Did you connect?" if detection misses it. */
data class PendingCall(val contactId: String, val channel: Channel, val startedAt: Long)

/** Settings and small bits of state, kept in SharedPreferences. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("intouch", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)

    /** Bumps whenever a user-visible setting changes, so screens can re-read. */
    val version: StateFlow<Int> = _version

    private fun changed() {
        _version.value = _version.value + 1
    }

    /** The app's look: "DRIFTER" (default) or "MONUMENT". */
    var theme: String
        get() = p.getString("theme", "DRIFTER") ?: "DRIFTER"
        set(v) { p.edit().putString("theme", v).apply(); changed() }

    var setupDone: Boolean
        get() = p.getBoolean("setupDone", false)
        set(v) { p.edit().putBoolean("setupDone", v).apply(); changed() }

    /** On by default (decided Oct 15, after the Flip 6 test). */
    var whatsappDetection: Boolean
        get() = p.getBoolean("whatsappDetection", true)
        set(v) { p.edit().putBoolean("whatsappDetection", v).apply(); changed() }

    var minCallSeconds: Int
        get() = p.getInt("minCallSeconds", 30)
        set(v) { p.edit().putInt("minCallSeconds", v).apply(); changed() }

    /** Country calling code used for numbers saved without one. */
    var countryCode: Int
        get() = p.getInt("countryCode", 1)
        set(v) { p.edit().putInt("countryCode", v).apply(); changed() }

    val region: String
        get() = PhoneNumbers.regionFor(countryCode)

    /** Whether InTouch has asked once for notification permission (for people who finished setup earlier). */
    var askedNotifications: Boolean
        get() = p.getBoolean("askedNotifications", false)
        set(v) { p.edit().putBoolean("askedNotifications", v).apply() }

    var askedSms: Boolean
        get() = p.getBoolean("askedSms", false)
        set(v) { p.edit().putBoolean("askedSms", v).apply() }

    var lastCallLogSync: Long
        get() = p.getLong("lastCallLogSync", 0L)
        set(v) { p.edit().putLong("lastCallLogSync", v).apply() }

    var lastSmsSync: Long
        get() = p.getLong("lastSmsSync", 0L)
        set(v) { p.edit().putLong("lastSmsSync", v).apply() }

    var lastWhatsAppDetectedAt: Long
        get() = p.getLong("lastWaAt", 0L)
        set(v) { p.edit().putLong("lastWaAt", v).apply(); changed() }

    /** Name of the tracked contact on the last detected WhatsApp call, or "" if it wasn't someone you track. */
    var lastWhatsAppDetectedName: String
        get() = p.getString("lastWaName", "") ?: ""
        set(v) { p.edit().putString("lastWaName", v).apply(); changed() }

    fun savePending(call: PendingCall) {
        p.edit()
            .putString("pendingContact", call.contactId)
            .putString("pendingChannel", call.channel.name)
            .putLong("pendingAt", call.startedAt)
            .apply()
    }

    fun pending(): PendingCall? {
        val id = p.getString("pendingContact", null) ?: return null
        val channel = runCatching { Channel.valueOf(p.getString("pendingChannel", "") ?: "") }.getOrNull() ?: return null
        return PendingCall(id, channel, p.getLong("pendingAt", 0L))
    }

    fun clearPending() {
        p.edit().remove("pendingContact").remove("pendingChannel").remove("pendingAt").apply()
    }

    @Synchronized
    fun loadWaTracker(): WhatsAppCallTracker {
        val root = runCatching { JSONObject(p.getString("waTracker", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val active = mutableMapOf<String, ActiveWaCall>()
        root.optJSONObject("active")?.let { a ->
            a.keys().forEach { k ->
                val v = a.getJSONObject(k)
                active[k] = ActiveWaCall(v.getString("title"), v.getLong("start"), v.getBoolean("outgoing"))
            }
        }
        val calling = mutableMapOf<String, Long>()
        root.optJSONObject("calling")?.let { c -> c.keys().forEach { k -> calling[k] = c.getLong(k) } }
        return WhatsAppCallTracker(active, calling)
    }

    @Synchronized
    fun saveWaTracker(tracker: WhatsAppCallTracker) {
        val active = JSONObject()
        tracker.active.forEach { (k, v) ->
            active.put(k, JSONObject().put("title", v.title).put("start", v.start).put("outgoing", v.outgoing))
        }
        val calling = JSONObject()
        tracker.callingSeen.forEach { (k, v) -> calling.put(k, v) }
        p.edit().putString("waTracker", JSONObject().put("active", active).put("calling", calling).toString()).apply()
    }

    fun isWaCallActive(name: String): Boolean = loadWaTracker().isActiveFor(name)
}
