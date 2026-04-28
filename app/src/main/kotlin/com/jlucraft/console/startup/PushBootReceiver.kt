package com.jlucraft.console.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.unifiedpush.android.connector.UnifiedPush

class PushBootReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
                if (success) {
                    UnifiedPush.register(context)
                }
            }
        }
    }
}
