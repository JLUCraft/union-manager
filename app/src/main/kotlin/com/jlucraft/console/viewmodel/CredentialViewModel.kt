package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.model.CredentialActionRequest
import com.jlucraft.console.data.model.CredentialSubject
import com.jlucraft.console.data.model.GrantRoleAuthPayload
import com.jlucraft.console.data.model.GrantRoleRequest
import com.jlucraft.console.data.model.IssueCredentialAuthPayload
import com.jlucraft.console.data.model.IssueCredentialResponse
import com.jlucraft.console.data.model.MemberSummary
import com.jlucraft.console.data.model.RevokeCredentialAuthPayload
import com.jlucraft.console.data.model.VcVerificationResult
import com.jlucraft.console.data.model.VcVerifyRequest
import com.jlucraft.console.data.model.VerifiableCredential
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

data class CredentialUiState(
    val members: List<MemberSummary> = emptyList(),
    val membersLoading: Boolean = false,
    val membersError: String? = null,
    val issuedVcJson: String? = null,
    val issuedVc: VerifiableCredential? = null,
    val vcVerificationResult: VcVerificationResult? = null,
    val vcVerifying: Boolean = false,
    val vcVerifyError: String? = null,
    val operationLoading: Boolean = false,
    val operationError: String? = null,
    val operationSuccess: String? = null
)


 *
 *
@HiltViewModel
class CredentialViewModel @Inject constructor(
    private val client: Libp2pClient,
    private val authCoordinator: AuthCoordinator,
) : ViewModel() {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    private val _uiState = mutableStateOf(CredentialUiState())
    val uiState: State<CredentialUiState> = _uiState

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

    fun issueCredential(subjectDid: String, reason: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                operationLoading = true,
                operationError = null,
                operationSuccess = null,
                issuedVcJson = null,
                issuedVc = null
            )
            val result = authCoordinator.withAuthenticatedOperation(
                cmdType = "issue-credential",
                payload = IssueCredentialAuthPayload(subjectDid),
                title = "签发凭证",
                subtitle = "请验证身份以签发凭证",
                operation = {
                    val request = CredentialActionRequest(subjectDid, reason)
                    client.issueCredential(request)
                }
            )
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    operationLoading = false,
                    operationError = result.exceptionOrNull()?.message ?: "签发凭证失败"
                )
                return@launch
            }
            val response = result.getOrThrow()
            response
                .onSuccess { resp ->
                    val displayVc = VerifiableCredential(
                        id = "vc:${subjectDid}:${System.currentTimeMillis()}",
                        issuer = "union-manager",
                        issuanceDate = Instant.now().toString(),
                        credentialSubject = CredentialSubject(
                            id = subjectDid,
                            role = resp.credentialType,
                            displayName = subjectDid,
                            clubCode = null
                        )
                    )
                    val displayVcJson = json.encodeToString(
                        VerifiableCredential.serializer(),
                        displayVc
                    )
                    _uiState.value = _uiState.value.copy(
                        operationLoading = false,
                        operationSuccess = "VC 已签发",
                        issuedVcJson = displayVcJson,
                        issuedVc = displayVc
                    )
                    loadMembers()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        operationLoading = false,
                        operationError = error.message
                    )
                }
        }
    }

    fun revokeCredential(subjectDid: String, reason: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                operationLoading = true,
                operationError = null,
                operationSuccess = null
            )
            val result = authCoordinator.withAuthenticatedOperation(
                cmdType = "revoke-credential",
                payload = RevokeCredentialAuthPayload(subjectDid, reason),
                title = "吊销凭证",
                subtitle = "请验证身份以吊销凭证",
                operation = {
                    val request = CredentialActionRequest(subjectDid, reason)
                    client.revokeCredential(request)
                }
            )
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    operationLoading = false,
                    operationError = result.exceptionOrNull()?.message ?: "吊销凭证失败"
                )
                return@launch
            }
            val inner = result.getOrThrow()
            inner
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        operationLoading = false,
                        operationSuccess = "凭证已吊销"
                    )
                    loadMembers()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        operationLoading = false,
                        operationError = error.message
                    )
                }
        }
    }

    fun grantRole(subjectDid: String, role: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                operationLoading = true,
                operationError = null,
                operationSuccess = null
            )
            val result = authCoordinator.withAuthenticatedOperation(
                cmdType = "grant-role",
                payload = GrantRoleAuthPayload(subjectDid, role),
                title = "授予角色",
                subtitle = "请验证身份以授予 $role 角色",
                operation = {
                    val request = GrantRoleRequest(subjectDid, role)
                    client.grantRole(request)
                }
            )
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    operationLoading = false,
                    operationError = result.exceptionOrNull()?.message ?: "授予角色失败"
                )
                return@launch
            }
            val inner = result.getOrThrow()
            inner
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        operationLoading = false,
                        operationSuccess = "角色已更新为 $role"
                    )
                    loadMembers()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        operationLoading = false,
                        operationError = error.message
                    )
                }
        }
    }

    fun verifyVc(vcJson: VerifiableCredential) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                vcVerifying = true,
                vcVerifyError = null,
                vcVerificationResult = null
            )
            val request = VcVerifyRequest(vcJwt = vcJson.id, vcJson = vcJson)
            client.verifyVc(request)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        vcVerifying = false,
                        vcVerificationResult = result
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        vcVerifying = false,
                        vcVerifyError = error.message
                    )
                }
        }
    }

}
