package com.geekfrend.intouch.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.geekfrend.intouch.app
import java.util.concurrent.TimeUnit

/**
 * Backup trigger for the "Did you connect?" notification when WhatsApp detection can't tell
 * (detection off, or the call never showed up). Checks every few minutes while a WhatsApp call
 * started from InTouch is still unanswered, for up to 2 hours.
 */
class PromptCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = applicationContext.app.repository
        val shown = repo.notifyPromptIfDue()
        if (!shown && repo.hasPendingWhatsAppCall()) PromptWork.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

object PromptWork {
    private const val NAME = "prompt-check"
    private const val DELAY_MINUTES = 3L

    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            policy,
            OneTimeWorkRequestBuilder<PromptCheckWorker>().setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES).build(),
        )
    }
}
