package com.jlucraft.console.app.di

import android.content.Context
import com.jlucraft.console.app.AppServices
import dagger.hilt.EntryPoints


 *
object HiltServicesAccessor {


     *
    fun appServices(appContext: Context): AppServices {
        return EntryPoints.get(appContext, AppServicesEntryPoint::class.java).appServices()
    }
}
