package com.codegauge.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CodeGaugeWidgetScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CodeGaugeWidgetWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun refreshSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<CodeGaugeWidgetWorker>()
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RefreshSoonWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(WorkName)
        workManager.cancelUniqueWork(RefreshSoonWorkName)
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private const val WorkName = "codegauge-widget-refresh"
    private const val RefreshSoonWorkName = "codegauge-widget-refresh-soon"
}
