package com.jlucraft.console.data.remote

import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor


 *
 *
object UnifiedPushRegistrar {
    private const val TAG = "UnifiedPushRegistrar"

    fun register(context: Context) {

        val ackDistributor = UnifiedPush.getAckDistributor(context)
        if (ackDistributor != null) {
            Log.d(TAG, "Using ack distributor: $ackDistributor")
            UnifiedPush.register(context)
            return
        }


        when (val resolved = UnifiedPush.resolveDefaultDistributor(context)) {
            is ResolvedDistributor.Found -> {
                Log.d(TAG, "Found distributor: ${resolved.packageName}")
                UnifiedPush.saveDistributor(context, resolved.packageName)
                UnifiedPush.register(context)
            }
            is ResolvedDistributor.ToSelect -> {



                Log.d(TAG, "Multiple distributors available, auto-selecting")
                tryAutoSelectExternal(context)
            }
            is ResolvedDistributor.NoneAvailable -> {
                Log.d(TAG, "No external distributor, trying embedded FCM")
                tryEmbeddedFcm(context)
            }
        }
    }

    private fun tryAutoSelectExternal(context: Context) {






        tryEmbeddedFcm(context)
    }

    private fun tryEmbeddedFcm(context: Context) {



        try {
            UnifiedPush.register(context)
        } catch (e: Exception) {
            Log.w(TAG, "Embedded FCM registration attempt failed", e)
        }
    }
}
