package com.geekfrend.intouch.ui

import android.content.res.Resources
import com.geekfrend.intouch.R
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.Direction
import com.geekfrend.intouch.data.Interaction
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.Source
import com.geekfrend.intouch.domain.ContactStatus
import com.geekfrend.intouch.domain.Summary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * Every piece of user-facing text built from data. The words themselves live in res/values/strings.xml,
 * so wording can change without touching code. Shared by the app screens and the widgets.
 */
object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun due(res: Resources, s: ContactStatus): String {
        s.snoozedUntil?.let { return res.getString(R.string.due_snoozed_until, date(it)) }
        val d = s.daysUntilDue.toInt()
        return when {
            d < 0 -> res.getQuantityString(R.plurals.days_overdue, -d, -d)
            d == 0 -> res.getString(R.string.due_today)
            d == 1 -> res.getString(R.string.due_tomorrow)
            else -> res.getQuantityString(R.plurals.due_in_days, d, d)
        }
    }

    fun nextDue(res: Resources, s: ContactStatus): String = res.getString(R.string.next_due, date(s.nextDue))

    fun lastCall(res: Resources, s: ContactStatus): String {
        val at = s.lastConnectedAt ?: return res.getString(R.string.last_call_none)
        return res.getString(R.string.last_call_format, ago(res, at), channel(res, s.lastConnectedChannel))
    }

    /** "Talked 2 days ago" for on-track tiles. */
    fun lastCallShort(res: Resources, s: ContactStatus): String {
        val at = s.lastConnectedAt ?: return res.getString(R.string.last_call_none)
        return res.getString(R.string.last_call_short, ago(res, at))
    }

    /** "SEP 28"-style date for the log (the label style uppercases it). */
    fun shortDate(t: Long): String =
        Instant.ofEpochMilli(t).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d"))

    /** "WhatsApp · incoming", "Text · sent", "Phone · no answer", "Skipped this time". */
    fun logLine(res: Resources, i: Interaction): String {
        if (i.outcome == Outcome.SKIPPED) return res.getString(R.string.entry_skipped)
        val what = when {
            i.outcome != Outcome.CONNECTED -> outcome(res, i.outcome).lowercase()
            i.channel == Channel.TEXT && i.direction == Direction.OUTGOING -> res.getString(R.string.log_sent)
            i.channel == Channel.TEXT && i.direction == Direction.INCOMING -> res.getString(R.string.log_received)
            i.direction == Direction.UNKNOWN -> null
            else -> direction(res, i.direction).lowercase()
        }
        return listOfNotNull(channel(res, i.channel), what).joinToString(" · ")
    }

    fun ago(res: Resources, time: Long): String {
        val days = ChronoUnit.DAYS.between(localDate(time), LocalDate.now()).toInt()
        return when {
            days <= 0 -> res.getString(R.string.ago_today)
            days == 1 -> res.getString(R.string.ago_yesterday)
            days < 60 -> res.getQuantityString(R.plurals.ago_days, days, days)
            else -> {
                val months = days / 30
                res.getQuantityString(R.plurals.ago_months, months, months)
            }
        }
    }

    fun channel(res: Resources, c: Channel?): String = res.getString(
        when (c) {
            Channel.PHONE -> R.string.channel_phone
            Channel.WHATSAPP -> R.string.channel_whatsapp
            Channel.TEXT -> R.string.channel_text
            Channel.OTHER, null -> R.string.channel_other
        }
    )

    /** Label on a call or text button. */
    fun callLabel(res: Resources, c: Channel): String = res.getString(
        when (c) {
            Channel.WHATSAPP -> R.string.call_whatsapp
            Channel.TEXT -> R.string.call_text
            else -> R.string.call_phone
        }
    )

    fun direction(res: Resources, d: Direction): String = res.getString(
        when (d) {
            Direction.OUTGOING -> R.string.direction_outgoing
            Direction.INCOMING -> R.string.direction_incoming
            Direction.UNKNOWN -> R.string.direction_unknown
        }
    )

    fun outcome(res: Resources, o: Outcome): String = res.getString(
        when (o) {
            Outcome.CONNECTED -> R.string.outcome_connected
            Outcome.NO_ANSWER -> R.string.outcome_no_answer
            Outcome.LEFT_MESSAGE -> R.string.outcome_left_message
            Outcome.SKIPPED -> R.string.outcome_skipped
        }
    )

    fun source(res: Resources, s: Source): String = res.getString(
        when (s) {
            Source.CALL_HISTORY -> R.string.source_call_history
            Source.WHATSAPP_NOTIFICATION -> R.string.source_whatsapp
            Source.APP_PROMPT -> R.string.source_prompt
            Source.MANUAL -> R.string.source_manual
            Source.SMS_HISTORY -> R.string.source_sms
        }
    )

    fun frequency(res: Resources, days: Int): String = when (days) {
        7 -> res.getString(R.string.freq_weekly)
        14 -> res.getString(R.string.freq_two_weeks)
        30 -> res.getString(R.string.freq_monthly)
        90 -> res.getString(R.string.freq_quarterly)
        else -> res.getQuantityString(R.plurals.freq_every_days, days, days)
    }

    /** Short label for a frequency tile: "Weekly", "2 weeks", "3 days". */
    fun frequencyShort(res: Resources, days: Int): String = when (days) {
        7 -> res.getString(R.string.freq_weekly)
        14 -> res.getString(R.string.freq_two_weeks_short)
        30 -> res.getString(R.string.freq_monthly)
        90 -> res.getString(R.string.freq_quarterly)
        else -> res.getQuantityString(R.plurals.unit_days_count, days, days)
    }

    fun date(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun time(t: Long): String =
        Instant.ofEpochMilli(t).atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    fun dateTime(t: Long): String =
        Instant.ofEpochMilli(t).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

    fun duration(res: Resources, sec: Long): String =
        if (sec < 60) res.getString(R.string.duration_seconds, sec) else res.getString(R.string.duration_minutes, (sec + 30) / 60)

    fun summary(res: Resources, s: Summary): String = res.getString(
        R.string.summary_format,
        s.onTrack,
        s.overdue,
        res.getQuantityString(R.plurals.calls_this_month, s.callsThisMonth, s.callsThisMonth),
    )

    fun entryTitle(res: Resources, i: Interaction): String =
        if (i.outcome == Outcome.SKIPPED) {
            res.getString(R.string.entry_skipped)
        } else {
            res.getString(R.string.entry_title_format, direction(res, i.direction), channel(res, i.channel))
        }

    fun entrySubtitle(res: Resources, i: Interaction): String {
        val parts = mutableListOf(dateTime(i.occurredAt))
        i.durationSec?.takeIf { it > 0 }?.let { parts.add(duration(res, it)) }
        if (i.outcome != Outcome.CONNECTED && i.outcome != Outcome.SKIPPED) parts.add(outcome(res, i.outcome))
        if (i.outcome != Outcome.SKIPPED) parts.add(source(res, i.source))
        if (i.dismissed) parts.add(res.getString(R.string.entry_not_real))
        return parts.joinToString(" · ")
    }

    fun lastWhatsApp(res: Resources, time: Long, name: String): String =
        if (name.isEmpty()) {
            res.getString(R.string.settings_wa_last_untracked, dateTime(time))
        } else {
            res.getString(R.string.settings_wa_last_tracked, dateTime(time), name)
        }
}
