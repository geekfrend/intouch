package com.geekfrend.intouch.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.domain.PhoneNumbers

/** Builds the intents that start phone and WhatsApp calls. */
object CallLauncher {
    const val WA_PKG = "com.whatsapp"

    fun isWhatsAppInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(WA_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Places the call directly when allowed, otherwise opens the dialer with the number. */
    fun phoneIntent(context: Context, number: String): Intent {
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        return Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    }

    /** Starts a WhatsApp voice call through WhatsApp's entry in the contact (tested on the Flip 6). */
    fun whatsAppVoiceIntent(context: Context, contact: TrackedContact): Intent? {
        val contactId = contact.systemContactId ?: return null
        val dataId = ContactsReader.whatsAppVoiceDataId(context, contactId, contact.phoneNumber) ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, dataId.toString()),
                ContactsReader.WA_VOICE_MIME,
            )
            .setPackage(WA_PKG)
    }

    fun whatsAppChatIntent(number: String, region: String): Intent {
        val digits = PhoneNumbers.normalize(number, region).filter { it.isDigit() }
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage(WA_PKG)
    }

    /** Voice call when possible, the chat otherwise. Null when WhatsApp isn't installed. */
    fun whatsAppIntent(context: Context, contact: TrackedContact, region: String): Intent? {
        if (!isWhatsAppInstalled(context)) return null
        return whatsAppVoiceIntent(context, contact) ?: whatsAppChatIntent(contact.phoneNumber, region)
    }
}
