package com.jlucraft.console.data.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jlucraft.console.ui.MainActivity

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_PUSH = "union_push"
    const val CHANNEL_ALERTS = "union_alerts"
    const val CHANNEL_AUTH_CHALLENGE = "union_auth_challenge"
    private const val NOTIFICATION_ID_BASE = 5000


    const val EXTRA_AUTH_CHALLENGE_EVENT = "auth_challenge_event"

    fun createChannels(context: Context) {
        com.jlucraft.console.data.push.NotificationChannelRegistry.createChannels(context)
    }


     *
    fun show(
        context: Context,
        title: String,
        body: String,
        channelId: String = CHANNEL_PUSH,
        intentAction: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (intentAction != null) {
                action = intentAction
                putExtra(EXTRA_AUTH_CHALLENGE_EVENT, true)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (channelId) {
            CHANNEL_AUTH_CHALLENGE -> NotificationCompat.PRIORITY_MAX
            CHANNEL_ALERTS -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(
                if (channelId == CHANNEL_AUTH_CHALLENGE) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_EVENT
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (NOTIFICATION_ID_BASE + System.currentTimeMillis() % 10000).toInt(),
                notification
            )
        } catch (_: SecurityException) {
            Log.w(TAG, "Notification not permitted, skipping")
        }
    }
}
