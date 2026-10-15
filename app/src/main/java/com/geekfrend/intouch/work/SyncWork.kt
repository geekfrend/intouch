package com.geekfrend.intouch.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.geekfrend.intouch.app
import com.geekfrend.intouch.widget.WidgetUpdater
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Background refresh: reads new call history and redraws the widgets. Never keeps the app running. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        runCatching { app.repository.refreshFromAddressBook() }
        runCatching { app.repository.syncCallLog() }
        app.repository.refreshToday()
        runCatching { WidgetUpdater.refresh(applicationContext) }
        if (inputData.getBoolean(KEY_MIDNIGHT, false)) SyncWork.scheduleMidnight(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_MIDNIGHT = "midnight"
    }
}

object SyncWork {
    /** Every few hours, plus just after midnight so "due today" rolls over. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS).build(),
        )
        scheduleMidnight(context)
    }

    fun scheduleMidnight(context: Context) {
        val now = ZonedDateTime.now()
        val next = now.toLocalDate().plusDays(1).atTime(0, 1).atZone(now.zone)
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_MIDNIGHT to true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("midnight-${next.toLocalDate()}", ExistingWorkPolicy.KEEP, request)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork("sync-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().build())
    }
}
