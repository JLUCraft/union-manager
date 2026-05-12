package com.jlucraft.console.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jlucraft.console.data.local.PushRuntimeConfigStore
import com.jlucraft.console.data.remote.PushRuntimeConfigHolder
import com.jlucraft.console.data.remote.UnifiedPushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


 *
public class PushBootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scope.launch {

                val configStore = PushRuntimeConfigStore(context)
                configStore.refreshCache()
                val vapidKey = configStore.getVapidPublicKey()
                if (vapidKey != null) {
                    PushRuntimeConfigHolder.vapidPublicKey = vapidKey
                }

                UnifiedPushRegistrar.register(context)
            }
        }
    }
}
