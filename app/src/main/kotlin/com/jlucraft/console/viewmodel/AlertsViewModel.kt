package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.model.AcknowledgeAlertPayload
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.ResolveAlertPayload
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch

data class AlertsUiState(
    val isLoading: Boolean = false,
    val alerts: List<Alert> = emptyList(),
    val severityFilter: String? = null,
    val includeResolved: Boolean = false,
    val error: String? = null,
    val actionLoading: String? = null,
    val actionError: String? = null,
    val actionSuccess: String? = null
)

/**
 * ViewModel for alert list with severity filters and acknowledge/resolve actions.
 *
 * Routes:
 *  - GET /v1/alerts?severity=&include_resolved=
 *  - POST /v1/alerts/{id}/acknowledge
 *  - POST /v1/alerts/{id}/resolve
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val authCoordinator: AuthCoordinator
) : ViewModel() {

    private val _uiState = mutableStateOf(AlertsUiState())
    val uiState: State<AlertsUiState> = _uiState

    init {
        refresh()
    }

    fun setSeverityFilter(severity: String?) {
        _uiState.value = _uiState.value.copy(severityFilter = severity)
        refresh()
    }

    fun setIncludeResolved(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeResolved = include)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.listAlerts(
                severity = _uiState.value.severityFilter,
                includeResolved = _uiState.value.includeResolved
            )
                .onSuccess { alerts ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        alerts = alerts
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

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actionLoading = alertId,
                actionError = null,
                actionSuccess = null
            )
            val result = authCoordinator.withAuthenticatedOperation(
                "acknowledge-alert", AcknowledgeAlertPayload(alertId), "确认警报", "请验证身份以确认警报"
            ) {
                client.acknowledgeAlert(alertId)
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    actionLoading = null,
                    actionError = result.exceptionOrNull()?.message
                )
                return@launch
            }
            result.getOrThrow()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        actionLoading = null,
                        actionSuccess = "alert_acknowledged"
                    )
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        actionLoading = null,
                        actionError = error.message
                    )
                }
        }
    }

    fun resolveAlert(alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                actionLoading = alertId,
                actionError = null,
                actionSuccess = null
            )
            val result = authCoordinator.withAuthenticatedOperation(
                "resolve-alert", ResolveAlertPayload(alertId), "解决警报", "请验证身份以解决警报"
            ) {
                client.resolveAlert(alertId)
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    actionLoading = null,
                    actionError = result.exceptionOrNull()?.message
                )
                return@launch
            }
            result.getOrThrow()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        actionLoading = null,
                        actionSuccess = "alert_resolved"
                    )
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        actionLoading = null,
                        actionError = error.message
                    )
                }
        }
    }

    fun clearActionState() {
        _uiState.value = _uiState.value.copy(actionError = null, actionSuccess = null)
    }
}
