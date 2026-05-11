package com.jlucraft.console.app

import android.app.Activity
import androidx.fragment.app.FragmentActivity
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compatibility facade for UI screens that receive [AppServices].
 * All instances are injected by Hilt; no manual construction.
 *
 * There is no server URL — the peer connects via configured bootstrap peers.
 */
@Singleton
class AppServices @Inject constructor(
    val settingsStore: SettingsStore,
    val client: Libp2pClient,
    val nodeRepository: NodeRepository,
    val teeAuthManager: TeeAuthManager,
    val biometricAuthManager: BiometricAuthManager,
    val authCoordinator: AuthCoordinator,
    val pushService: PushService,
    val didResolver: DidResolver,
    val commandRepository: CommandRepository,
    val oracleRepository: OracleRepository,
) {
    fun attachActivity(activity: Activity) {
        biometricAuthManager.fragmentActivity = activity as? FragmentActivity
    }
}
