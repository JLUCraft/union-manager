package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.di.ServiceLocator
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class GovernanceUiState(
    val isLoading: Boolean = false,
    val proposals: List<Proposal> = emptyList(),
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val createError: String? = null,
    val selectedProposal: Proposal? = null,
    val isSigning: Boolean = false,
    val signError: String? = null,
    /** Current status filter; null = show all. */
    val statusFilter: String? = null
)

class GovernanceViewModel(
    private val apiService: ApiService,
    private val teeAuth: TeeAuthManager,
    private val authCoordinator: AuthCoordinator
) : ViewModel() {

    private val _uiState = mutableStateOf(GovernanceUiState())
    val uiState: State<GovernanceUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            ServiceLocator.pushService.events.collect { event ->
                when (event.type) {
                    "proposal_created", "proposal_signed", "proposal_executed",
                    "proposal_rejected", "proposal_status_changed" -> refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            apiService.listProposals()
                .onSuccess { proposals ->
                    val filtered = _uiState.value.statusFilter?.let { filter ->
                        proposals.filter { it.status.equals(filter, ignoreCase = true) }
                    } ?: proposals
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        proposals = filtered
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

    fun setStatusFilter(status: String?) {
        _uiState.value = _uiState.value.copy(statusFilter = status)
        refresh()
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, createError = null)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false, createError = null)
    }

    fun showProposalDetail(proposal: Proposal) {
        _uiState.value = _uiState.value.copy(selectedProposal = proposal, signError = null)
    }

    fun dismissProposalDetail() {
        _uiState.value = _uiState.value.copy(selectedProposal = null, signError = null)
    }

    fun createProposal(proposalType: String, payload: JsonObject) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(createError = null)
            val proposer = teeAuth.getPublicKey()
            val authPayload = buildJsonObject {
                put("proposal_type", proposalType)
                put("payload", payload)
                put("proposer", proposer)
            }
            authCoordinator.withAuthenticatedOperation(
                cmdType = "create-proposal",
                payload = authPayload,
                title = "创建提案",
                subtitle = "请验证身份以创建治理提案"
            ) {
                apiService.createProposal(proposalType, payload, proposer)
            }
                .onSuccess { result ->
                    result.getOrThrow()
                    _uiState.value = _uiState.value.copy(showCreateDialog = false)
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        createError = error.message ?: "创建失败"
                    )
                }
        }
    }

    /** Save a proposal as draft without submitting it to peers. */
    fun createDraft(proposalType: String, payload: JsonObject) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(createError = null)
            val proposer = teeAuth.getPublicKey()
            val authPayload = buildJsonObject {
                put("proposal_type", proposalType)
                put("payload", payload)
                put("proposer", proposer)
            }
            authCoordinator.withAuthenticatedOperation(
                cmdType = "create-proposal-draft",
                payload = authPayload,
                title = "创建草案",
                subtitle = "请验证身份以创建提案草案"
            ) {
                apiService.createProposalDraft(proposalType, payload, proposer)
            }
                .onSuccess { result ->
                    result.getOrThrow()
                    _uiState.value = _uiState.value.copy(showCreateDialog = false)
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        createError = error.message ?: "草稿创建失败"
                    )
                }
        }
    }

    /** Submit a drafting proposal to pending, making it visible for signing. */
    fun submitDraft(proposalId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val authPayload = buildJsonObject {
                put("proposal_id", proposalId)
            }
            authCoordinator.withAuthenticatedOperation(
                cmdType = "submit-proposal-draft",
                payload = authPayload,
                title = "提交草案",
                subtitle = "请验证身份以提交提案"
            ) {
                apiService.submitProposalDraft(proposalId)
            }
                .onSuccess { result ->
                    result.getOrThrow()
                    _uiState.value = _uiState.value.copy(selectedProposal = null)
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    /** Veto / reject a pending proposal. */
    fun rejectProposal(proposalId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val authPayload = buildJsonObject {
                put("proposal_id", proposalId)
            }
            authCoordinator.withAuthenticatedOperation(
                cmdType = "reject-proposal",
                payload = authPayload,
                title = "否决提案",
                subtitle = "请验证身份以否决提案"
            ) {
                apiService.rejectProposal(proposalId)
            }
                .onSuccess { result ->
                    result.getOrThrow()
                    _uiState.value = _uiState.value.copy(selectedProposal = null)
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun signSelectedProposal() {
        val proposal = _uiState.value.selectedProposal ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSigning = true, signError = null)

            val fullProposal = apiService.getProposal(proposal.id).getOrElse {
                _uiState.value = _uiState.value.copy(
                    isSigning = false,
                    signError = "获取提案详情失败: ${it.message}"
                )
                return@launch
            }

            val payloadBytes = buildSignablePayload(fullProposal)

            val signature = authCoordinator.signWithBiometric(
                title = "签署提案",
                subtitle = "请验证身份以签署提案: ${proposal.proposalType}",
                message = payloadBytes
            ).getOrElse {
                _uiState.value = _uiState.value.copy(
                    isSigning = false,
                    signError = it.message
                )
                return@launch
            }

            val pubkey = teeAuth.getPublicKey()
            apiService.signProposal(proposal.id, pubkey, signature)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSigning = false,
                        selectedProposal = null
                    )
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSigning = false,
                        signError = error.message ?: "提交签名失败"
                    )
                }
        }
    }

    fun executeProposal(proposalId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val authPayload = buildJsonObject {
                put("proposal_id", proposalId)
            }
            authCoordinator.withAuthenticatedOperation(
                cmdType = "execute-proposal",
                payload = authPayload,
                title = "执行提案",
                subtitle = "请验证身份以执行已批准的提案"
            ) {
                apiService.executeProposal(proposalId)
            }
                .onSuccess { result ->
                    result.getOrThrow()
                    refresh()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    private fun buildSignablePayload(proposal: Proposal): ByteArray {
        // Canonical payload: proposal_id + payload_json_bytes
        val idBytes = proposal.id.toByteArray(Charsets.UTF_8)
        val payloadBytes = proposal.payload.toString().toByteArray(Charsets.UTF_8)
        return idBytes + payloadBytes
    }
}
