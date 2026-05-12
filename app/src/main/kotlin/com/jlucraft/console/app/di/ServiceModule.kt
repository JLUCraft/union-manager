package com.jlucraft.console.app.di

import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.DidResolver
import com.jlucraft.console.data.auth.TeeAuthManager
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
object ServiceModule {

    @Provides
    @Singleton
    fun provideNodeRepository(client: Libp2pClient): NodeRepository =
        NodeRepository(client)

    @Provides
    @Singleton
    fun provideCommandRepository(
        client: Libp2pClient,
        teeAuth: TeeAuthManager,
    ): CommandRepository = CommandRepository(client, teeAuth)

    @Provides
    @Singleton
    fun provideOracleRepository(client: Libp2pClient): OracleRepository =
        OracleRepository(client)

    @Provides
    @Singleton
    fun provideAuthCoordinator(
        client: Libp2pClient,
        teeAuth: TeeAuthManager,
        biometric: BiometricAuthManager,
    ): AuthCoordinator = AuthCoordinator(client, teeAuth, biometric)

    @Provides
    @Singleton
    fun provideDidResolver(client: Libp2pClient): DidResolver =
        DidResolver(client)
}
