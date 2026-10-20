package com.geekfrend.intouch.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.geekfrend.intouch.MainActivity
import com.geekfrend.intouch.R
import com.geekfrend.intouch.app
import com.geekfrend.intouch.data.DueItem
import com.geekfrend.intouch.data.DueOverview
import com.geekfrend.intouch.device.Photos
import com.geekfrend.intouch.ui.Format
import com.geekfrend.intouch.ui.TileInfo
import com.geekfrend.intouch.ui.components.signalLevel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The three groups the widget lists, weakest signal first. */
enum class WidgetSection { FADING, SOON, STRONG }

/** Which sections are open, per widget. Fading starts open (or Soon, when nobody is fading). */
object WidgetSections {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_sections", Context.MODE_PRIVATE)

    fun open(context: Context, widgetId: Int, overview: DueOverview): Set<WidgetSection> {
        val saved = prefs(context).getStringSet("open_$widgetId", null)
        if (saved != null) return saved.mapNotNull { runCatching { WidgetSection.valueOf(it) }.getOrNull() }.toSet()
        return if (overview.overdue.isNotEmpty()) setOf(WidgetSection.FADING) else setOf(WidgetSection.SOON)
    }

    fun toggle(context: Context, widgetId: Int, section: WidgetSection, overview: DueOverview) {
        val now = open(context, widgetId, overview)
        val next = if (section in now) now - section else now + section
        prefs(context).edit().putStringSet("open_$widgetId", next.map { it.name }.toSet()).apply()
    }
}

/** Fills the widget's scrolling list: a header per section, and the people in each open section. */
class WidgetListService : RemoteViewsService() {
    companion object {
        const val EXTRA_COVER = "cover"
        const val EXTRA_ACTION = "widgetAction"
        const val EXTRA_SECTION = "section"
        const val EXTRA_CONTACT = "contactId"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_OPEN = "open"
        const val ACTION_REACH = "reach"
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        intent.getBooleanExtra(EXTRA_COVER, false),
    )

    private sealed interface Row {
        data class Header(val section: WidgetSection, val count: Int, val open: Boolean) : Row
        data class Person(val item: DueItem, val section: WidgetSection) : Row
    }

    private class Factory(val context: Context, val widgetId: Int, val cover: Boolean) : RemoteViewsFactory {
        private var rows: List<Row> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            val overview = runBlocking { context.app.repository.currentOverview() }
            val open = WidgetSections.open(context, widgetId, overview)
            val groups = listOf(
                WidgetSection.FADING to overview.overdue,
                WidgetSection.SOON to overview.dueSoon,
                WidgetSection.STRONG to overview.onTrack,
            )
            rows = buildList {
                groups.forEach { (section, people) ->
                    if (people.isEmpty()) return@forEach
                    val isOpen = section in open
                    add(Row.Header(section, people.size, isOpen))
                    if (isOpen) people.forEach { add(Row.Person(it, section)) }
                }
            }
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val t = WidgetLook.of(context.app.prefs.theme == "MONUMENT")
            return when (val row = rows.getOrNull(position)) {
                is Row.Header -> header(row, t)
                is Row.Person -> person(row, t)
                null -> RemoteViews(context.packageName, R.layout.widget_row_header)
            }
        }

        private fun accent(section: WidgetSection, t: WidgetLook) = when (section) {
            WidgetSection.FADING -> t.fading
            WidgetSection.SOON -> t.soon
            WidgetSection.STRONG -> t.signal
        }

        private fun header(row: Row.Header, t: WidgetLook): RemoteViews {
            val res = context.resources
            val name = res.getString(
                when (row.section) {
                    WidgetSection.FADING -> R.string.summary_fading
                    WidgetSection.SOON -> R.string.summary_soon
                    WidgetSection.STRONG -> R.string.summary_strong
                }
            )
            return RemoteViews(context.packageName, R.layout.widget_row_header).apply {
                setInt(R.id.section_dot, "setBackgroundColor", WidgetUpdater.color(context, accent(row.section, t)))
                setTextViewText(R.id.section_label, res.getString(R.string.widget_section, name, row.count))
                setTextColor(R.id.section_label, WidgetUpdater.color(context, t.text))
                setTextViewText(R.id.section_chevron, if (row.open) "▾" else "▸")
                setTextColor(R.id.section_chevron, WidgetUpdater.color(context, t.muted))
                setContentDescription(R.id.section_root, res.getString(if (row.open) R.string.widget_collapse else R.string.widget_expand, name))
                setOnClickFillInIntent(
                    R.id.section_root,
                    Intent().putExtra(EXTRA_ACTION, ACTION_TOGGLE).putExtra(EXTRA_SECTION, row.section.name)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                )
            }
        }

