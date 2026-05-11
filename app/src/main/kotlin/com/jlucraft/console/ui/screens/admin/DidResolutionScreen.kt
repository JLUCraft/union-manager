package com.jlucraft.console.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.model.DidResolutionError
import com.jlucraft.console.data.model.DidResolutionResult
import com.jlucraft.console.ui.theme.StatusAmber
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.DidResolutionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DidResolutionScreen(
    services: AppServices,
    viewModel: DidResolutionViewModel = viewModel(),
) {
    val state = viewModel.uiState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DID 解析") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Input field
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "输入 DID 进行解析",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "支持 did:key (本地离线) 和 did:web (服务器代理)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.inputDid,
                onValueChange = { viewModel.setInputDid(it) },
                label = { Text("DID") },
                placeholder = { Text("did:key:z6Mk... 或 did:web:example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.resolve() },
                        enabled = !state.isLoading && state.inputDid.isNotBlank()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "解析")
                        }
                    }
                }
            )

            // Quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.setInputDid("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"); viewModel.resolve() },
                    enabled = !state.isLoading
                ) {
                    Text("示例 did:key", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.setInputDid("did:web:example.com"); viewModel.resolve() },
                    enabled = !state.isLoading
                ) {
                    Text("示例 did:web", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Error display
            state.error?.let { errorMsg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Success: DID Document display
            state.result?.let { result ->
                when (result) {
                    is DidResolutionResult.Success -> {
                        DidDocumentContent(result)
                    }
                    is DidResolutionResult.ServerError -> {
                        ServerErrorContent(result.error)
                    }
                    else -> {
                        // Handled by error string above
                    }
                }
            }

            // Resolved JSON
            state.resolvedJson?.let { json ->
                Text(
                    text = "DID 文档 JSON",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = json,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DidDocumentContent(result: DidResolutionResult.Success) {
    val doc = result.document

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = StatusGreen.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "解析成功",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusGreen
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DID: ${doc.id}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (doc.verificationMethod.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "验证方法 (${doc.verificationMethod.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                doc.verificationMethod.forEach { vm ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "类型: ${vm.type}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "ID: ${vm.id.take(48)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "控制器: ${vm.controller.take(48)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (doc.authentication.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "认证: ${doc.authentication.size} 方法",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (doc.assertionMethod.isNotEmpty()) {
                Text(
                    text = "断言: ${doc.assertionMethod.size} 方法",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (doc.service.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "服务端点 (${doc.service.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                doc.service.forEach { svc ->
                    Text(
                        text = "${svc.type}: ${svc.serviceEndpoint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerErrorContent(error: DidResolutionError) {
    val (title, description, color) = when (error) {
        is DidResolutionError.InvalidDid -> Triple(
            "无效的 DID",
            "DID 格式无效: ${error.message}",
            MaterialTheme.colorScheme.error
        )
        is DidResolutionError.NotFound -> Triple(
            "未找到",
            "未找到 DID 文档: ${error.did}",
            StatusAmber
        )
        is DidResolutionError.MethodNotSupported -> Triple(
            "不支持的 DID 方法",
            "服务器不支持此 DID 方法: ${error.did}",
            StatusAmber
        )
        is DidResolutionError.ResolutionFailed -> Triple(
            "解析失败",
            "DID 解析过程中发生错误: ${error.message}",
            MaterialTheme.colorScheme.error
        )
        is DidResolutionError.NetworkError -> Triple(
            "网络错误",
            "无法连接到解析服务: ${error.message}",
            MaterialTheme.colorScheme.error
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
