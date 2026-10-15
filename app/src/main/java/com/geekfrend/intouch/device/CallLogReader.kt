package com.geekfrend.intouch.device

import android.content.Context
import android.provider.CallLog

data class CallRow(val id: Long, val number: String, val type: Int, val date: Long, val durationSec: Long)

/** Reads the phone's call history. Needs the call-log permission. */
object CallLogReader {
    fun since(context: Context, since: Long): List<CallRow> {
        val rows = mutableListOf<CallRow>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                rows.add(CallRow(c.getLong(0), c.getString(1) ?: "", c.getInt(2), c.getLong(3), c.getLong(4)))
            }
        }
        return rows
    }
}
