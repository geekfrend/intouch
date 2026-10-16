package com.geekfrend.intouch.device

import android.content.Context
import android.provider.Telephony

/** One text message, without its content: only who, when and which direction. */
data class SmsRow(val id: Long, val address: String, val sent: Boolean, val date: Long)

/**
 * Reads the phone's SMS history for the given numbers only. The query itself is filtered by number,
 * so Android never returns texts with anyone else. Only the address, date and type columns are
 * requested; message text is never read. Chat-style (RCS) messages may not be stored here.
 */
object SmsReader {
    /** Numbers are matched on their last 7 digits in the query, then checked exactly by the caller. */
    private const val TAIL_DIGITS = 7

    fun since(context: Context, since: Long, numbers: List<String>): List<SmsRow> {
        val tails = numbers
            .map { n -> n.filter { it.isDigit() }.takeLast(TAIL_DIGITS) }
            .filter { it.length == TAIL_DIGITS }
            .distinct()
        if (tails.isEmpty()) return emptyList()

        val numberClause = tails.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} LIKE ?" }
        val selection = "${Telephony.Sms.DATE} > ? AND ($numberClause)"
        val args = arrayOf(since.toString()) + tails.map { "%$it" }

        val rows = mutableListOf<SmsRow>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.DATE),
            selection,
            args,
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(2)
                if (type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_SENT) continue
                rows.add(SmsRow(c.getLong(0), c.getString(1) ?: "", type == Telephony.Sms.MESSAGE_TYPE_SENT, c.getLong(3)))
            }
        }
        return rows
    }
}
