package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.NetworkSnapshot
import com.jlucraft.console.data.repository.NodeRepository
import com.jlucraft.console.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val health: ClusterHealthResponse? = null,
    val network: NetworkSnapshot? = null,
    val nodeScores: List<NodeScore> = emptyList(),
    val instances: List<Instance> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val error: String? = null
)

class DashboardViewModel(
    private val repository: NodeRepository
) : ViewModel() {

    private val _uiState = mutableStateOf(DashboardUiState())
    val uiState: State<DashboardUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.pushService.events.collect { event ->
                when (event.type) {
                    "cluster_health", "alerts", "proposals" -> refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val healthDeferred = async { repository.getClusterHealth() }
            val networkDeferred = async { repository.getNetworkSnapshot() }
            val scoresDeferred = async { repository.listNodeScores() }
            val instancesDeferred = async { repository.getInstances() }
            val alertsDeferred = async { repository.listAlerts(includeResolved = false) }

            val healthResult = healthDeferred.await()
            val networkResult = networkDeferred.await()
            val scoresResult = scoresDeferred.await()
            val instancesResult = instancesDeferred.await()
            val alertsResult = alertsDeferred.await()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                health = healthResult.getOrNull(),
                network = networkResult.getOrNull(),
                nodeScores = scoresResult.getOrNull() ?: emptyList(),
                instances = instancesResult.getOrNull() ?: emptyList(),
                alerts = alertsResult.getOrNull() ?: emptyList(),
                error = healthResult.exceptionOrNull()?.message
                    ?: networkResult.exceptionOrNull()?.message
                    ?: scoresResult.exceptionOrNull()?.message
                    ?: instancesResult.exceptionOrNull()?.message
                    ?: alertsResult.exceptionOrNull()?.message
            )
        }
    }
}
