package com.geekfrend.intouch.device

import android.content.Context
import android.provider.Telephony

/** One text message, without its content: only who, when and which direction. */
data class SmsRow(val id: Long, val address: String, val sent: Boolean, val date: Long)

/**
 * Reads the phone's SMS history. Needs the SMS permission. Only the address, date and type columns
 * are queried; message text is never read. Chat-style (RCS) messages may not be stored here.
 */
object SmsReader {
    fun since(context: Context, since: Long): List<SmsRow> {
        val rows = mutableListOf<SmsRow>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
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
