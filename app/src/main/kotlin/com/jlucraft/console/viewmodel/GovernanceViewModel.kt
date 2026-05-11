package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.TeeAuthManager
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.domain.auth.AuthenticatedOperationUseCase
import com.jlucraft.console.data.model.CreateProposalAuthPayload
import com.jlucraft.console.data.model.ExecuteProposalAuthPayload
import com.jlucraft.console.data.model.GovernanceEvent
import com.jlucraft.console.data.model.GovernanceEventType
import com.jlucraft.console.data.model.GrantRoleAuthPayload
import com.jlucraft.console.data.model.IssueCredentialAuthPayload
import com.jlucraft.console.data.model.MemberSummary
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ProposalPayload
import com.jlucraft.console.data.model.RejectProposalAuthPayload
import com.jlucraft.console.data.model.RevokeCredentialAuthPayload
import com.jlucraft.console.data.model.SubmitProposalDraftAuthPayload
import com.jlucraft.console.data.model.toJsonString
import com.jlucraft.console.data.remote.PushService
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

enum class StreamStatus { CONNECTING, CONNECTED, RECONNECTING, OFFLINE }

data class GovernanceUiState(
    val isLoading: Boolean = false,
    val proposals: List<Proposal> = emptyList(),
    val members: List<MemberSummary> = emptyList(),
    val membersLoading: Boolean = false,
    val membersError: String? = null,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val createError: String? = null,
    val selectedProposal: Proposal? = null,
    val isSigning: Boolean = false,
    val signError: String? = null,
    val statusFilter: String? = null,
    val activeTab: String = "proposals",
    val showVcResultSheet: Boolean = false,
    val issuedVcJson: String? = null,
    val issuedVc: com.jlucraft.console.data.model.VerifiableCredential? = null,
    val vcVerificationResult: com.jlucraft.console.data.model.VcVerificationResult? = null,
    val streamStatus: StreamStatus = StreamStatus.OFFLINE,
    val lastEventTime: String? = null,
    val lastEvent: GovernanceEvent? = null
)

