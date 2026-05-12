package com.jlucraft.console.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.jlucraft.console.app.di.HiltServicesAccessor
import com.jlucraft.console.data.local.PushRuntimeConfigStore
import com.jlucraft.console.data.remote.NotificationHelper
import com.jlucraft.console.data.remote.PushConfigService
import com.jlucraft.console.data.remote.PushRuntimeConfigHolder
import com.jlucraft.console.data.push.PushPolicyInitializer
import com.jlucraft.console.data.remote.UnifiedPushRegistrar
import com.jlucraft.console.data.remote.UnionPushReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushLifecycleInitializer : Initializer<Unit> {

    private companion object {
        private const val TAG = "PushLifecycleInitializer"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun create(context: Context) {
        val services = HiltServicesAccessor.appServices(context.applicationContext)
        NotificationHelper.createChannels(context)
        PushPolicyInitializer.initialize(services.settingsStore)

        val configStore = PushRuntimeConfigStore(context)
        val configService = PushConfigService(services.client, configStore)

        scope.launch {

            configService.ensurePushConfig()


            val vapidKey = configStore.getVapidPublicKey()
            if (vapidKey != null) {
                PushRuntimeConfigHolder.vapidPublicKey = vapidKey
            }


            UnifiedPushRegistrar.register(context)


            UnionPushReceiver.endpointFlow.collect { endpoint ->
                if (endpoint.isNotBlank()) {
                    val devicePubkey = try {
                        services.teeAuthManager.getPublicKey()
                    } catch (e: Exception) {
                        Log.e(TAG, "Push endpoint registration requires a device public key", e)
                        return@collect
                    }
                    services.client.registerPushEndpoint(
                        endpoint = endpoint,
                        devicePubkey = devicePubkey,
                    )
                }
            }
        }


        services.pushService.connect()



    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
