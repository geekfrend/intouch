package com.geekfrend.intouch.device

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data

/** A contact chosen in the system contact picker. */
data class PickedContact(
    val contactId: Long,
    val lookupKey: String?,
    val name: String,
    val photoUri: String?,
    val numbers: List<String>,
    val hasWhatsApp: Boolean,
)

/** Reads the phone's address book. Every call needs the contacts permission. */
object ContactsReader {
    const val WA_VOICE_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

    private val CONTACT_COLUMNS = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME, Contacts.PHOTO_THUMBNAIL_URI)

    data class Resolved(val contactId: Long, val lookupKey: String?, val name: String, val photoUri: String?)

    fun readPicked(context: Context, uri: Uri): PickedContact? {
        var resolved: Resolved? = null
        context.contentResolver.query(uri, CONTACT_COLUMNS, null, null, null)?.use { c ->
            if (c.moveToFirst()) resolved = Resolved(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getString(3))
        }
        val r = resolved ?: return null
        return PickedContact(
            contactId = r.contactId,
            lookupKey = r.lookupKey,
            name = r.name,
            photoUri = r.photoUri,
            numbers = numbers(context, r.contactId),
            hasWhatsApp = whatsAppVoiceDataId(context, r.contactId, null) != null,
        )
    }

    fun numbers(context: Context, contactId: Long): List<String> {
        val out = mutableListOf<String>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.NUMBER),
                "${Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null,
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out.add(it) } }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out.distinctBy { n -> n.filter { it.isDigit() }.takeLast(10) }
    }

    /** Finds the contact again by lookup key (or ID). Null when it was deleted from the address book. */
    fun resolve(context: Context, lookupKey: String?, contactId: Long?): Resolved? {
        val cr = context.contentResolver
        return try {
            val contactUri: Uri = when {
                lookupKey != null -> {
                    val lookupUri = if (contactId != null) {
                        Contacts.getLookupUri(contactId, lookupKey)
                    } else {
                        Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey)
                    }
                    Contacts.lookupContact(cr, lookupUri) ?: return null
                }
                contactId != null -> ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
                else -> return null
            }
            var found: Resolved? = null
            cr.query(contactUri, CONTACT_COLUMNS, null, null, null)?.use { c ->
                if (c.moveToFirst()) found = Resolved(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getString(3))
            }
            found
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The contact's WhatsApp "voice call" entry, preferring the one for [number]. */
    fun whatsAppVoiceDataId(context: Context, contactId: Long, number: String?): Long? {
        val tail = number?.filter { it.isDigit() }?.takeLast(10)
        var first: Long? = null
        var match: Long? = null
        try {
            context.contentResolver.query(
                Data.CONTENT_URI,
                arrayOf(Data._ID, Data.DATA1),
                "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), WA_VOICE_MIME),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val dataId = c.getLong(0)
                    val digits = (c.getString(1) ?: "").filter { it.isDigit() }
                    if (first == null) first = dataId
                    if (tail != null && tail.length >= 7 && digits.contains(tail)) {
                        match = dataId
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            return null
        }
        return match ?: first
    }
}
