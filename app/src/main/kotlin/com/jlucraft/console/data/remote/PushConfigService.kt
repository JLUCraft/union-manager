package com.jlucraft.console.data.remote

import android.util.Log
import com.jlucraft.console.data.local.PushRuntimeConfigStore
import com.jlucraft.console.data.remote.libp2p.Libp2pClient

/**
 * Service for fetching or initializing server-side push configuration via libp2p.
 *
 * Flow:
 * 1. GetPushConfig on /control/v1 → check if server has push config.
 * 2. If not configured, InitPushConfig → trigger server-side generation.
 * 3. Save vapid_public_key to local persistent store.
 *
 * UnifiedPush remains the Android notification transport; this service
 * manages the server-side push configuration over the libp2p control channel.
 */
class PushConfigService(
    private val client: Libp2pClient,
    private val configStore: PushRuntimeConfigStore,
) {
    companion object {
        private const val TAG = "PushConfigService"
    }

    suspend fun ensurePushConfig() {
        val configResult = client.getPushConfig()
        val config = configResult.getOrNull()
        if (config != null && config.configured && config.vapidPublicKey != null) {
            configStore.setVapidPublicKey(config.vapidPublicKey)
            Log.d(TAG, "Push config fetched from server via libp2p")
            return
        }

        Log.d(TAG, "Push config not initialized on server, triggering init via libp2p")
        val initResult = client.initPushConfig()
        val initConfig = initResult.getOrNull()
        if (initConfig != null && initConfig.configured && initConfig.vapidPublicKey != null) {
            configStore.setVapidPublicKey(initConfig.vapidPublicKey)
            Log.d(TAG, "Push config initialized and saved")
        } else {
            Log.w(TAG, "Push config initialization failed: ${initResult.exceptionOrNull()}")
        }
    }
}
