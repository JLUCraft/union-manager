package com.jlucraft.console.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.jlucraft.console.data.remote.NotificationHelper

data class ChannelConfig(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    val bypassDnd: Boolean = false,
    val lockscreenVisibility: Int = NotificationCompat.VISIBILITY_PRIVATE
)

object NotificationChannelRegistry {
    val ALL_CHANNELS = listOf(
        ChannelConfig(
            id = NotificationHelper.CHANNEL_PUSH,
            name = "Push Notifications",
            description = "Real-time notifications from Union Manager",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelConfig(
            id = NotificationHelper.CHANNEL_ALERTS,
            name = "Alerts",
            description = "Critical alerts and incidents",
            importance = NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelConfig(
            id = NotificationHelper.CHANNEL_AUTH_CHALLENGE,
            name = "\u8EAB\u4EFD\u9A8C\u8BC1\u8BF7\u6C42",
            description = "\u9700\u8981\u7ACB\u5373\u786E\u8BA4\u7684\u8EAB\u4EFD\u9A8C\u8BC1\u8BF7\u6C42",
            importance = NotificationManager.IMPORTANCE_HIGH,
            bypassDnd = true,
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        )
    )

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (config in ALL_CHANNELS) {
            val channel = NotificationChannel(config.id, config.name, config.importance).apply {
                description = config.description
                setBypassDnd(config.bypassDnd)
                lockscreenVisibility = config.lockscreenVisibility
            }
            manager.createNotificationChannel(channel)
        }
    }
}
