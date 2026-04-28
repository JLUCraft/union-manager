package com.jlucraft.console.data.remote

import org.unifiedpush.android.embedded_fcm_distributor.EmbeddedDistributorReceiver
import org.unifiedpush.android.embedded_fcm_distributor.Gateway

/**
 * Embedded FCM distributor for UnifiedPush.
 *
 * This distributor is bundled inside the union-manager app and acts as the
 * fallback when no external UnifiedPush distributor (ntfy, NextPush, Gotify)
 * is installed on the device.
 *
 * It uses Google FCM under the hood but exposes the UnifiedPush protocol,
 * so the app never directly depends on Google Firebase APIs.
 */
class UnionEmbeddedDistributor : EmbeddedDistributorReceiver() {

    /**
     * Use the default Google FCM gateway.
     * The endpoint format is: https://fcm.googleapis.com/fcm/send/{token}
     *
     * If the federated-server needs a custom gateway, override this.
     */
    override val gateway = object : Gateway {
        override val vapid: String = ""

        override fun getEndpoint(token: String): String {
            return "https://fcm.googleapis.com/fcm/send/$token"
        }
    }
}
