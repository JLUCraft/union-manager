package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.BiometricAuthManager
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.remote.UnionPushReceiver
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = SettingsStore.DEFAULT_SERVER_URL,
    val hasTeeKey: Boolean = false,
    val biometricResult: String? = null,
    val distributorInfo: String = "检测中...",
    val devices: List<Device> = emptyList(),
    val devicesLoading: Boolean = false,
    val devicesError: String? = null,
    val revokeSuccess: String? = null
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    val teeAuth: TeeAuthManager,
    private val biometric: BiometricAuthManager
) : ViewModel() {

    val repository: NodeRepository = ServiceLocator.nodeRepository
    val authCoordinator: AuthCoordinator = ServiceLocator.authCoordinator

    private val _uiState = mutableStateOf(SettingsUiState())
    val uiState: State<SettingsUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            serverUrl = settingsStore.currentServerUrl,
            hasTeeKey = teeAuth.hasKey()
        )
        viewModelScope.launch {
            UnionPushReceiver.distributorInfo.collect { info ->
                _uiState.value = _uiState.value.copy(distributorInfo = info)
            }
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            ServiceLocator.setServerUrl(url)
            _uiState.value = _uiState.value.copy(serverUrl = settingsStore.currentServerUrl)
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
            repository.listDevices()
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
        performRevocation(
            pubkey = pubkey,
            reason = reason,
            cmdType = "revoke-device",
            title = "吊销设备",
            subtitle = "请验证身份以吊销该设备",
            successMessage = "设备已吊销",
            revokeCall = { repository.revokeDevice(pubkey, reason, teeAuth.getPublicKey()) }
        )
    }

    fun emergencyRevokeDevice(pubkey: String, reason: String) {
        performRevocation(
            pubkey = pubkey,
            reason = reason,
            cmdType = "emergency-revoke-device",
            title = "紧急吊销设备",
            subtitle = "请验证身份以紧急吊销该设备",
            successMessage = "设备已紧急吊销",
            revokeCall = { repository.emergencyRevokeDevice(pubkey, reason, teeAuth.getPublicKey()) }
        )
    }

    private fun performRevocation(
        pubkey: String,
        reason: String,
        cmdType: String,
        title: String,
        subtitle: String,
        successMessage: String,
        revokeCall: suspend () -> Result<com.jlucraft.console.data.model.Device>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(devicesLoading = true, devicesError = null, revokeSuccess = null)
            val payload = kotlinx.serialization.json.JsonObject(mapOf(
                "target_pubkey" to kotlinx.serialization.json.JsonPrimitive(pubkey),
                "reason" to kotlinx.serialization.json.JsonPrimitive(reason)
            ))
            val authResult = authCoordinator.authenticateForOperation(
                cmdType = cmdType,
                payload = payload,
                title = title,
                subtitle = subtitle
            )
            if (authResult.isSuccess) {
                try {
                    revokeCall()
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
                } finally {
                    authCoordinator.clearAuth()
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    devicesLoading = false,
                    devicesError = authResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearRevokeSuccess() {
        _uiState.value = _uiState.value.copy(revokeSuccess = null)
    }
}
