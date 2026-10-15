package com.geekfrend.intouch.lab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads contacts and call history, and builds the intents that start calls. */
object Device {
    const val WA_PKG = "com.whatsapp"
    const val WA_VOICE_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

    data class CallRow(
        val name: String?,
        val number: String,
        val type: Int,
        val date: Long,
        val durationSec: Long,
    )

    /** Reads a picked contact: name, first phone number, and any WhatsApp entries on it. */
    fun readContact(ctx: Context, contactUri: Uri): Store.Picked? {
        val cr = ctx.contentResolver
        var contactId: String? = null
        var name = ""
        cr.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                contactId = c.getString(0)
                name = c.getString(1) ?: ""
            }
        }
        val id = contactId ?: return null

        var number: String? = null
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(id),
            null,
        )?.use { c -> if (c.moveToFirst()) number = c.getString(0) }

        val mimes = mutableListOf<String>()
        var waDataId: Long? = null
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE} LIKE ?",
            arrayOf(id, "%whatsapp%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val dataId = c.getLong(0)
                val mime = c.getString(1) ?: ""
                if (mime.isNotEmpty()) mimes.add(mime)
                if (mime == WA_VOICE_MIME && waDataId == null) waDataId = dataId
            }
        }

        val phone = number ?: return null
        return Store.Picked(
            name = name,
            number = phone,
            waDataId = waDataId,
            waMime = if (waDataId != null) WA_VOICE_MIME else null,
            waMimes = mimes.distinct(),
        )
    }

    fun recentCalls(ctx: Context, limit: Int): List<CallRow> {
        val rows = mutableListOf<CallRow>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && rows.size < limit) {
                rows.add(
                    CallRow(
                        name = c.getString(0),
                        number = c.getString(1) ?: "",
                        type = c.getInt(2),
                        date = c.getLong(3),
                        durationSec = c.getLong(4),
                    )
                )
            }
        }
        return rows
    }

    fun typeLabel(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        CallLog.Calls.MISSED_TYPE -> "Missed"
        CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
        CallLog.Calls.REJECTED_TYPE -> "Rejected"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "Answered elsewhere"
        else -> "Type $type"
    }

    /** Digits for wa.me, assuming +1 when a 10-digit number has no country code. */
    fun waDigits(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (!number.trim().startsWith("+") && digits.length == 10) "1$digits" else digits
    }

    /** True when two numbers are the same line (compares the last 10 digits). */
    fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }.takeLast(10)
        val y = b.filter { it.isDigit() }.takeLast(10)
        return x.isNotEmpty() && x == y
    }

    fun mask(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 4) "…" + digits.takeLast(4) else digits
    }

    fun time(millis: Long): String =
        SimpleDateFormat("d MMM HH:mm:ss", Locale.US).format(Date(millis))

    fun phoneCallIntent(number: String): Intent =
        Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))

    fun dialIntent(number: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))

    /** Starts a WhatsApp voice call through WhatsApp's entry in the contact (undocumented). */
    fun waVoiceIntent(picked: Store.Picked): Intent? {
        val dataId = picked.waDataId ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, dataId.toString()),
                picked.waMime ?: WA_VOICE_MIME,
            )
            .setPackage(WA_PKG)
    }

    fun waChatIntent(number: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waDigits(number)))
            .setPackage(WA_PKG)
}
