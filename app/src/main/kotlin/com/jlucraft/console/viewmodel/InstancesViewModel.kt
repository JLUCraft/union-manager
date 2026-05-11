package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.domain.auth.AuthenticatedOperationUseCase
import com.jlucraft.console.data.model.AdmissionPolicy
import com.jlucraft.console.data.model.AuthPayload
import com.jlucraft.console.data.model.BatchOperationPayload
import com.jlucraft.console.data.model.CreateInstancePayload
import com.jlucraft.console.data.model.CreateInstanceRequest
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.InstanceRuntimeSpec
import com.jlucraft.console.data.model.MigrateInstancePayload
import com.jlucraft.console.data.model.ResourceRequest
import com.jlucraft.console.data.model.StartInstancePayload
import com.jlucraft.console.data.model.StopInstancePayload
import com.jlucraft.console.data.model.StopInstanceWithReasonPayload
import com.jlucraft.console.data.model.UpdateInstanceConfigPayload
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstancesUiState(
    val isLoading: Boolean = false,
    val instances: List<Instance> = emptyList(),
    val error: String? = null,
    val isAuthing: Boolean = false,
    val authError: String? = null,
    val operationError: String? = null,
    val selectedInstance: Instance? = null,
    val logs: List<String> = emptyList(),
    val logsLoading: Boolean = false,
    val logsError: String? = null,
    // Batch operation state
    val isBatchMode: Boolean = false,
    val selectedInstanceIds: Set<String> = emptySet(),
    val batchOperationInProgress: Boolean = false,
    val batchOperationType: String? = null,
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val batchErrors: List<String> = emptyList(),
    val batchAborted: Boolean = false,
    // Config update state
    val showConfigDialog: Boolean = false,
    val configUpdateError: String? = null
)

