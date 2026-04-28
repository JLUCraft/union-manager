package com.jlucraft.console.startup

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.startup.Initializer
import com.jlucraft.console.di.ServiceLocator
import com.jlucraft.console.data.remote.NotificationHelper
import com.jlucraft.console.data.remote.UnionPushReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

class PushLifecycleInitializer : Initializer<Unit> {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Suppress("DEPRECATION")
    override fun create(context: Context) {
        NotificationHelper.createChannels(context)

        // Register with UnifiedPush (uses embedded FCM distributor as fallback)
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { success ->
            if (success) {
                UnifiedPush.register(context)
            }
        }

        // Listen for endpoint changes and report to server
        scope.launch {
            UnionPushReceiver.endpointFlow.collect { endpoint ->
                if (endpoint.isNotBlank()) {
                    ServiceLocator.apiService.registerPushEndpoint(endpoint)
                }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                ServiceLocator.pushService.connect()
            }

            override fun onStop(owner: LifecycleOwner) {
                ServiceLocator.pushService.disconnect()
            }
        })
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(ServiceLocatorInitializer::class.java)
}
