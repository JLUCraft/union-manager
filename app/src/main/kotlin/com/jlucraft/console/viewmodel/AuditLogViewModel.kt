package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.model.AuditChainVerification
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class AuditLogUiState(
    val isLoading: Boolean = false,
    val entries: List<AuditEntry> = emptyList(),
    val chainVerification: AuditChainVerification? = null,
    val error: String? = null
)

class AuditLogViewModel(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = mutableStateOf(AuditLogUiState())
    val uiState: State<AuditLogUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.pushService.events.collect { event ->
                when (event.type) {
                    "audit_entry", "audit_created" -> refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val entriesDeferred = async { apiService.listAuditEntries(limit = 100) }
            val verifyDeferred = async { apiService.verifyAuditChain() }

            val entriesResult = entriesDeferred.await()
            val verifyResult = verifyDeferred.await()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                entries = entriesResult.getOrNull() ?: emptyList(),
                error = entriesResult.exceptionOrNull()?.message,
                chainVerification = verifyResult.getOrNull()
            )
        }
    }
}
