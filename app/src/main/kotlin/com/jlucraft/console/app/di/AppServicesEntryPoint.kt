package com.jlucraft.console.app.di

import com.jlucraft.console.app.AppServices
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppServicesEntryPoint {
    fun appServices(): AppServices
}
