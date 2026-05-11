package com.jlucraft.console.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.jlucraft.console.data.auth.DidResolver
import com.jlucraft.console.data.model.DidDocument
import com.jlucraft.console.data.model.DidResolutionError
import com.jlucraft.console.data.model.DidResolutionResult
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DidResolutionUiState(
    val inputDid: String = "",
    val isLoading: Boolean = false,
    val result: DidResolutionResult? = null,
    val resolvedJson: String? = null,
    val error: String? = null
)

/**
 * ViewModel for the DID resolution screen.
 *
 * Calls DidResolver which uses:
 *  - Local did:key fast path
 *  - Server GET /v1/did/resolve?did=... for did:web and unknown methods
 *
 * Structured errors (invalidDid/methodNotSupported/notFound/resolutionFailed)
 * are surfaced in the UI.
 */
@HiltViewModel
class DidResolutionViewModel @Inject constructor(
    private val didResolver: DidResolver
) : ViewModel() {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    private val _uiState = mutableStateOf(DidResolutionUiState())
    val uiState: State<DidResolutionUiState> = _uiState

    fun setInputDid(did: String) {
        _uiState.value = _uiState.value.copy(
            inputDid = did,
            result = null,
            resolvedJson = null,
            error = null
        )
    }

    fun resolve() {
        val did = _uiState.value.inputDid.trim()
        if (did.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请输入 DID")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, result = null, resolvedJson = null)
            val result = didResolver.resolve(did)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                result = result,
                resolvedJson = when (result) {
                    is DidResolutionResult.Success -> try {
                        json.encodeToString(DidDocument.serializer(), result.document)
                    } catch (_: Exception) {
                        null
                    }
                    else -> null
                },
                error = when (result) {
                    is DidResolutionResult.Error -> result.cause.message
                    is DidResolutionResult.Invalid -> result.reason
                    is DidResolutionResult.NotFound -> "DID 未找到: ${result.did}"
                    is DidResolutionResult.ServerError -> errorMessageFromServerError(result.error)
                    else -> null
                }
            )
        }
    }

    private fun errorMessageFromServerError(error: DidResolutionError): String = when (error) {
        is DidResolutionError.InvalidDid -> "无效的 DID 格式: ${error.message}"
        is DidResolutionError.NotFound -> "DID 文档未找到: ${error.did}"
        is DidResolutionError.MethodNotSupported -> "不支持的 DID 方法: ${error.did}"
        is DidResolutionError.ResolutionFailed -> "DID 解析失败: ${error.message}"
        is DidResolutionError.NetworkError -> "网络错误: ${error.message}"
    }
}
