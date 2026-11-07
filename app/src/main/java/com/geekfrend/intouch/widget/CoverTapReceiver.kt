package com.geekfrend.intouch.widget

import android.Manifest
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.geekfrend.intouch.MainActivity
import com.geekfrend.intouch.app
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.device.CallLauncher
import kotlinx.coroutines.launch

/**
 * Taps on the cover-screen widget. Phone calls are placed directly with TelecomManager, so no screen of
 * InTouch's opens: Samsung's own call screen appears on the cover and the phone can stay folded.
 * WhatsApp, texts and opening InTouch need an app screen, so Samsung only shows them folded for apps
 * allowed in Settings › Advanced features › Labs › Apps allowed on cover screen.
 */
class CoverTapReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_REACH = "reachContactId"
        const val EXTRA_OPEN = "openContactId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext.app
        val reachId = intent.getStringExtra(EXTRA_REACH)
        val openId = intent.getStringExtra(EXTRA_OPEN)
        if (openId != null) {
            startOnCover(context, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_CONTACT_ID, openId))
            return
        }
        if (reachId == null) return
        val pending = goAsync()
        app.scope.launch {
            try {
                val overview = app.repository.currentOverview()
                val contact = (overview.overdue + overview.dueSoon + overview.onTrack).firstOrNull { it.contact.id == reachId }?.contact
                if (contact != null) reach(context.applicationContext, contact)
            } finally {
                pending.finish()
            }
        }
    }

    private fun reach(context: Context, contact: TrackedContact) {
        val whatsApp = contact.preferredChannel == Channel.WHATSAPP && CallLauncher.isWhatsAppInstalled(context)
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val phone = contact.preferredChannel == Channel.PHONE || contact.preferredChannel == Channel.OTHER ||
            (contact.preferredChannel == Channel.WHATSAPP && !whatsApp)
        if (phone && canCall) {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val placed = runCatching { telecom.placeCall(Uri.fromParts("tel", contact.phoneNumber, null), Bundle()) }.isSuccess
            if (placed) return
        }
        startOnCover(context, WidgetUpdater.reachIntent(context, contact))
    }

    private fun startOnCover(context: Context, intent: Intent) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(WidgetUpdater.COVER_DISPLAY_ID).toBundle()
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options) }
    }
}