@HiltViewModel
class GovernanceViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val teeAuth: TeeAuthManager,
    private val authCoordinator: AuthCoordinator,
    pushService: PushService,
) : ViewModel() {

    private val authenticatedOperation = AuthenticatedOperationUseCase(authCoordinator)

    private val vcJsonFormatter = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _uiState = mutableStateOf(GovernanceUiState())
    val uiState: State<GovernanceUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            pushService.events.collect { event ->
                when (event.type) {
                    "proposal_created", "proposal_signed", "proposal_executed",
                    "proposal_rejected", "proposal_status_changed",
                    "member_updated", "member_credential_changed" -> refresh()
                }
            }
        }
        observeGovernanceStream()
    }

    private fun observeGovernanceStream() {
        viewModelScope.launch {
            var attempt = 0
            while (currentCoroutineContext().isActive) {
                _uiState.value = _uiState.value.copy(
                    streamStatus = if (attempt == 0) StreamStatus.CONNECTING else StreamStatus.RECONNECTING
                )

                val completedNormally = kotlin.runCatching {
                    client.subscribeGovernanceEvents()
                        .catch { throw it }
                        .collect { envelope ->
                            attempt = 0
                            _uiState.value = _uiState.value.copy(
                                streamStatus = StreamStatus.CONNECTED,
                                lastEventTime = envelope.timestamp,
                            )
                            parseAndApplyGovernanceEvent(envelope)
                        }
                }.isSuccess

                _uiState.value = _uiState.value.copy(streamStatus = StreamStatus.OFFLINE)

                if (completedNormally) break

                attempt += 1
                delay((attempt.coerceAtMost(5) * 1_000).toLong())
            }
        }
    }

    private fun parseAndApplyGovernanceEvent(envelope: com.jlucraft.console.data.remote.libp2p.EventEnvelope) {
        when {
            envelope.eventType.contains("proposal", ignoreCase = true) ||
            envelope.eventType.contains("member", ignoreCase = true) ||
            envelope.eventType.contains("credential", ignoreCase = true) ||
            envelope.eventType.contains("governance", ignoreCase = true) -> refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            client.listProposals()
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

    fun setActiveTab(tab: String) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        if (tab == "members") loadMembers()
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

    fun createProposal(proposalType: String, payload: ProposalPayload) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(createError = null)
            val proposer = teeAuth.getPublicKey()
            val result = authenticatedOperation.execute(
                cmdType = "create-proposal",
                payload = CreateProposalAuthPayload(proposalType, payload, proposer),
                title = "创建提案",
                subtitle = "请验证身份以创建治理提案",
                operation = { client.createProposal(proposalType, payload, proposer) },
                failureMessage = "创建失败"
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(showCreateDialog = false)
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(createError = result.exceptionOrNull()?.message ?: "创建失败")
            }
        }
    }

    fun submitDraft(proposalId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authenticatedOperation.execute(
                cmdType = "submit-proposal-draft",
                payload = SubmitProposalDraftAuthPayload(proposalId),
                title = "提交草案",
                subtitle = "请验证身份以提交提案",
                operation = { client.submitProposalDraft(proposalId) },
                failureMessage = "提交草案失败"
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(selectedProposal = null)
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun rejectProposal(proposalId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authenticatedOperation.execute(
                cmdType = "reject-proposal",
                payload = RejectProposalAuthPayload(proposalId),
                title = "否决提案",
                subtitle = "请验证身份以否决提案",
                operation = { client.rejectProposal(proposalId) },
                failureMessage = "否决提案失败"
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(selectedProposal = null)
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun signSelectedProposal() {
        val proposal = _uiState.value.selectedProposal ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSigning = true, signError = null)

            val fullProposal = client.getProposal(proposal.id).getOrElse {
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
            client.signProposal(proposal.id, pubkey, signature)
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
            val result = authenticatedOperation.execute(
                cmdType = "execute-proposal",
                payload = ExecuteProposalAuthPayload(proposalId),
                title = "执行提案",
                subtitle = "请验证身份以执行已批准的提案",
                operation = { client.executeProposal(proposalId) },
                failureMessage = "执行提案失败"
            )
            if (result.isSuccess) {
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    // ── Member management ──

    fun loadMembers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(membersLoading = true, membersError = null)
            client.listMembers()
                .onSuccess { members ->
                    _uiState.value = _uiState.value.copy(
                        membersLoading = false,
                        members = members
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        membersLoading = false,
                        membersError = error.message
                    )
                }
        }
    }

    fun grantRole(subjectDid: String, role: String) {
        viewModelScope.launch {
            val result = authenticatedOperation.execute(
                cmdType = "grant-role",
                payload = GrantRoleAuthPayload(subjectDid, role),
                title = "授予角色",
                subtitle = "请验证身份以授予 $role 角色",
                operation = { client.grantRole(com.jlucraft.console.data.model.GrantRoleRequest(subjectDid, role)) },
                failureMessage = "授予角色失败"
            )
            if (result.isSuccess) {
                loadMembers()
            } else {
                _uiState.value = _uiState.value.copy(membersError = result.exceptionOrNull()?.message)
            }
        }
    }

    fun issueCredential(subjectDid: String) {
        viewModelScope.launch {
            val result = authenticatedOperation.execute(
                cmdType = "issue-credential",
                payload = IssueCredentialAuthPayload(subjectDid),
                title = "签发凭证",
                subtitle = "请验证身份以为成员签发凭证",
                operation = { client.issueCredential(com.jlucraft.console.data.model.CredentialActionRequest(subjectDid)) },
                failureMessage = "签发凭证失败"
            )
            result
                .onSuccess { response ->
                    val displayVc = com.jlucraft.console.data.model.VerifiableCredential(
                        id = "vc:${subjectDid}:${System.currentTimeMillis()}",
                        issuer = "union-manager",
                        issuanceDate = Instant.now().toString(),
                        credentialSubject = com.jlucraft.console.data.model.CredentialSubject(
                            id = subjectDid,
                            role = response.credentialType,
                            displayName = subjectDid,
                            clubCode = null
                        )
                    )
                    val vcJson = vcJsonFormatter.encodeToString(
                        com.jlucraft.console.data.model.VerifiableCredential.serializer(),
                        displayVc
                    )
                    _uiState.value = _uiState.value.copy(
                        showVcResultSheet = true,
                        issuedVcJson = vcJson,
                        issuedVc = displayVc
                    )
                    loadMembers()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(membersError = error.message)
                }
        }
    }

    fun dismissVcResult() {
        _uiState.value = _uiState.value.copy(
            showVcResultSheet = false,
            issuedVcJson = null,
            issuedVc = null,
            vcVerificationResult = null
        )
    }

    fun revokeCredential(subjectDid: String, reason: String) {
        viewModelScope.launch {
            val result = authenticatedOperation.execute(
                cmdType = "revoke-credential",
                payload = RevokeCredentialAuthPayload(subjectDid, reason),
                title = "吊销凭证",
                subtitle = "请验证身份以吊销成员凭证",
                operation = { client.revokeCredential(com.jlucraft.console.data.model.CredentialActionRequest(subjectDid, reason)) },
                failureMessage = "吊销凭证失败"
            )
            if (result.isSuccess) {
                loadMembers()
            } else {
                _uiState.value = _uiState.value.copy(membersError = result.exceptionOrNull()?.message)
            }
        }
    }

    private fun buildSignablePayload(proposal: Proposal): ByteArray {
        val idBytes = proposal.id.toByteArray(Charsets.UTF_8)
        val payloadBytes = proposal.payload.toJsonString().toByteArray(Charsets.UTF_8)
        return idBytes + payloadBytes
    }
}
