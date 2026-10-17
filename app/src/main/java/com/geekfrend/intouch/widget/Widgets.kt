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
import com.geekfrend.intouch.device.Photos
import com.geekfrend.intouch.domain.DueState
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.components.signalLevel
import com.geekfrend.intouch.work.SyncWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Widget colors and shapes for each app theme (Settings › Theme). Widgets can't read the Compose theme. */
private data class WidgetLook(
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
 * Draws both widgets. Built with RemoteViews, the approach tested on the Flip 6 cover screen.
 * Colors come from res/values/colors.xml; layouts are widget_due.xml (home) and widget_cover_due.xml (cover).
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
    private val PHOTOS = intArrayOf(R.id.photo_1, R.id.photo_2, R.id.photo_3, R.id.photo_4, R.id.photo_5, R.id.photo_6)
    private val BUTTON_ICONS = intArrayOf(R.id.button_icon_1, R.id.button_icon_2, R.id.button_icon_3, R.id.button_icon_4, R.id.button_icon_5, R.id.button_icon_6)
    private val BUTTON_TEXTS = intArrayOf(R.id.button_text_1, R.id.button_text_2, R.id.button_text_3, R.id.button_text_4, R.id.button_text_5, R.id.button_text_6)
    private val SEGMENTS = arrayOf(
        intArrayOf(R.id.seg_1_1, R.id.seg_1_2, R.id.seg_1_3, R.id.seg_1_4, R.id.seg_1_5),
        intArrayOf(R.id.seg_2_1, R.id.seg_2_2, R.id.seg_2_3, R.id.seg_2_4, R.id.seg_2_5),
        intArrayOf(R.id.seg_3_1, R.id.seg_3_2, R.id.seg_3_3, R.id.seg_3_4, R.id.seg_3_5),
        intArrayOf(R.id.seg_4_1, R.id.seg_4_2, R.id.seg_4_3, R.id.seg_4_4, R.id.seg_4_5),
        intArrayOf(R.id.seg_5_1, R.id.seg_5_2, R.id.seg_5_3, R.id.seg_5_4, R.id.seg_5_5),
        intArrayOf(R.id.seg_6_1, R.id.seg_6_2, R.id.seg_6_3, R.id.seg_6_4, R.id.seg_6_5),
    )

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
        val views = RemoteViews(context.packageName, if (cover) R.layout.widget_cover_due else R.layout.widget_due)
        val t = WidgetLook.of(context.app.prefs.theme == "MONUMENT")
        views.setInt(R.id.widget_root, "setBackgroundResource", if (cover) t.coverBg else t.bg)
        views.setInt(R.id.widget_diamond, "setBackgroundColor", color(context, t.signal))
        views.setTextColor(R.id.widget_empty, color(context, t.muted))
        if (!cover) views.setTextColor(R.id.widget_title, color(context, t.text)) else views.setTextColor(R.id.widget_subtitle, color(context, t.muted))

        val height = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val maxRows = if (cover) 3 else 6
        val rowHeight = if (cover) 74 else 56
        val fits = if (height <= 0) maxRows else ((height - 48) / rowHeight).coerceIn(1, maxRows)
        val shown = due.take(fits)

        val subtitle = when {
            overview.overdue.isNotEmpty() -> res.getQuantityString(R.plurals.widget_overdue, overview.overdue.size, overview.overdue.size)
            overview.dueSoon.isNotEmpty() -> res.getQuantityString(R.plurals.widget_due_soon, overview.dueSoon.size, overview.dueSoon.size)
            else -> ""
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle)
        if (!cover) {
            val subColor = if (overview.overdue.isNotEmpty()) t.fading else t.soon
            views.setTextColor(R.id.widget_subtitle, color(context, subColor))
        }
        views.setOnClickPendingIntent(R.id.widget_header, openApp(context, widgetId * 20 + 19, null, cover))
        views.setViewVisibility(R.id.widget_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)

        for (i in 0 until maxRows) {
            if (i >= shown.size) {
                views.setViewVisibility(ROWS[i], View.GONE)
                continue
            }
            val item = shown[i]
            val overdue = item.status.state == DueState.OVERDUE
            val accent = color(context, if (overdue) t.fading else t.soon)
            val contact = item.contact
            views.setViewVisibility(ROWS[i], View.VISIBLE)
            views.setTextColor(NAMES[i], color(context, t.text))
            views.setTextViewText(NAMES[i], if (cover) contact.displayName.substringBefore(' ') else contact.displayName)
            views.setTextViewText(STATUSES[i], Format.due(res, item.status))
            views.setTextColor(STATUSES[i], accent)
            val photoPx = ((if (cover) 40 else 32) * res.displayMetrics.density).toInt()
            views.setImageViewBitmap(PHOTOS[i], Photos.circle(context, contact.displayName, contact.photoUri, photoPx, color(context, t.text), t.light))

            // Overdue rows get the filled button; due-soon rows an outlined one.
            val buttonBg = if (overdue) t.button else t.buttonOutline
            val iconColor = color(context, if (overdue) t.onSignal else t.text)
            views.setInt(BUTTONS[i], "setBackgroundResource", buttonBg)
            val icon = widgetIcon(contact.preferredChannel)
            if (cover) {
                views.setImageViewResource(BUTTONS[i], icon)
                views.setInt(BUTTONS[i], "setColorFilter", iconColor)
                views.setContentDescription(BUTTONS[i], Format.callLabel(res, contact.preferredChannel) + " " + contact.displayName)
                views.setInt(ROWS[i], "setBackgroundResource", if (overdue) t.cardFading else t.card)
            } else {
                views.setImageViewResource(BUTTON_ICONS[i], icon)
                views.setInt(BUTTON_ICONS[i], "setColorFilter", iconColor)
                views.setTextViewText(BUTTON_TEXTS[i], Format.channel(res, contact.preferredChannel))
                views.setTextColor(BUTTON_TEXTS[i], iconColor)
                val level = signalLevel(item.status, contact.frequencyDays)
                val off = color(context, t.signalOff)
                SEGMENTS[i].forEachIndexed { k, seg -> views.setInt(seg, "setBackgroundColor", if (k < level) accent else off) }
            }
            views.setOnClickPendingIntent(BUTTONS[i], callIntent(context, widgetId * 20 + i * 2, contact, region))
            views.setOnClickPendingIntent(ROW_TEXT[i], openApp(context, widgetId * 20 + i * 2 + 1, contact.id, cover))
        }
        return views
    }

    private fun color(context: Context, id: Int) = ContextCompat.getColor(context, id)

    private fun widgetIcon(c: Channel) = when (c) {
        Channel.WHATSAPP -> R.drawable.ic_whatsapp
        Channel.TEXT -> R.drawable.ic_text
        else -> R.drawable.ic_call
    }

    /** Goes straight to the dialer or WhatsApp, which worked with the Flip closed in testing. */
    private fun callIntent(context: Context, code: Int, contact: TrackedContact, region: String): PendingIntent {
        val intent = when (contact.preferredChannel) {
            Channel.WHATSAPP -> CallLauncher.whatsAppIntent(context, contact, region)
                ?: CallLauncher.phoneIntent(context, contact.phoneNumber)
            Channel.TEXT -> CallLauncher.textIntent(contact.phoneNumber)
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