@HiltViewModel
class InstancesViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val authCoordinator: AuthCoordinator,
    pushService: PushService,
) : ViewModel() {

    private val authenticatedOperation = AuthenticatedOperationUseCase(authCoordinator)

    private val _uiState = mutableStateOf(InstancesUiState())
    val uiState: State<InstancesUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            pushService.events.collect { event ->
                when (event.type) {
                    "instance_started", "instance_stopped", "instance_crash",
                    "instance_created", "instance_deleted", "instance_migrated",
                    "instance_updated" -> refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = client.getInstances()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                instances = result.getOrNull() ?: emptyList(),
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun startInstance(instanceId: String) {
        performAuthenticatedOperation(
            cmdType = "start-instance",
            payload = StartInstancePayload(instanceId),
            title = "启动实例",
            subtitle = "请验证身份以启动实例",
            operation = { client.startInstance(instanceId) },
            failureMessage = "启动失败"
        )
    }

    fun stopInstance(instanceId: String, reason: String? = null) {
        performAuthenticatedOperation(
            cmdType = "stop-instance",
            payload = if (reason != null) StopInstanceWithReasonPayload(instanceId, reason) else StopInstancePayload(instanceId),
            title = "停止实例",
            subtitle = "请验证身份以停止实例" + (reason?.let { "（原因: $it）" } ?: ""),
            operation = { if (reason != null) client.stopInstanceWithReason(instanceId, reason) else client.stopInstance(instanceId) },
            failureMessage = "停止失败"
        )
    }

    fun createInstance(
        name: String,
        kind: String,
        image: String,
        owner: String,
        club: String,
        cpuCores: Int,
        memoryGb: Int,
        diskGb: Int,
        admission: AdmissionPolicy = AdmissionPolicy(mode = "public")
    ) {
        val request = CreateInstanceRequest(
            name = name,
            kind = kind,
            owner = owner,
            club = club,
            runtime = InstanceRuntimeSpec(
                image = image
            ),
            resources = ResourceRequest(
                cpuCores = cpuCores,
                memoryGb = memoryGb,
                diskGb = diskGb
            ),
            admission = admission
        )
        performAuthenticatedOperation(
            cmdType = "create-instance",
            payload = CreateInstancePayload(name, kind, owner, club),
            title = "创建实例",
            subtitle = "请验证身份以创建实例",
            operation = { client.createInstance(request) },
            failureMessage = "创建失败"
        )
    }

    fun migrateInstance(instanceId: String, targetHost: String) {
        performAuthenticatedOperation(
            cmdType = "migrate-instance",
            payload = MigrateInstancePayload(instanceId, targetHost),
            title = "迁移实例",
            subtitle = "请验证身份以迁移实例",
            operation = { client.migrateInstance(instanceId, targetHost) },
            failureMessage = "迁移失败"
        )
    }

    fun autoMigrateInstance(instanceId: String) {
        performAuthenticatedOperation(
            cmdType = "migrate-instance",
            payload = MigrateInstancePayload(instanceId),
            title = "自动迁移实例",
            subtitle = "请验证身份以触发自动迁移",
            operation = { client.migrateInstance(instanceId, null) },
            failureMessage = "迁移失败"
        )
    }

    fun updateInstanceConfig(instanceId: String, config: Map<String, String>) {
        performAuthenticatedOperation(
            cmdType = "update-instance-config",
            payload = UpdateInstanceConfigPayload(instanceId, config),
            title = "更新实例配置",
            subtitle = "请验证身份以更新实例配置",
            operation = { client.updateInstanceConfig(instanceId, config) },
            failureMessage = "配置更新失败"
        )
    }

    fun showConfigDialog() {
        _uiState.value = _uiState.value.copy(showConfigDialog = true, configUpdateError = null)
    }

    fun dismissConfigDialog() {
        _uiState.value = _uiState.value.copy(showConfigDialog = false, configUpdateError = null)
    }

    private fun <T> performAuthenticatedOperation(
        cmdType: String,
        payload: AuthPayload,
        title: String,
        subtitle: String,
        operation: suspend () -> Result<T>,
        failureMessage: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthing = true, authError = null, operationError = null)
            val result = authenticatedOperation.execute(cmdType, payload, title, subtitle, operation, failureMessage)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isAuthing = false, operationError = null)
                refresh()
            } else {
                val error = result.exceptionOrNull()?.message ?: failureMessage
                _uiState.value = _uiState.value.copy(
                    isAuthing = false,
                    authError = if (error.contains("认证")) error else null,
                    operationError = if (!error.contains("认证")) error else null
                )
            }
        }
    }

    fun selectInstance(instance: Instance) {
        _uiState.value = _uiState.value.copy(selectedInstance = instance)
        fetchLogs(instance.id)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedInstance = null,
            logs = emptyList(),
            logsError = null,
            authError = null
        )
    }

    fun clearAuthError() {
        _uiState.value = _uiState.value.copy(authError = null)
    }

    fun clearOperationError() {
        _uiState.value = _uiState.value.copy(operationError = null)
    }

    // --- Batch operations ---

    fun toggleBatchMode() {
        _uiState.value = _uiState.value.copy(
            isBatchMode = !_uiState.value.isBatchMode,
            selectedInstanceIds = emptySet()
        )
    }

    fun exitBatchMode() {
        _uiState.value = _uiState.value.copy(
            isBatchMode = false,
            selectedInstanceIds = emptySet()
        )
    }

    fun toggleInstanceSelection(instanceId: String) {
        val current = _uiState.value.selectedInstanceIds
        if (current.contains(instanceId)) {
            _uiState.value = _uiState.value.copy(selectedInstanceIds = current - instanceId)
        } else {
            if (current.size >= 20) {
                _uiState.value = _uiState.value.copy(
                    operationError = "批量操作最多选择 20 个实例"
                )
                return
            }
            _uiState.value = _uiState.value.copy(selectedInstanceIds = current + instanceId)
        }
    }

    fun selectAllInstances() {
        val allIds = _uiState.value.instances.map { it.id }.take(20).toSet()
        _uiState.value = _uiState.value.copy(selectedInstanceIds = allIds)
    }

    fun abortBatchOperation() {
        _uiState.value = _uiState.value.copy(batchAborted = true)
    }

    fun clearBatchErrors() {
        _uiState.value = _uiState.value.copy(batchErrors = emptyList())
    }

    fun batchStart() {
        performBatchOperation(
            operationType = "start",
            title = "批量启动实例",
            subtitle = "请验证身份以批量启动 ${ _uiState.value.selectedInstanceIds.size } 个实例",
            cmdType = "batch-start",
            operation = { instanceId -> client.startInstance(instanceId) }
        )
    }

    fun batchStop() {
        performBatchOperation(
            operationType = "stop",
            title = "批量停止实例",
            subtitle = "请验证身份以批量停止 ${ _uiState.value.selectedInstanceIds.size } 个实例",
            cmdType = "batch-stop",
            operation = { instanceId -> client.stopInstance(instanceId) }
        )
    }

    fun batchMigrate(targetHost: String) {
        performBatchOperation(
            operationType = "migrate",
            title = "批量迁移实例",
            subtitle = "请验证身份以批量迁移 ${ _uiState.value.selectedInstanceIds.size } 个实例",
            cmdType = "batch-migrate",
            operation = { instanceId -> client.migrateInstance(instanceId, targetHost) }
        )
    }

    private fun performBatchOperation(
        operationType: String,
        title: String,
        subtitle: String,
        cmdType: String,
        operation: suspend (String) -> Result<Unit>
    ) {
        val instanceIds = _uiState.value.selectedInstanceIds.toList()
        if (instanceIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                batchOperationInProgress = true,
                batchOperationType = operationType,
                batchProgress = 0,
                batchTotal = instanceIds.size,
                batchErrors = emptyList(),
                batchAborted = false
            )

            val batchPayload = BatchOperationPayload(
                instanceIds = instanceIds.joinToString(","),
                count = instanceIds.size,
                operation = operationType
            )

            val authResult = authCoordinator.withAuthenticatedOperation(cmdType, batchPayload, title, subtitle) {
                val errors = mutableListOf<String>()
                for ((index, instanceId) in instanceIds.withIndex()) {
                    if (_uiState.value.batchAborted) {
                        errors.add("操作已中止 (${index}/${instanceIds.size})")
                        break
                    }

                    _uiState.value = _uiState.value.copy(batchProgress = index + 1)

                    try {
                        val result = operation(instanceId)
                        if (result.isFailure) {
                            errors.add("实例 $instanceId: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        errors.add("实例 $instanceId: ${e.message}")
                    }
                }
                errors
            }

            if (authResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    batchOperationInProgress = false,
                    authError = authResult.exceptionOrNull()?.message
                )
                return@launch
            }

            val errors = authResult.getOrThrow()

            _uiState.value = _uiState.value.copy(
                batchOperationInProgress = false,
                batchErrors = errors,
                isBatchMode = false,
                selectedInstanceIds = emptySet()
            )
            refresh()
        }
    }

    fun fetchLogs(instanceId: String, tail: Int = 100) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(logsLoading = true, logsError = null)
            val result = client.getInstanceLogs(instanceId, tail)
            _uiState.value = _uiState.value.copy(
                logsLoading = false,
                logs = result.getOrNull() ?: emptyList(),
                logsError = result.exceptionOrNull()?.message
            )
        }
    }
}
