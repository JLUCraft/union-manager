package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.model.RevokeDevicePayload
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.TeeCapability
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.PushPreferencesGetAuthPayload
import com.jlucraft.console.data.model.PushPreferencesPutAuthPayload
import com.jlucraft.console.data.remote.PushPreferencesResponse
import com.jlucraft.console.data.remote.UnionPushReceiver
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import com.jlucraft.console.data.repository.NodeRepository
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hasTeeKey: Boolean = false,
    val teeCapability: String = "检测中...",
    val isOnboardingCompleted: Boolean = false,
    val biometricResult: String? = null,
    val distributorInfo: String = "检测中...",
    val devices: List<Device> = emptyList(),
    val devicesLoading: Boolean = false,
    val devicesError: String? = null,
    val revokeSuccess: String? = null,
    // Push preferences
    val pushEnabledEventTypes: Set<String> = SettingsStore.DEFAULT_ENABLED_EVENT_TYPES,
    val dndEnabled: Boolean = false,
    val dndStartHour: Int = 22,
    val dndEndHour: Int = 7,
    val pushPrefsLoading: Boolean = false,
    val pushPrefsError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    val teeAuth: TeeAuthManager,
    private val biometric: BiometricAuthManager,
    private val client: Libp2pClient,
    val repository: NodeRepository,
    val authCoordinator: AuthCoordinator,
) : ViewModel() {

    private val _uiState = mutableStateOf(SettingsUiState())
    val uiState: State<SettingsUiState> = _uiState

    init {
        val capabilityLabel = when (val cap = teeAuth.capability) {
            is TeeCapability.StrongBoxAvailable -> "StrongBox"
            is TeeCapability.TeeOnlyAvailable -> "TEE"
            is TeeCapability.NoHardwareBackedKey -> "无硬件安全能力 (${cap.reason})"
        }
        _uiState.value = _uiState.value.copy(
            hasTeeKey = teeAuth.hasKey(),
            teeCapability = capabilityLabel
        )
        viewModelScope.launch {
            UnionPushReceiver.distributorInfo.collect { info ->
                _uiState.value = _uiState.value.copy(distributorInfo = info)
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isOnboardingCompleted = settingsStore.isOnboardingCompleted()
            )
        }
        loadPushPreferences()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
            _uiState.value = _uiState.value.copy(isOnboardingCompleted = true)
        }
    }

    fun testBiometric() {
        viewModelScope.launch {
            val result = biometric.authenticate()
            _uiState.value = _uiState.value.copy(
                biometricResult = when (result) {
                    is com.jlucraft.console.data.auth.BiometricResult.Success -> "认证成功"
                    is com.jlucraft.console.data.auth.BiometricResult.Failed -> "认证失败"
                    is com.jlucraft.console.data.auth.BiometricResult.Error -> "错误: ${result.message}"
                }
            )
        }
    }

    fun clearBiometricResult() {
        _uiState.value = _uiState.value.copy(biometricResult = null)
    }

    // --- Device management ---

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(devicesLoading = true, devicesError = null)
            client.listDevices()
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(
                        devicesLoading = false,
                        devices = devices
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        devicesLoading = false,
                        devicesError = error.message
                    )
                }
        }
    }

    fun revokeDevice(pubkey: String, reason: String) {
        val revokedBy = teeAuth.getPublicKey()
        performRevocation(
            pubkey = pubkey,
            reason = reason,
            revokedBy = revokedBy,
            cmdType = "revoke-device",
            title = "吊销设备",
            subtitle = "请验证身份以吊销该设备",
            successMessage = "设备已吊销",
            revokeCall = { client.revokeDevice(pubkey, reason, revokedBy) }
        )
    }

    fun emergencyRevokeDevice(pubkey: String, reason: String) {
        val revokedBy = teeAuth.getPublicKey()
        performRevocation(
            pubkey = pubkey,
            reason = reason,
            revokedBy = revokedBy,
            cmdType = "emergency-revoke-device",
            title = "紧急吊销设备",
            subtitle = "请验证身份以紧急吊销该设备",
            successMessage = "设备已紧急吊销",
            revokeCall = { client.emergencyRevokeDevice(pubkey, reason, revokedBy) }
        )
    }

    private fun performRevocation(
        pubkey: String,
        reason: String,
        revokedBy: String,
        cmdType: String,
        title: String,
        subtitle: String,
        successMessage: String,
        revokeCall: suspend () -> Result<Device>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(devicesLoading = true, devicesError = null, revokeSuccess = null)

            val payload = RevokeDevicePayload(pubkey, reason, revokedBy)
            val wrappedResult: Result<Result<Device>> = authCoordinator.withAuthenticatedOperation(
                cmdType = cmdType,
                payload = payload,
                title = title,
                subtitle = subtitle,
                operation = revokeCall
            )

            if (wrappedResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    devicesLoading = false,
                    devicesError = wrappedResult.exceptionOrNull()?.message
                )
                return@launch
            }

            val result: Result<Device> = wrappedResult.getOrThrow()
            result
                .onSuccess {
                    _uiState.value = _uiState.value.copy(revokeSuccess = successMessage)
                    loadDevices()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        devicesLoading = false,
                        devicesError = error.message
                    )
                }
        }
    }

    fun clearRevokeSuccess() {
        _uiState.value = _uiState.value.copy(revokeSuccess = null)
    }

    // --- Push preferences ---

    fun loadPushPreferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pushPrefsLoading = true, pushPrefsError = null)
            try {
                _uiState.value = _uiState.value.copy(
                    pushEnabledEventTypes = settingsStore.getPushEnabledEventTypes(),
                    dndEnabled = settingsStore.isDndEnabled(),
                    dndStartHour = settingsStore.getDndStartHour(),
                    dndEndHour = settingsStore.getDndEndHour(),
                    pushPrefsLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    pushPrefsLoading = false,
                    pushPrefsError = e.message
                )
            }
            syncPushPreferencesFromServer()
        }
    }

    private fun syncPushPreferencesFromServer() {
        viewModelScope.launch {
            val payload = PushPreferencesGetAuthPayload
            val wrappedResult: Result<Result<PushPreferencesResponse>> = authCoordinator.withAuthenticatedOperation(
                cmdType = "push-preferences-get",
                payload = payload,
                title = "推送偏好",
                subtitle = "请验证身份以同步推送偏好"
            ) {
                repository.getPushPreferences()
            }

            if (wrappedResult.isFailure) return@launch

            val result: Result<PushPreferencesResponse> = wrappedResult.getOrThrow()
            result
                .onSuccess { prefs ->
                    _uiState.value = _uiState.value.copy(
                        pushEnabledEventTypes = prefs.enabledEventTypes.toSet(),
                        dndEnabled = prefs.dndEnabled,
                        dndStartHour = prefs.dndStartHour,
                        dndEndHour = prefs.dndEndHour
                    )
                    settingsStore.setPushEnabledEventTypes(prefs.enabledEventTypes.toSet())
                    settingsStore.setDndEnabled(prefs.dndEnabled)
                    settingsStore.setDndStartHour(prefs.dndStartHour)
                    settingsStore.setDndEndHour(prefs.dndEndHour)
                }
                .onFailure { /* silently ignore server fetch failures */ }
        }
    }

    fun togglePushEventType(eventType: String) {
        val current = _uiState.value.pushEnabledEventTypes.toMutableSet()
        if (current.contains(eventType)) {
            if (SettingsStore.FORCE_ENABLED_EVENT_TYPES.contains(eventType)) return
            current.remove(eventType)
        } else {
            current.add(eventType)
        }
        _uiState.value = _uiState.value.copy(pushEnabledEventTypes = current)
        persistPushPreferences()
    }

    fun setDndEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dndEnabled = enabled)
        persistPushPreferences()
    }

    fun setDndStartHour(hour: Int) {
        _uiState.value = _uiState.value.copy(dndStartHour = hour.coerceIn(0, 23))
        persistPushPreferences()
    }

    fun setDndEndHour(hour: Int) {
        _uiState.value = _uiState.value.copy(dndEndHour = hour.coerceIn(0, 23))
        persistPushPreferences()
    }

    private fun persistPushPreferences() {
        viewModelScope.launch {
            try {
                settingsStore.setPushEnabledEventTypes(_uiState.value.pushEnabledEventTypes)
                settingsStore.setDndEnabled(_uiState.value.dndEnabled)
                settingsStore.setDndStartHour(_uiState.value.dndStartHour)
                settingsStore.setDndEndHour(_uiState.value.dndEndHour)

                val payload = PushPreferencesPutAuthPayload(
                    enabledEventTypes = _uiState.value.pushEnabledEventTypes.toList(),
                    dndEnabled = _uiState.value.dndEnabled,
                    dndStartHour = _uiState.value.dndStartHour,
                    dndEndHour = _uiState.value.dndEndHour
                )

                val wrappedResult: Result<Result<PushPreferencesResponse>> = authCoordinator.withAuthenticatedOperation(
                    cmdType = "push-preferences-put",
                    payload = payload,
                    title = "推送偏好",
                    subtitle = "请验证身份以保存推送偏好"
                ) {
                    repository.updatePushPreferences(
                        _uiState.value.pushEnabledEventTypes,
                        _uiState.value.dndEnabled,
                        _uiState.value.dndStartHour,
                        _uiState.value.dndEndHour
                    )
                }

                if (wrappedResult.isFailure) return@launch

                val result: Result<PushPreferencesResponse> = wrappedResult.getOrThrow()
                result
                    .onFailure { /* silently ignore server errors if local persisted */ }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(pushPrefsError = e.message)
            }
        }
    }
}
