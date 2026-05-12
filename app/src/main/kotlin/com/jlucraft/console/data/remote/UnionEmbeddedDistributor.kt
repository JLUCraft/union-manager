package com.jlucraft.console.data.remote

import org.unifiedpush.android.embedded_fcm_distributor.EmbeddedDistributorReceiver
import org.unifiedpush.android.embedded_fcm_distributor.Gateway


 *
 *
public open class UnionEmbeddedDistributor : EmbeddedDistributorReceiver() {

    override val gateway = object : Gateway {

        override val vapid: String
            get() = PushRuntimeConfigHolder.vapidPublicKey ?: ""

        override fun getEndpoint(token: String): String {
            return "https://fcm.googleapis.com/fcm/send/$token"
        }
    }
}


 *
object PushRuntimeConfigHolder {
    @Volatile
    var vapidPublicKey: String? = null
}
