package com.geekfrend.intouch.lab

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Records WhatsApp notifications so we can see what a call looks like on this phone.
 * Other apps are ignored. Message text is never stored; only call-like notifications keep their text.
 */
class WaListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        record(sbn, "posted")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        record(sbn, "removed")
    }

    private fun record(sbn: StatusBarNotification, event: String) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val template = (extras?.getString(Notification.EXTRA_TEMPLATE) ?: "").substringAfterLast('$')
        val channel = n.channelId ?: ""
        val category = n.category ?: ""
        val looksLikeCall = category == Notification.CATEGORY_CALL ||
            channel.contains("call", ignoreCase = true) ||
            channel.contains("voip", ignoreCase = true) ||
            template.contains("CallStyle")

        Store.addEvent(
            this,
            JSONObject()
                .put("t", System.currentTimeMillis())
                .put("event", event)
                .put("pkg", sbn.packageName)
                .put("category", category)
                .put("channel", channel)
                .put("template", template)
                .put("ongoing", sbn.isOngoing)
                .put("call", looksLikeCall)
                .put("title", title)
                .put("text", if (looksLikeCall) text else "[message hidden]"),
        )
    }
}
