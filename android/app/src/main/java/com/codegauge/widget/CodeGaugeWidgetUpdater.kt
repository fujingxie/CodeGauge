package com.codegauge.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.OkHttpDashboardApi
import com.codegauge.pairing.EncryptedPairingStore
import java.time.Instant

object CodeGaugeWidgetUpdater {
    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        val store = CodeGaugeWidgetStore(appContext)
        val pairing = EncryptedPairingStore(appContext).load()
        if (pairing == null) {
            store.save(WidgetFormatter.unpaired())
            CodeGaugeWidget().updateAll(appContext)
            return
        }

        val state = try {
            val snapshot = DashboardRepository(OkHttpDashboardApi()).loadStatus(pairing)
            WidgetFormatter.fromSnapshot(snapshot, Instant.now())
        } catch (exception: Exception) {
            WidgetFormatter.error(exception.message ?: "刷新失败")
        }

        store.save(state)
        CodeGaugeWidget().updateAll(appContext)
    }
}

