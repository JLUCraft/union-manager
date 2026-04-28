package com.jlucraft.console.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jlucraft.console.di.ServiceLocator

class ViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            DashboardViewModel::class.java -> DashboardViewModel(ServiceLocator.nodeRepository)
            InstancesViewModel::class.java -> InstancesViewModel(ServiceLocator.nodeRepository, ServiceLocator.authCoordinator)
            LeagueViewModel::class.java -> LeagueViewModel(ServiceLocator.nodeRepository, ServiceLocator.teeAuthManager)
            GovernanceViewModel::class.java -> GovernanceViewModel(ServiceLocator.apiService, ServiceLocator.teeAuthManager, ServiceLocator.authCoordinator)
            AuditLogViewModel::class.java -> AuditLogViewModel(ServiceLocator.apiService)
            SettingsViewModel::class.java -> SettingsViewModel(ServiceLocator.settingsStore, ServiceLocator.teeAuthManager, ServiceLocator.biometricAuthManager)
            AuthViewModel::class.java -> AuthViewModel(ServiceLocator.apiService, ServiceLocator.teeAuthManager)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
