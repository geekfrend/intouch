package com.geekfrend.intouch.domain

import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.TrackedContact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

const val DAY_MS = 86_400_000L
const val HOUR_MS = 3_600_000L

enum class DueState { OVERDUE, DUE_SOON, ON_TRACK }

data class ContactStatus(
    val state: DueState,
    val nextDue: LocalDate,
    /** Negative when overdue. */
    val daysUntilDue: Long,
    val lastConnectedAt: Long?,
    val lastConnectedChannel: Channel?,
    /** Set only while a snooze is active. */
    val snoozedUntil: LocalDate?,
)

data class ContactStats(val callsLast90Days: Int, val averageGapDays: Double?)

data class Summary(val onTrack: Int, val overdue: Int, val callsThisMonth: Int)

/** The spec's "Derived status" rules. Pure Kotlin, so it is unit-tested on its own. */
object StatusCalculator {

    fun resetsTimer(i: Interaction): Boolean =
        !i.dismissed && (i.outcome == Outcome.CONNECTED || i.outcome == Outcome.SKIPPED)

    fun isRealCall(i: Interaction): Boolean = !i.dismissed && i.outcome == Outcome.CONNECTED

    /** Due-soon window: 20% of the frequency, at least 1 day. */
    fun dueSoonWindow(frequencyDays: Int): Int = max(1, (frequencyDays * 0.2).roundToInt())

    fun status(
        contact: TrackedContact,
        interactions: List<Interaction>,
        today: LocalDate,
        zone: ZoneId,
    ): ContactStatus {
        val mine = interactions.filter { it.contactId == contact.id }
        val lastReset = mine.filter(::resetsTimer).maxOfOrNull { it.occurredAt } ?: contact.createdAt
        val lastCall = mine.filter(::isRealCall).maxByOrNull { it.occurredAt }
        val nextDue = Instant.ofEpochMilli(lastReset).atZone(zone).toLocalDate()
            .plusDays(contact.frequencyDays.toLong())
        val days = ChronoUnit.DAYS.between(today, nextDue)
        val snoozed = contact.snoozedUntilEpochDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { it.isAfter(today) }
        val state = when {
            snoozed != null -> DueState.ON_TRACK
            days < 0 -> DueState.OVERDUE
            days <= dueSoonWindow(contact.frequencyDays) -> DueState.DUE_SOON
            else -> DueState.ON_TRACK
        }
        return ContactStatus(
            state = state,
            nextDue = nextDue,
            daysUntilDue = days,
            lastConnectedAt = lastCall?.occurredAt,
            lastConnectedChannel = lastCall?.channel,
            snoozedUntil = snoozed,
        )
    }

    /** Calls in the last 90 days, and the average gap across the last 10 calls. Missed due dates aren't counted. */
    fun stats(interactions: List<Interaction>, now: Long): ContactStats {
        val calls = interactions.filter(::isRealCall).sortedBy { it.occurredAt }
        val recent = calls.count { it.occurredAt >= now - 90 * DAY_MS }
        val last = calls.takeLast(10)
        val gap = if (last.size >= 2) {
            (last.last().occurredAt - last.first().occurredAt).toDouble() / DAY_MS / (last.size - 1)
        } else {
            null
        }
        return ContactStats(recent, gap)
    }

    fun summary(
        statuses: List<ContactStatus>,
        interactions: List<Interaction>,
        today: LocalDate,
        zone: ZoneId,
    ): Summary {
        val monthStart = today.withDayOfMonth(1)
        val callsThisMonth = interactions.count {
            isRealCall(it) && !Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().isBefore(monthStart)
        }
        val overdue = statuses.count { it.state == DueState.OVERDUE }
        return Summary(onTrack = statuses.size - overdue, overdue = overdue, callsThisMonth = callsThisMonth)
    }
}
