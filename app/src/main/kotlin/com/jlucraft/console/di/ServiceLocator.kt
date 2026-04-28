package com.jlucraft.console.di

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.repository.NodeRepository

object ServiceLocator {
    lateinit var apiService: ApiService
        private set
    lateinit var nodeRepository: NodeRepository
        private set
    lateinit var teeAuthManager: TeeAuthManager
        private set
    lateinit var biometricAuthManager: BiometricAuthManager
        private set
    lateinit var authCoordinator: AuthCoordinator
        private set
    lateinit var pushService: PushService
        private set
    lateinit var settingsStore: SettingsStore
        private set

    fun initialize(context: Context) {
        settingsStore = SettingsStore(context.applicationContext)
        val initialUrl = SettingsStore.DEFAULT_SERVER_URL
        apiService = ApiService(initialUrl)
        nodeRepository = NodeRepository(apiService)
        teeAuthManager = TeeAuthManager(context.applicationContext)
        biometricAuthManager = BiometricAuthManager(context.applicationContext)
        authCoordinator = AuthCoordinator(apiService, teeAuthManager, biometricAuthManager)
        pushService = PushService(initialUrl)
    }

    fun initActivity(activity: FragmentActivity) {
        biometricAuthManager.fragmentActivity = activity
    }

    suspend fun setServerUrl(url: String) {
        settingsStore.setServerUrl(url)
        apiService.setBaseUrl(url)
        pushService.updateServerUrl(url)
    }
}
