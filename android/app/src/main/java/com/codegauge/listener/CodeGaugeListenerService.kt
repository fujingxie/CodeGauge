package com.codegauge.listener

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.activity.ActivityStreamConnection
import com.codegauge.activity.OkHttpActivityStreamClient
import com.codegauge.notification.CodeGaugeNotifications
import com.codegauge.notification.NotificationMapper
import com.codegauge.notification.NotificationPolicy
import com.codegauge.pairing.EncryptedPairingStore
import com.codegauge.pairing.PairingRecord
import com.codegauge.settings.AppSettings
import com.codegauge.settings.OkHttpSettingsApi
import com.codegauge.settings.SettingsApi
import com.codegauge.widget.CodeGaugeWidgetScheduler
import com.codegauge.widget.CodeGaugeWidgetUpdater
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CodeGaugeListenerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notifications: CodeGaugeNotifications
    private lateinit var streamClient: ActivityStreamClient
    private lateinit var settingsApi: SettingsApi

    private var connection: ActivityStreamConnection? = null
    private var listeningStarted = false

    override fun onCreate() {
        super.onCreate()
        notifications = CodeGaugeNotifications(this)
        notifications.createChannels()
        streamClient = OkHttpActivityStreamClient()
        settingsApi = OkHttpSettingsApi()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            CodeGaugeNotifications.ForegroundNotificationID,
            notifications.foregroundNotification("正在连接 Companion..."),
        )
        startListeningOnce()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopListening()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startListeningOnce() {
        if (listeningStarted) {
            return
        }
        listeningStarted = true

        serviceScope.launch {
            val pairing = EncryptedPairingStore(this@CodeGaugeListenerService).load()
            if (pairing == null) {
                Log.w(Tag, "No pairing found; listener service stopping")
                stopSelf()
                return@launch
            }

            var reconnectDelayMs = 1_000L
            CodeGaugeWidgetScheduler.schedule(this@CodeGaugeListenerService)
            CodeGaugeWidgetUpdater.refresh(this@CodeGaugeListenerService)
            while (isActive) {
                val disconnected = CompletableDeferred<Unit>()
                connection = streamClient.connect(
                    pairing = pairing,
                    onMessage = { message ->
                        NotificationMapper.map(message)?.let { spec ->
                            serviceScope.launch {
                                val settings = loadNotificationSettings(pairing)
                                if (settings != null && NotificationPolicy.shouldShow(settings, spec)) {
                                    notifications.show(spec)
                                } else {
                                    Log.i(Tag, "Notification suppressed by settings: ${spec.kind}")
                                }
                            }
                        }
                        serviceScope.launch {
                            CodeGaugeWidgetUpdater.refresh(this@CodeGaugeListenerService)
                        }
                    },
                    onFailure = { error ->
                        Log.w(Tag, "Stream disconnected", error)
                        disconnected.complete(Unit)
                    },
                    onClosed = {
                        disconnected.complete(Unit)
                    },
                )
                updateForeground("已连接 ${pairing.serverName}")
                disconnected.await()
                connection?.close()
                connection = null
                updateForeground("正在重新连接 Companion...")
                delay(reconnectDelayMs)
                reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun loadNotificationSettings(pairing: PairingRecord): AppSettings? {
        return runCatching {
            settingsApi.settings(pairing)
        }.getOrElse { error ->
            Log.w(Tag, "Load notification settings failed; suppressing business notification", error)
            null
        }
    }

    private fun stopListening() {
        connection?.close()
        connection = null
        listeningStarted = false
    }

    private fun updateForeground(statusText: String) {
        val notification = notifications.foregroundNotification(statusText)
        startForeground(CodeGaugeNotifications.ForegroundNotificationID, notification)
    }

    companion object {
        private const val Tag = "CodeGaugeListener"
        private const val ActionStart = "com.codegauge.listener.START"
        private const val ActionStop = "com.codegauge.listener.STOP"

        fun start(context: Context) {
            val intent = Intent(context, CodeGaugeListenerService::class.java)
                .setAction(ActionStart)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CodeGaugeListenerService::class.java)
                .setAction(ActionStop)
            context.startService(intent)
        }
    }
}
