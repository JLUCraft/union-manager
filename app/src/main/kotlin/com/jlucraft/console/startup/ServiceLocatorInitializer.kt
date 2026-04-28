package com.jlucraft.console.startup

import android.content.Context
import androidx.startup.Initializer
import com.jlucraft.console.di.ServiceLocator

class ServiceLocatorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ServiceLocator.initialize(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
