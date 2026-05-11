package com.jlucraft.console.app.di

import android.content.Context
import com.jlucraft.console.app.AppServices
import dagger.hilt.EntryPoints

/**
 * Single access point for non-Hilt-managed components (e.g.,
 * [androidx.startup.Initializer] implementations, [android.content.BroadcastReceiver]
 * instances) to reach the Hilt dependency graph.
 *
 * Always resolves services from the **application context** to avoid leaking
 * Activity/Service scoped graphs.  Callers must pass `context.applicationContext`.
 */
object HiltServicesAccessor {

    /**
     * Resolves [AppServices] from the Hilt singleton graph.
     *
     * @param appContext The **application** context (not an Activity or Service context).
     * @throws IllegalStateException if the Hilt graph has not been initialized yet.
     */
    fun appServices(appContext: Context): AppServices {
        return EntryPoints.get(appContext, AppServicesEntryPoint::class.java).appServices()
    }
}
