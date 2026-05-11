package com.jlucraft.console.app.di

import com.jlucraft.console.app.AppServices
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for non-Hilt-managed classes (e.g., [androidx.startup.Initializer]
 * implementations) to access [AppServices] from the Hilt graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppServicesEntryPoint {
    fun appServices(): AppServices
}
