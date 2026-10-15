package com.geekfrend.intouch.lab

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Test widget for the home screen and the Flip cover screen. */
class TestWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        /** Display ID of the Flip's cover screen, per Samsung's Flex Window guide. */
        private const val COVER_DISPLAY_ID = 1
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        fun refreshAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, TestWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, build(ctx)) }
        }

        private fun openApp(ctx: Context, code: Int, source: String, onCover: Boolean): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SOURCE, source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = if (onCover) {
                ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY_ID).toBundle()
            } else {
                null
            }
            return PendingIntent.getActivity(ctx, code, intent, FLAGS, options)
        }

        fun build(ctx: Context): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_test)
            val picked = Store.picked(ctx)
            views.setTextViewText(
                R.id.widget_contact,
                if (picked != null) "Test contact: ${picked.name}" else "Pick a test contact in the app",
            )

            val call = if (picked != null) {
                PendingIntent.getActivity(
                    ctx, 10,
                    Device.phoneCallIntent(picked.number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    FLAGS,
                )
            } else {
                openApp(ctx, 11, "widget-no-contact", onCover = false)
            }

            val waIntent = picked?.let { Device.waVoiceIntent(it) }
                ?: picked?.let { Device.waChatIntent(it.number) }
            val wa = if (waIntent != null) {
                PendingIntent.getActivity(ctx, 20, waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), FLAGS)
            } else {
                openApp(ctx, 21, "widget-no-contact", onCover = false)
            }

            views.setOnClickPendingIntent(R.id.btn_call, call)
            views.setOnClickPendingIntent(R.id.btn_wa, wa)
            views.setOnClickPendingIntent(R.id.btn_open_cover, openApp(ctx, 30, "widget, Open here", onCover = true))
            views.setOnClickPendingIntent(R.id.btn_open_default, openApp(ctx, 31, "widget, Open app", onCover = false))
            return views
        }
    }
}