        private fun person(row: Row.Person, t: WidgetLook): RemoteViews {
            val res = context.resources
            val item = row.item
            val contact = item.contact
            val fading = row.section == WidgetSection.FADING
            val accent = WidgetUpdater.color(context, accent(row.section, t))
            val textColor = WidgetUpdater.color(context, t.text)
            val info = runCatching { TileInfo.valueOf(context.app.prefs.tileInfo) }.getOrDefault(TileInfo.TALKED)
            val status = when (info) {
                TileInfo.TALKED -> Format.lastCallShort(res, item.status)
                TileInfo.DUE -> Format.due(res, item.status)
                TileInfo.BOTH -> Format.due(res, item.status) + " · " + Format.lastCallShort(res, item.status).replaceFirstChar { it.lowercase() }
            }
            val iconColor = WidgetUpdater.color(context, if (fading) t.onSignal else t.text)
            val icon = when (contact.preferredChannel) {
                com.geekfrend.intouch.data.Channel.WHATSAPP -> R.drawable.ic_whatsapp
                com.geekfrend.intouch.data.Channel.TEXT -> R.drawable.ic_text
                else -> R.drawable.ic_call
            }
            val layout = if (cover) R.layout.widget_row_person_cover else R.layout.widget_row_person
            return RemoteViews(context.packageName, layout).apply {
                val photoPx = ((if (cover) 40 else 32) * res.displayMetrics.density).toInt()
                setImageViewBitmap(R.id.photo, Photos.circle(context, contact.displayName, contact.photoUri, photoPx, textColor, t.light))
                setTextViewText(R.id.name, if (cover) contact.displayName.substringBefore(' ') else contact.displayName)
                setTextColor(R.id.name, textColor)
                setTextViewText(R.id.status, status)
                setTextColor(R.id.status, if (row.section == WidgetSection.STRONG) WidgetUpdater.color(context, t.muted) else accent)
                setInt(R.id.button, "setBackgroundResource", if (fading) t.button else t.buttonOutline)
                if (cover) {
                    setInt(R.id.card, "setBackgroundResource", if (fading) t.cardFading else t.card)
                    setImageViewResource(R.id.button, icon)
                    setInt(R.id.button, "setColorFilter", iconColor)
                    setContentDescription(R.id.button, Format.callLabel(res, contact.preferredChannel) + " " + contact.displayName)
                } else {
                    setImageViewResource(R.id.button_icon, icon)
                    setInt(R.id.button_icon, "setColorFilter", iconColor)
                    setTextViewText(R.id.button_text, Format.channel(res, contact.preferredChannel))
                    setTextColor(R.id.button_text, iconColor)
                    val level = signalLevel(item.status, contact.frequencyDays)
                    val off = WidgetUpdater.color(context, t.signalOff)
                    listOf(R.id.seg_1, R.id.seg_2, R.id.seg_3, R.id.seg_4, R.id.seg_5).forEachIndexed { k, seg ->
                        setInt(seg, "setBackgroundColor", if (k < level) accent else off)
                    }
                }
                setOnClickFillInIntent(R.id.button, Intent().putExtra(EXTRA_ACTION, ACTION_REACH).putExtra(EXTRA_CONTACT, contact.id))
                setOnClickFillInIntent(R.id.row_text, Intent().putExtra(EXTRA_ACTION, ACTION_OPEN).putExtra(EXTRA_CONTACT, contact.id))
            }
        }
    }
}

/**
 * Receives taps from the widget lists. It has no screen of its own: it expands or collapses a
 * section, opens someone in InTouch, or calls/texts/WhatsApps them, then closes straight away.
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext.app
        when (intent.getStringExtra(WidgetListService.EXTRA_ACTION)) {
            WidgetListService.ACTION_TOGGLE -> {
                val section = runCatching { WidgetSection.valueOf(intent.getStringExtra(WidgetListService.EXTRA_SECTION) ?: "") }.getOrNull()
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (section != null) {
                    app.scope.launch {
                        val overview = app.repository.currentOverview()
                        WidgetSections.toggle(applicationContext, widgetId, section, overview)
                        WidgetUpdater.refresh(applicationContext, overview)
                    }
                }
            }
            WidgetListService.ACTION_OPEN -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_CONTACT_ID, intent.getStringExtra(WidgetListService.EXTRA_CONTACT)),
                )
            }
            WidgetListService.ACTION_REACH -> {
                val id = intent.getStringExtra(WidgetListService.EXTRA_CONTACT)
                val contact = runBlocking {
                    app.repository.currentOverview().let { it.overdue + it.dueSoon + it.onTrack }.firstOrNull { it.contact.id == id }?.contact
                }
                if (contact != null) runCatching { startActivity(WidgetUpdater.reachIntent(this, contact)) }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
