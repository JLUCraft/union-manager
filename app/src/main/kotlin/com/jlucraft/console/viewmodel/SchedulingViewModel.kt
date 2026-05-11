package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.ReadOnlyDeviceException
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.model.ApplySchedulingConstraintsPayload
import com.jlucraft.console.data.model.ApplySchedulingConstraintsRequest
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.SchedulingConstraints
import com.jlucraft.console.data.model.SchedulingConstraintsResponse
import com.jlucraft.console.data.model.SchedulingPreset
import com.jlucraft.console.data.model.SchedulingSimulation
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch

data class SchedulingUiState(
    val instanceId: String = "",
    val constraints: SchedulingConstraints? = null,
    val preset: SchedulingPreset? = null,
    val simResult: SchedulingSimulation? = null,
    val applyResult: SchedulingConstraintsResponse? = null,
    val isLoading: Boolean = false,
    val isSimulating: Boolean = false,
    val isApplying: Boolean = false,
    val error: String? = null,
    val simError: String? = null,
    val applyError: String? = null,
    val applySuccess: String? = null,
    val allInstances: List<Instance>? = null,
    val instancesLoading: Boolean = false,
    val instancesError: String? = null
)

@HiltViewModel
class SchedulingViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager,
    private val authCoordinator: AuthCoordinator,
) : ViewModel() {

    private val _uiState = mutableStateOf(SchedulingUiState())
    val uiState: State<SchedulingUiState> = _uiState

    fun loadInstances() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(instancesLoading = true, instancesError = null)
            client.getInstances()
                .onSuccess { instances ->
                    _uiState.value = _uiState.value.copy(
                        instancesLoading = false,
                        allInstances = instances
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        instancesLoading = false,
                        instancesError = error.message
                    )
                }
        }
    }

    fun selectInstance(instance: Instance) {
        setInstanceId(instance.id)
    }

    fun setInstanceId(instanceId: String) {
        _uiState.value = _uiState.value.copy(
            instanceId = instanceId,
            constraints = null,
            simResult = null,
            applyResult = null,
            error = null
        )
        if (instanceId.isNotBlank()) {
            loadConstraints(instanceId)
        }
    }

    fun loadConstraints(instanceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.getSchedulingConstraints(instanceId)
                .onSuccess { constraints ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        constraints = constraints
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun updateConstraints(constraints: SchedulingConstraints) {
        _uiState.value = _uiState.value.copy(
            constraints = constraints,
            preset = null
        )
    }

    fun applyPreset(preset: SchedulingPreset) {
        val constraints = preset.toConstraints()
        _uiState.value = _uiState.value.copy(
            constraints = constraints,
            preset = preset
        )
    }

    fun applyConstraints(reason: String) {
        val state = _uiState.value
        val constraints = state.constraints ?: return
        val instanceId = state.instanceId

        if (!teeAuth.isTeeBacked) {
            _uiState.value = _uiState.value.copy(
                applyError = ReadOnlyDeviceException.fromCapability(teeAuth.capability).message
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isApplying = true,
                applyError = null,
                applySuccess = null
            )
            val result = authCoordinator.withAuthenticatedOperation(
                "apply-scheduling-constraints", ApplySchedulingConstraintsPayload(instanceId, reason),
                "应用调度约束", "请验证身份以应用调度约束"
            ) {
                val request = ApplySchedulingConstraintsRequest(
                    instanceId = instanceId,
                    constraints = constraints,
                    reason = reason
                )
                client.applySchedulingConstraints(request)
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isApplying = false,
                    applyError = result.exceptionOrNull()?.message
                )
                return@launch
            }
            result.getOrThrow()
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isApplying = false,
                        applyResult = response,
                        applySuccess = "调度约束已成功应用"
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isApplying = false,
                        applyError = error.message
                    )
                }
        }
    }

    fun simulateScheduling() {
        val state = _uiState.value
        val constraints = state.constraints ?: return
        val instanceId = state.instanceId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSimulating = true, simError = null, simResult = null)
            client.simulateScheduling(instanceId, constraints)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isSimulating = false,
                        simResult = result
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSimulating = false,
                        simError = error.message
                    )
                }
        }
    }

    fun clearApplyState() {
        _uiState.value = _uiState.value.copy(applySuccess = null, applyError = null)
    }
}
