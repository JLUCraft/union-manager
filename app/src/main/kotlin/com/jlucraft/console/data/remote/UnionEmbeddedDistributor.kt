package com.jlucraft.console.data.remote

import org.unifiedpush.android.embedded_fcm_distributor.EmbeddedDistributorReceiver
import org.unifiedpush.android.embedded_fcm_distributor.Gateway

/**
 * Embedded FCM distributor for UnifiedPush.
 *
 * Acts as the fallback when no external UnifiedPush distributor
 * (ntfy, NextPush, Gotify) is installed on the device.
 *
 * VAPID public key is resolved at runtime from the server's consensus push config,
 * persisted locally via [com.jlucraft.console.data.local.PushRuntimeConfigStore].
 */
public open class UnionEmbeddedDistributor : EmbeddedDistributorReceiver() {

    override val gateway = object : Gateway {
        /**
         * VAPID public key is populated at runtime from the server's consensus push config.
         * The key is set by [PushRuntimeConfigHolder] before the distributor is used.
         */
        override val vapid: String
            get() = PushRuntimeConfigHolder.vapidPublicKey ?: ""

        override fun getEndpoint(token: String): String {
            return "https://fcm.googleapis.com/fcm/send/$token"
        }
    }
}

/**
 * Thread-safe holder for the runtime VAPID public key.
 *
 * This is a simple static holder because [UnionEmbeddedDistributor.gateway]
 * is instantiated by the UnifiedPush framework and cannot receive constructor
 * injection. The key is set before UnifiedPush registration.
 */
object PushRuntimeConfigHolder {
    @Volatile
    var vapidPublicKey: String? = null
}
