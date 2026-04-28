package com.jlucraft.console.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jlucraft.console.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_PUSH = "union_push"
    const val CHANNEL_ALERTS = "union_alerts"
    private const val NOTIFICATION_ID_BASE = 5000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pushChannel = NotificationChannel(
            CHANNEL_PUSH,
            "Push Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Real-time notifications from Union Manager"
        }
        manager.createNotificationChannel(pushChannel)

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical alerts and incidents"
        }
        manager.createNotificationChannel(alertsChannel)
    }

    fun show(context: Context, title: String, body: String, channelId: String = CHANNEL_PUSH) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (channelId == CHANNEL_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (NOTIFICATION_ID_BASE + System.currentTimeMillis() % 10000).toInt(),
                notification
            )
        } catch (_: SecurityException) {
        }
    }
}
