package com.jlucraft.console.app.di

import android.content.Context
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import com.jlucraft.console.data.remote.libp2p.Libp2pConfig
import com.jlucraft.console.data.remote.libp2p.Libp2pTransport
import com.jlucraft.console.data.remote.libp2p.Libp2pTransportImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


 *
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun provideTeeAuthManager(@ApplicationContext context: Context): TeeAuthManager =
        TeeAuthManager(context)

    @Provides
    @Singleton
    fun provideBiometricAuthManager(@ApplicationContext context: Context): BiometricAuthManager =
        BiometricAuthManager(context)

    @Provides
    @Singleton
    fun providePushService(): PushService =
        PushService()


    @Provides
    @Singleton
    fun provideLibp2pTransport(settingsStore: SettingsStore): Libp2pTransport =
        Libp2pTransportImpl(settingsStore)


    @Provides
    @Singleton
    fun provideLibp2pConfig(): Libp2pConfig =
        Libp2pConfig(
            listenAddresses = listOf("/ip4/0.0.0.0/tcp/0"),
            bootstrapPeers = emptyList(),
            peerIdentityProto = null,
            protocolPrefix = "/jlucraft/control/1.0.0",
        )

    @Provides
    @Singleton
    fun provideLibp2pClient(
        transport: Libp2pTransport,
        config: Libp2pConfig,
    ): Libp2pClient =
        Libp2pClient(
            transport = transport,
            controlProtocolId = config.protocolPrefix,
        )
}
