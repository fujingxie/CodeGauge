package com.codegauge.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codegauge.MainActivity
import com.codegauge.R
import kotlin.math.absoluteValue

class CodeGaugeNotifications(
    private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelListener,
                "CodeGauge 监听服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持 CodeGauge 与 Companion 连接。"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelEvents,
                "CodeGauge 事件通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "额度提醒和编程会话状态通知。"
            },
        )
    }

    fun foregroundNotification(statusText: String): Notification {
        return NotificationCompat.Builder(context, ChannelListener)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CodeGauge 正在监听")
            .setContentText(statusText)
            .setContentIntent(appIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun show(spec: NotificationSpec) {
        val channel = when (spec.kind) {
            NotificationKind.Alert,
            NotificationKind.Waiting,
            NotificationKind.Done,
            -> ChannelEvents
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setContentIntent(appIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationID(spec.stableKey), notification)
    }

    private fun appIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationID(stableKey: String): Int {
        return stableKey.hashCode().absoluteValue.takeIf { it != 0 } ?: 1
    }

    companion object {
        const val ChannelListener = "codegauge_listener"
        const val ChannelEvents = "codegauge_events"
        const val ForegroundNotificationID = 1001
    }
}
