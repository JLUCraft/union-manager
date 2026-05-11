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

/**
 * Hilt module providing core application-level singletons:
 * data stores, libp2p client/transport, TEE/biometric managers, and push service.
 *
 * There is no longer a server URL or baseUrl configuration.
 * The union-manager is a libp2p peer that connects via bootstrap peers.
 */
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

    /**
     * The libp2p transport using jvm-libp2p. Loads or generates an Ed25519
     * key pair via [SettingsStore] and connects to the bootstrap peers.
     */
    @Provides
    @Singleton
    fun provideLibp2pTransport(settingsStore: SettingsStore): Libp2pTransport =
        Libp2pTransportImpl(settingsStore)

    /**
     * Libp2p peer configuration. Bootstrap peers are loaded from onboarding;
     * key identity is managed by [Libp2pTransportImpl] via SettingsStore.
     */
    @Provides
    @Singleton
    fun provideLibp2pConfig(): Libp2pConfig =
        Libp2pConfig(
            listenAddresses = listOf("/ip4/0.0.0.0/tcp/0"),
            bootstrapPeers = emptyList(), // Populated via setBootstrapPeers() during onboarding
            peerIdentityProto = null,     // Managed internally by Libp2pTransportImpl
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
            eventProtocolId = "/jlucraft/events/1.0.0",
        )
}
