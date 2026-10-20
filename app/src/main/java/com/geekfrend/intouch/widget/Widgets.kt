package com.geekfrend.intouch.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.geekfrend.intouch.MainActivity
import com.geekfrend.intouch.R
import com.geekfrend.intouch.app
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.device.CallLauncher
import com.geekfrend.intouch.work.SyncWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Widget colors and shapes for each app theme (Settings › Theme). Widgets can't read the Compose theme. */
internal data class WidgetLook(
    val light: Boolean,
    val bg: Int, val coverBg: Int, val card: Int, val cardFading: Int, val button: Int, val buttonOutline: Int,
    val text: Int, val muted: Int, val signal: Int, val onSignal: Int, val fading: Int, val soon: Int, val signalOff: Int,
) {
    companion object {
        private val DRIFTER = WidgetLook(
            false, R.drawable.widget_bg, R.drawable.widget_cover_bg, R.drawable.widget_card, R.drawable.widget_card_fading,
            R.drawable.widget_button, R.drawable.widget_button_outline,
            R.color.widget_text, R.color.widget_text_secondary, R.color.widget_button_bg, R.color.widget_button_text,
            R.color.widget_overdue, R.color.widget_due_soon, R.color.widget_signal_off,
        )
        private val MONUMENT = WidgetLook(
            true, R.drawable.mwidget_bg, R.drawable.mwidget_cover_bg, R.drawable.mwidget_card, R.drawable.mwidget_card_fading,
            R.drawable.mwidget_button, R.drawable.mwidget_button_outline,
            R.color.mwidget_text, R.color.mwidget_muted, R.color.mwidget_signal, R.color.mwidget_on_signal,
            R.color.mwidget_fading, R.color.mwidget_soon, R.color.mwidget_border,
        )
        fun of(monument: Boolean) = if (monument) MONUMENT else DRIFTER
    }
}

/** FR-19: home-screen widget. */
class HomeWidget : DueWidget()

/** FR-20: the same widget registered for the Galaxy Z Flip cover screen. Taps open the app there. */
class CoverWidget : DueWidget()

abstract class DueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
        SyncWork.runNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context)
    }

    private fun refresh(context: Context) {
        val appContext = context.applicationContext
        val pending = goAsync()
        appContext.app.scope.launch {
            try {
                WidgetUpdater.refresh(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Draws both widgets. Each is a header plus a scrolling list (filled by [WidgetListService]) with
 * Fading, Soon and Strong sections that expand and collapse when you tap their headers.
 * Colors come from res/values/colors.xml; layouts are widget_due.xml (home) and widget_cover_due.xml (cover).
 */
object WidgetUpdater {
    /** Display ID of the Flip's cover screen, per Samsung's Flex Window guide (tested Oct 15). */
    const val COVER_DISPLAY_ID = 1
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    suspend fun refresh(context: Context) {
        refresh(context, context.app.repository.currentOverview())
    }

    suspend fun refresh(context: Context, overview: DueOverview) = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(HomeWidget::class.java to false, CoverWidget::class.java to true).forEach { (cls, cover) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, render(context, id, overview, cover)) } }
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }

    private fun render(context: Context, widgetId: Int, overview: DueOverview, cover: Boolean): RemoteViews {
        val res = context.resources
        val views = RemoteViews(context.packageName, if (cover) R.layout.widget_cover_due else R.layout.widget_due)
        val t = WidgetLook.of(context.app.prefs.theme == "MONUMENT")
        views.setInt(R.id.widget_root, "setBackgroundResource", if (cover) t.coverBg else t.bg)
        views.setInt(R.id.widget_diamond, "setBackgroundColor", color(context, t.signal))
        views.setTextColor(R.id.widget_empty, color(context, t.muted))
        if (!cover) views.setTextColor(R.id.widget_title, color(context, t.text))

        val subtitle = when {
            overview.overdue.isNotEmpty() -> res.getQuantityString(R.plurals.widget_overdue, overview.overdue.size, overview.overdue.size)
            overview.dueSoon.isNotEmpty() -> res.getQuantityString(R.plurals.widget_due_soon, overview.dueSoon.size, overview.dueSoon.size)
            else -> ""
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle)
        val subColor = when {
            cover -> t.muted
            overview.overdue.isNotEmpty() -> t.fading
            else -> t.soon
        }
        views.setTextColor(R.id.widget_subtitle, color(context, subColor))
        views.setOnClickPendingIntent(R.id.widget_header, openApp(context, widgetId * 20 + 19, null, cover))

        // The list. A unique data URI per widget keeps each widget's adapter separate.
        val service = Intent(context, WidgetListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(WidgetListService.EXTRA_COVER, cover)
        service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
        @Suppress("DEPRECATION")
        views.setRemoteAdapter(R.id.widget_list, service)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // Taps in the list (expand/collapse, open someone, call someone) all go through WidgetActionActivity.
        val template = Intent(context, WidgetActionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = if (cover) ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY_ID).toBundle() else null
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getActivity(context, widgetId * 20 + 18, template, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, options),
        )
        return views
    }

    fun color(context: Context, id: Int) = ContextCompat.getColor(context, id)

    /** The intent that reaches someone on their usual channel: WhatsApp, a text, or the dialer. */
    fun reachIntent(context: Context, contact: TrackedContact): Intent {
        val region = context.app.prefs.region
        val intent = when (contact.preferredChannel) {
            Channel.WHATSAPP -> CallLauncher.whatsAppIntent(context, contact, region)
                ?: CallLauncher.phoneIntent(context, contact.phoneNumber)
            Channel.TEXT -> CallLauncher.textIntent(contact.phoneNumber)
            else -> CallLauncher.phoneIntent(context, contact.phoneNumber)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun openApp(context: Context, code: Int, contactId: String?, cover: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (contactId != null) intent.putExtra(MainActivity.EXTRA_CONTACT_ID, contactId)
        val options = if (cover) ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY_ID).toBundle() else null
        return PendingIntent.getActivity(context, code, intent, FLAGS, options)
    }
}
