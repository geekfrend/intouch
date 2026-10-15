package com.geekfrend.intouch.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.geekfrend.intouch.MainActivity
import com.geekfrend.intouch.R
import com.geekfrend.intouch.app
import com.geekfrend.intouch.data.Channel
import com.geekfrend.intouch.data.DueItem
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.device.CallLauncher
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.work.SyncWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * Draws both widgets. Built with RemoteViews, the approach tested on the Flip 6 cover screen.
 * Colors and sizes come from res/values/colors.xml and res/layout/widget_due.xml.
 */
object WidgetUpdater {
    /** Display ID of the Flip's cover screen, per Samsung's Flex Window guide (tested Oct 15). */
    private const val COVER_DISPLAY_ID = 1
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private val ROWS = intArrayOf(R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4, R.id.row_5, R.id.row_6)
    private val ROW_TEXT = intArrayOf(R.id.row_text_1, R.id.row_text_2, R.id.row_text_3, R.id.row_text_4, R.id.row_text_5, R.id.row_text_6)
    private val NAMES = intArrayOf(R.id.name_1, R.id.name_2, R.id.name_3, R.id.name_4, R.id.name_5, R.id.name_6)
    private val STATUSES = intArrayOf(R.id.status_1, R.id.status_2, R.id.status_3, R.id.status_4, R.id.status_5, R.id.status_6)
    private val BUTTONS = intArrayOf(R.id.button_1, R.id.button_2, R.id.button_3, R.id.button_4, R.id.button_5, R.id.button_6)

    suspend fun refresh(context: Context) {
        refresh(context, context.app.repository.currentOverview())
    }

    suspend fun refresh(context: Context, overview: DueOverview) = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        val due = overview.overdue + overview.dueSoon
        val region = context.app.prefs.region
        listOf(HomeWidget::class.java to false, CoverWidget::class.java to true).forEach { (cls, cover) ->
            manager.getAppWidgetIds(ComponentName(context, cls)).forEach { id ->
                runCatching { manager.updateAppWidget(id, render(context, manager, id, due, overview, cover, region)) }
            }
        }
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        due: List<DueItem>,
        overview: DueOverview,
        cover: Boolean,
        region: String,
    ): RemoteViews {
        val res = context.resources
        val views = RemoteViews(context.packageName, R.layout.widget_due)

        val height = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val maxRows = if (cover) 3 else 6
        val fits = if (height <= 0) maxRows else ((height - 56) / 56).coerceIn(1, maxRows)
        val shown = due.take(fits)

        val subtitle = when {
            overview.overdue.isNotEmpty() -> res.getQuantityString(R.plurals.widget_overdue, overview.overdue.size, overview.overdue.size)
            overview.dueSoon.isNotEmpty() -> res.getQuantityString(R.plurals.widget_due_soon, overview.dueSoon.size, overview.dueSoon.size)
            else -> ""
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle)
        views.setOnClickPendingIntent(R.id.widget_header, openApp(context, widgetId * 20 + 19, null, cover))
        views.setViewVisibility(R.id.widget_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)

        for (i in ROWS.indices) {
            if (i >= shown.size) {
                views.setViewVisibility(ROWS[i], View.GONE)
                continue
            }
            val item = shown[i]
            val color = if (item.status.state == DueState.OVERDUE) R.color.widget_overdue else R.color.widget_due_soon
            views.setViewVisibility(ROWS[i], View.VISIBLE)
            views.setTextViewText(NAMES[i], item.contact.displayName)
            views.setTextViewText(STATUSES[i], Format.due(res, item.status))
            views.setTextColor(STATUSES[i], ContextCompat.getColor(context, color))
            views.setTextViewText(BUTTONS[i], Format.callLabel(res, item.contact.preferredChannel))
            views.setOnClickPendingIntent(BUTTONS[i], callIntent(context, widgetId * 20 + i * 2, item.contact, region))
            views.setOnClickPendingIntent(ROW_TEXT[i], openApp(context, widgetId * 20 + i * 2 + 1, item.contact.id, cover))
        }
        return views
    }

    /** Goes straight to the dialer or WhatsApp, which worked with the Flip closed in testing. */
    private fun callIntent(context: Context, code: Int, contact: TrackedContact, region: String): PendingIntent {
        val intent = when (contact.preferredChannel) {
            Channel.WHATSAPP -> CallLauncher.whatsAppIntent(context, contact, region)
                ?: CallLauncher.phoneIntent(context, contact.phoneNumber)
            else -> CallLauncher.phoneIntent(context, contact.phoneNumber)
        }
        return PendingIntent.getActivity(context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)
    }

    private fun openApp(context: Context, code: Int, contactId: String?, cover: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (contactId != null) intent.putExtra(MainActivity.EXTRA_CONTACT_ID, contactId)
        val options = if (cover) ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY_ID).toBundle() else null
        return PendingIntent.getActivity(context, code, intent, FLAGS, options)
    }
}
