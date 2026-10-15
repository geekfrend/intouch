package com.geekfrend.intouch.lab

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/** The checklist shown in the app and included in the shared results. */
val CHECKS = listOf(
    "phone_app" to "Phone call starts from the app",
    "wa_app" to "WhatsApp button starts a voice call directly (not just the chat)",
    "unanswered" to "An unanswered outgoing call shows 0 s in the call history below",
    "wa_detect" to "A WhatsApp call shows up under WhatsApp notifications",
    "home_widget" to "Widget works on the home screen",
    "cover_widget" to "Widget appears on the cover screen",
    "cover_call" to "Phone call from the cover widget works with the phone closed",
    "cover_wa" to "WhatsApp call from the cover widget works with the phone closed",
    "cover_app" to "\"Open here\" on the cover widget opens this app on the cover screen",
)

/**
 * Builds a plain-text summary to share. Phone numbers are cut to their last 4 digits, and
 * names in WhatsApp notifications are replaced with [test contact] or [someone else].
 */
object Report {
    fun build(ctx: Context): String {
        val picked = Store.picked(ctx)
        val out = StringBuilder()
        out.appendLine("InTouch Lab results")
        out.appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        out.appendLine("App version: ${versionCode(ctx)}")
        out.appendLine()

        out.appendLine("Test contact")
        if (picked == null) {
            out.appendLine("  none picked")
        } else {
            out.appendLine("  number ${Device.mask(picked.number)}")
            out.appendLine("  WhatsApp voice entry: ${if (picked.waDataId != null) "yes" else "no"}")
            out.appendLine("  WhatsApp entries: ${picked.waMimes.joinToString { it.substringAfterLast('.') }.ifEmpty { "none" }}")
        }
        out.appendLine()

        out.appendLine("Checklist")
        CHECKS.forEach { (id, label) ->
            val answer = when (Store.check(ctx, id)) {
                "yes" -> "WORKED"
                "no" -> "DIDN'T WORK"
                else -> "not tried"
            }
            out.appendLine("  [$answer] $label")
        }
        out.appendLine()

        out.appendLine("Button results")
        listOf("Phone call", "WhatsApp voice call", "WhatsApp chat").forEach { key ->
            out.appendLine("  $key: ${Store.note(ctx, key).ifEmpty { "not pressed" }}")
        }
        out.appendLine()

        out.appendLine("Recent calls (newest first)")
        val calls = runCatching { Device.recentCalls(ctx, 15) }.getOrElse { emptyList() }
        if (calls.isEmpty()) out.appendLine("  none, or no permission")
        calls.forEach { r ->
            val who = if (picked != null && Device.sameNumber(r.number, picked.number)) "test contact" else Device.mask(r.number)
            out.appendLine("  ${Device.time(r.date)}  ${Device.typeLabel(r.type)}  ${r.durationSec} s  $who")
        }
        out.appendLine()

        out.appendLine("WhatsApp notifications (newest first)")
        val events = Store.events(ctx).take(25)
        if (events.isEmpty()) out.appendLine("  none recorded")
        events.forEach { e ->
            val title = e.optString("title")
            val who = when {
                title.isEmpty() -> "(no title)"
                picked != null && title == picked.name -> "[test contact]"
                else -> "[someone else]"
            }
            var text = e.optString("text")
            if (picked != null && picked.name.isNotEmpty()) text = text.replace(picked.name, "[test contact]")
            out.appendLine(
                "  ${Device.time(e.optLong("t"))}  ${e.optString("event")}  call=${e.optBoolean("call")}  " +
                    "ongoing=${e.optBoolean("ongoing")}  category=${e.optString("category")}  " +
                    "channel=${e.optString("channel")}  template=${e.optString("template")}  $who  \"$text\""
            )
        }
        return out.toString()
    }

    private fun versionCode(ctx: Context): String = runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        "${info.versionName} (build ${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrElse { "unknown" }
}
