package com.codegauge.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CodeGaugeWidgetWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        CodeGaugeWidgetUpdater.refresh(applicationContext)
        return Result.success()
    }
}

