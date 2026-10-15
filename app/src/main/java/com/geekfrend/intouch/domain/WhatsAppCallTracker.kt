package com.geekfrend.intouch.domain

import kotlin.math.max

/** One WhatsApp notification, reduced to what call detection needs. Message text is never passed in. */
data class WaEvent(
    val key: String,
    val title: String,
    val text: String,
    val category: String?,
    val time: Long,
)

data class ActiveWaCall(val title: String, val start: Long, val outgoing: Boolean)

data class EndedWaCall(val title: String, val start: Long, val durationSec: Long, val outgoing: Boolean)

/**
 * Turns WhatsApp call notifications into calls, using the pattern seen on the Galaxy Z Flip 6 test:
 * an outgoing call shows "Calling…" then "Ringing…" then "Ongoing voice call", all with category
 * "call", and the notification is removed when the call ends. "On hold" can appear mid-call.
 *
 * A call is connected from the first "Ongoing"/"On hold" notification until that notification is
 * removed. It is outgoing when "Calling…" for the same person came shortly before.
 */
class WhatsAppCallTracker(
    val active: MutableMap<String, ActiveWaCall> = mutableMapOf(),
    /** Person's name -> when "Calling…" was last seen. */
    val callingSeen: MutableMap<String, Long> = mutableMapOf(),
) {
    fun onPosted(e: WaEvent) {
        if (e.category != CATEGORY_CALL) return
        prune(e.time)
        val text = e.text.lowercase()
        when {
            text.startsWith("calling") -> callingSeen[e.title] = e.time
            text.contains("ongoing") || text.contains("on hold") -> {
                if (!active.containsKey(e.key)) {
                    val seen = callingSeen[e.title]
                    val outgoing = seen != null && e.time - seen <= OUTGOING_WINDOW_MS
                    active[e.key] = ActiveWaCall(e.title, e.time, outgoing)
                }
            }
        }
    }

    /** Returns the finished call when a connected call's notification goes away. */
    fun onRemoved(key: String, time: Long): EndedWaCall? {
        val call = active.remove(key) ?: return null
        callingSeen.remove(call.title)
        return EndedWaCall(call.title, call.start, max(0L, (time - call.start) / 1000), call.outgoing)
    }

    fun isActiveFor(title: String): Boolean = active.values.any { it.title.trim().equals(title.trim(), ignoreCase = true) }

    private fun prune(now: Long) {
        active.entries.removeAll { now - it.value.start > STALE_CALL_MS }
        callingSeen.entries.removeAll { now - it.value > OUTGOING_WINDOW_MS }
    }

    companion object {
        const val CATEGORY_CALL = "call"
        const val OUTGOING_WINDOW_MS = 2 * 60_000L
        const val STALE_CALL_MS = 6 * HOUR_MS
    }
}
