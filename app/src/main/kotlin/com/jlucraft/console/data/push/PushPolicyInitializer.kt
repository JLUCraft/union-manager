package com.jlucraft.console.data.push

import com.jlucraft.console.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object PushPolicyInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize(settingsStore: SettingsStore) {
        scope.launch {

            PushNotificationPolicy.enabledEventTypes = settingsStore.getPushEnabledEventTypes()
            PushNotificationPolicy.dndEnabled = settingsStore.isDndEnabled()
            PushNotificationPolicy.dndStartHour = settingsStore.getDndStartHour()
            PushNotificationPolicy.dndEndHour = settingsStore.getDndEndHour()
        }
        scope.launch {
            settingsStore.pushEnabledEventTypesFlow.collectLatest { types ->
                PushNotificationPolicy.enabledEventTypes = types
            }
        }
        scope.launch {
            settingsStore.dndEnabledFlow.collectLatest { enabled ->
                PushNotificationPolicy.dndEnabled = enabled
            }
        }
        scope.launch {
            settingsStore.dndStartHourFlow.collectLatest { hour ->
                PushNotificationPolicy.dndStartHour = hour
            }
        }
        scope.launch {
            settingsStore.dndEndHourFlow.collectLatest { hour ->
                PushNotificationPolicy.dndEndHour = hour
            }
        }
    }
}
