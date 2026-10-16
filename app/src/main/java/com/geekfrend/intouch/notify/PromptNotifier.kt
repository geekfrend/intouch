package com.geekfrend.intouch.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geekfrend.intouch.MainActivity
import com.geekfrend.intouch.R
import com.geekfrend.intouch.app
import com.geekfrend.intouch.data.Outcome
import com.geekfrend.intouch.data.TrackedContact
import com.geekfrend.intouch.domain.HOUR_MS
import com.geekfrend.intouch.ui.Format
import kotlinx.coroutines.launch

/**
 * FR-12: "Did you connect?" as a notification with answer buttons. It stays in the shade until
 * answered, and expires after 2 hours. Swiping it away counts as "No answer".
 */
object PromptNotifier {
    private const val CHANNEL = "checkins"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    const val ACTION_ANSWER = "com.geekfrend.intouch.action.ANSWER_PROMPT"
    const val ACTION_DISMISSED = "com.geekfrend.intouch.action.PROMPT_DISMISSED"
    const val EXTRA_CONTACT = "contactId"
    const val EXTRA_OUTCOME = "outcome"

    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notificationId(contactId: String) = 1_000 + (contactId.hashCode() and 0x7fffffff) % 100_000

    private fun ensureChannel(context: Context) {
        val res = context.resources
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(res.getString(R.string.notif_channel_name))
                .setDescription(res.getString(R.string.notif_channel_description))
                .build()
        )
    }

    @SuppressLint("MissingPermission") // checked by canPost
    fun show(context: Context, contact: TrackedContact, startedAt: Long) {
        if (!canPost(context)) return
        ensureChannel(context)
        val res = context.resources
        val id = notificationId(contact.id)

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CONTACT_ID, contact.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS,
        )

        fun broadcast(action: String, outcome: Outcome?, code: Int): PendingIntent {
            val intent = Intent(context, PromptReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_CONTACT, contact.id)
            if (outcome != null) intent.putExtra(EXTRA_OUTCOME, outcome.name)
            return PendingIntent.getBroadcast(context, id * 4 + code, intent, FLAGS)
        }

        val remaining = (2 * HOUR_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(60_000L)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.ic_fg))
            .setContentTitle(res.getString(R.string.prompt_title, contact.displayName))
            .setContentText(res.getString(R.string.notif_prompt_text, Format.time(startedAt)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setTimeoutAfter(remaining)
            .setContentIntent(open)
            .setDeleteIntent(broadcast(ACTION_DISMISSED, null, 3))
            .addAction(0, res.getString(R.string.prompt_yes), broadcast(ACTION_ANSWER, Outcome.CONNECTED, 0))
            .addAction(0, res.getString(R.string.prompt_no_answer), broadcast(ACTION_ANSWER, Outcome.NO_ANSWER, 1))
            .addAction(0, res.getString(R.string.prompt_left_message), broadcast(ACTION_ANSWER, Outcome.LEFT_MESSAGE, 2))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, contactId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(contactId))
    }
}

/** Handles the notification's buttons and swipe-away. */
class PromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val contactId = intent.getStringExtra(PromptNotifier.EXTRA_CONTACT) ?: return
        val outcome = when (intent.action) {
            PromptNotifier.ACTION_ANSWER ->
                intent.getStringExtra(PromptNotifier.EXTRA_OUTCOME)?.let { runCatching { Outcome.valueOf(it) }.getOrNull() }
            PromptNotifier.ACTION_DISMISSED -> Outcome.NO_ANSWER
            else -> null
        } ?: return
        val app = context.app
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.answerPrompt(contactId, outcome)
            } finally {
                pending.finish()
            }
        }
    }
}
