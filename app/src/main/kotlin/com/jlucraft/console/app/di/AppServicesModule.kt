package com.jlucraft.console.app.di

import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.DidResolver
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import com.jlucraft.console.data.repository.CommandRepository
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.data.repository.OracleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppServicesModule {

    @Provides
    @Singleton
    fun provideAppServices(
        settingsStore: SettingsStore,
        client: Libp2pClient,
        nodeRepository: NodeRepository,
        teeAuthManager: TeeAuthManager,
        biometricAuthManager: BiometricAuthManager,
        authCoordinator: AuthCoordinator,
        pushService: PushService,
        didResolver: DidResolver,
        commandRepository: CommandRepository,
        oracleRepository: OracleRepository,
    ): AppServices = AppServices(
        settingsStore = settingsStore,
        client = client,
        nodeRepository = nodeRepository,
        teeAuthManager = teeAuthManager,
        biometricAuthManager = biometricAuthManager,
        authCoordinator = authCoordinator,
        pushService = pushService,
        didResolver = didResolver,
        commandRepository = commandRepository,
        oracleRepository = oracleRepository,
    )
}
