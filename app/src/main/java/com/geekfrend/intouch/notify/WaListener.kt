package com.geekfrend.intouch.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.geekfrend.intouch.app
import com.geekfrend.intouch.domain.EndedWaCall
import com.geekfrend.intouch.domain.WaEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FR-11: watches WhatsApp's call notifications only. Messages and other apps are ignored, and no
 * notification text is stored. Finished calls go to the repository, which keeps only tracked contacts.
 */
class WaListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, posted = true)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = handle(sbn, posted = false)

    private fun handle(sbn: StatusBarNotification, posted: Boolean) {
        if (sbn.packageName !in WA_PACKAGES) return
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_CALL) return
        val app = applicationContext.app
        if (!app.prefs.whatsappDetection) return

        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val now = System.currentTimeMillis()

        val ended: EndedWaCall? = synchronized(LOCK) {
            val tracker = app.prefs.loadWaTracker()
            val result = if (posted) {
                tracker.onPosted(WaEvent(sbn.key, title, text, n.category, now))
                null
            } else {
                tracker.onRemoved(sbn.key, now)
            }
            app.prefs.saveWaTracker(tracker)
            result
        }
        if (!posted) {
            app.scope.launch {
                if (ended != null) app.repository.recordWhatsAppCall(ended)
                // A WhatsApp call screen just went away. If it was a call started from InTouch that
                // detection couldn't confirm (e.g. it never connected), ask now in a notification.
                delay(1_500)
                app.repository.notifyPromptIfDue()
            }
        }
    }

    companion object {
        private val LOCK = Any()
        val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
