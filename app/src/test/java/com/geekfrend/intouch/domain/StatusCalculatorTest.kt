package com.geekfrend.intouch.domain

import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.Direction
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.Source
import com.geekfrend.intouch.data.TrackedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatusCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 10, 1)
    private fun ms(d: LocalDate, hour: Int = 12) = d.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun contact(frequency: Int = 7, snoozedUntil: LocalDate? = null) = TrackedContact(
        id = "c", lookupKey = null, systemContactId = null, displayName = "Sam", photoUri = null,
        phoneNumber = "+15551234567", preferredChannel = Channel.PHONE, frequencyDays = frequency,
        snoozedUntilEpochDay = snoozedUntil?.toEpochDay(), createdAt = ms(day0), archived = false,
        missingFromContacts = false,
    )

    private fun call(d: LocalDate, outcome: Outcome = Outcome.CONNECTED, dismissed: Boolean = false) = Interaction(
        id = d.toString() + outcome, contactId = "c", occurredAt = ms(d), channel = Channel.WHATSAPP,
        direction = Direction.OUTGOING, outcome = outcome, source = Source.MANUAL, durationSec = 60,
        externalId = null, dismissed = dismissed,
    )

    private fun state(today: LocalDate, vararg calls: Interaction, frequency: Int = 7, snoozedUntil: LocalDate? = null) =
        StatusCalculator.status(contact(frequency, snoozedUntil), calls.toList(), today, zone)

    @Test fun newContactCountsFromTheDayItWasAdded() {
        assertEquals(DueState.ON_TRACK, state(day0.plusDays(3)).state)
        assertEquals(DueState.DUE_SOON, state(day0.plusDays(6)).state)
        assertEquals(DueState.DUE_SOON, state(day0.plusDays(7)).state) // due today
        assertEquals(DueState.OVERDUE, state(day0.plusDays(8)).state)
        assertEquals(-1L, state(day0.plusDays(8)).daysUntilDue)
    }

    @Test fun connectedCallResetsTheTimer() {
        val s = state(day0.plusDays(10), call(day0.plusDays(9)))
        assertEquals(DueState.ON_TRACK, s.state)
        assertEquals(day0.plusDays(16), s.nextDue)
        assertEquals(ms(day0.plusDays(9)), s.lastConnectedAt)
    }

    @Test fun skipResetsButIsNotACall() {
        val s = state(day0.plusDays(10), call(day0.plusDays(9), Outcome.SKIPPED))
        assertEquals(DueState.ON_TRACK, s.state)
        assertNull(s.lastConnectedAt)
    }

    @Test fun noAnswerAndDismissedCallsDontReset() {
        val s = state(day0.plusDays(10), call(day0.plusDays(9), Outcome.NO_ANSWER), call(day0.plusDays(9), dismissed = true))
        assertEquals(DueState.OVERDUE, s.state)
    }

    @Test fun snoozeKeepsAContactOnTrackUntilItEnds() {
        val until = day0.plusDays(12)
        assertEquals(DueState.ON_TRACK, state(day0.plusDays(10), snoozedUntil = until).state)
        assertEquals(DueState.OVERDUE, state(day0.plusDays(12), snoozedUntil = until).state)
    }

    @Test fun dueSoonWindowIsTwentyPercentAndAtLeastOneDay() {
        assertEquals(1, StatusCalculator.dueSoonWindow(3))
        assertEquals(1, StatusCalculator.dueSoonWindow(7))
        assertEquals(3, StatusCalculator.dueSoonWindow(14))
        assertEquals(6, StatusCalculator.dueSoonWindow(30))
        assertEquals(18, StatusCalculator.dueSoonWindow(90))
    }

    @Test fun statsCountRecentCallsAndAverageGap() {
        val now = ms(day0.plusDays(30))
        val calls = listOf(call(day0), call(day0.plusDays(10)), call(day0.plusDays(20)), call(day0.plusDays(25), Outcome.NO_ANSWER))
        val stats = StatusCalculator.stats(calls, now)
        assertEquals(3, stats.callsLast90Days)
        assertEquals(10.0, stats.averageGapDays!!, 0.01)
    }
}
