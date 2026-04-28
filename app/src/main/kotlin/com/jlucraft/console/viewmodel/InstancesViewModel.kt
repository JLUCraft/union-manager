package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    val batchAborted: Boolean = false
)

class InstancesViewModel(
    private val repository: NodeRepository,
    private val authCoordinator: AuthCoordinator
) : ViewModel() {

    private val _uiState = mutableStateOf(InstancesUiState())
    val uiState: State<InstancesUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.pushService.events.collect { event ->
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
            val result = repository.getInstances()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                instances = result.getOrNull() ?: emptyList(),
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun startInstance(instanceId: String) {
        performAuthenticatedOperation(
            cmdType = "instance-start",
            payload = JsonObject(mapOf("instance_id" to JsonPrimitive(instanceId))),
            title = "启动实例",
            subtitle = "请验证身份以启动实例",
            operation = { repository.startInstance(instanceId) },
            successMessage = null,
            failureMessage = "启动失败"
        )
    }

    fun stopInstance(instanceId: String) {
        performAuthenticatedOperation(
            cmdType = "instance-stop",
            payload = JsonObject(mapOf("instance_id" to JsonPrimitive(instanceId))),
            title = "停止实例",
            subtitle = "请验证身份以停止实例",
            operation = { repository.stopInstance(instanceId) },
            successMessage = null,
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
        admissionMode: String = "public"
    ) {
        val request = com.jlucraft.console.data.remote.CreateInstanceRequest(
            name = name,
            kind = kind,
            owner = owner,
            club = club,
            runtime = com.jlucraft.console.data.remote.InstanceRuntimeSpec(
                image = image
            ),
            resources = com.jlucraft.console.data.remote.ResourceRequest(
                cpuCores = cpuCores,
                memoryGb = memoryGb,
                diskGb = diskGb
            ),
            admission = com.jlucraft.console.data.remote.AdmissionPolicy(
                mode = admissionMode
            )
        )
        performAuthenticatedOperation(
            cmdType = "instance-create",
            payload = JsonObject(mapOf(
                "name" to JsonPrimitive(name),
                "kind" to JsonPrimitive(kind),
                "owner" to JsonPrimitive(owner),
                "club" to JsonPrimitive(club)
            )),
            title = "创建实例",
            subtitle = "请验证身份以创建实例",
            operation = { repository.createInstance(request) },
            successMessage = null,
            failureMessage = "创建失败"
        )
    }

    fun migrateInstance(instanceId: String, targetHost: String) {
        performAuthenticatedOperation(
            cmdType = "instance-migrate",
            payload = JsonObject(mapOf(
                "instance_id" to JsonPrimitive(instanceId),
                "target_host" to JsonPrimitive(targetHost)
            )),
            title = "迁移实例",
            subtitle = "请验证身份以迁移实例",
            operation = { repository.migrateInstance(instanceId, targetHost) },
            successMessage = null,
            failureMessage = "迁移失败"
        )
    }

    private fun <T> performAuthenticatedOperation(
        cmdType: String,
        payload: JsonObject,
        title: String,
        subtitle: String,
        operation: suspend () -> Result<T>,
        successMessage: String?,
        failureMessage: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthing = true, authError = null, operationError = null)
            val authResult = authCoordinator.authenticateForOperation(cmdType, payload, title, subtitle)
            val result: Result<T> = if (authResult.isFailure) {
                Result.failure(authResult.exceptionOrNull()!!)
            } else {
                try {
                    operation()
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    authCoordinator.clearAuth()
                }
            }
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
            cmdType = "instance-batch-start",
            operation = { instanceId -> repository.startInstance(instanceId) }
        )
    }

    fun batchStop() {
        performBatchOperation(
            operationType = "stop",
            title = "批量停止实例",
            subtitle = "请验证身份以批量停止 ${ _uiState.value.selectedInstanceIds.size } 个实例",
            cmdType = "instance-batch-stop",
            operation = { instanceId -> repository.stopInstance(instanceId) }
        )
    }

    fun batchMigrate(targetHost: String) {
        performBatchOperation(
            operationType = "migrate",
            title = "批量迁移实例",
            subtitle = "请验证身份以批量迁移 ${ _uiState.value.selectedInstanceIds.size } 个实例",
            cmdType = "instance-batch-migrate",
            operation = { instanceId -> repository.migrateInstance(instanceId, targetHost) }
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

            val batchPayload = JsonObject(mapOf(
                "instance_ids" to JsonPrimitive(instanceIds.joinToString(",")),
                "count" to JsonPrimitive(instanceIds.size),
                "operation" to JsonPrimitive(operationType)
            ))

            val authResult = authCoordinator.authenticateForOperation(cmdType, batchPayload, title, subtitle)
            if (authResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    batchOperationInProgress = false,
                    authError = authResult.exceptionOrNull()?.message
                )
                return@launch
            }

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

            authCoordinator.clearAuth()

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
            val result = repository.getInstanceLogs(instanceId, tail)
            _uiState.value = _uiState.value.copy(
                logsLoading = false,
                logs = result.getOrNull() ?: emptyList(),
                logsError = result.exceptionOrNull()?.message
            )
        }
    }
}
