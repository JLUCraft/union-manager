package com.jlucraft.console.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.toShortDate
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    services: AppServices,
    viewModel: DeviceViewModel = viewModel(),
    onBack: (() -> Unit)? = null
) {
    val state = viewModel.uiState.value
    var showRevokeDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showEmergencyRevokeDialog by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentPubkey = remember { viewModel.getCurrentPubkey() }

    LaunchedEffect(state.revokeSuccess) {
        val msg = state.revokeSuccess
        if (msg != null) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearRevokeStatus()
        }
    }
    LaunchedEffect(state.revokeError) {
        val msg = state.revokeError
        if (msg != null) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearRevokeStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备管理") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadDevices() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "已注册设备",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (state.error != null) {
                item {
                    Text(
                        text = "加载失败: ${state.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            if (state.devices.isEmpty() && !state.isLoading && state.error == null) {
                item {
                    Text(
                        text = "暂无设备记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(state.devices, key = { it.pubkey }) { device ->
                DeviceCard(
                    device = device,
                    currentPubkey = currentPubkey,
                    onRevoke = { showRevokeDialog = device.pubkey },
                    onEmergencyRevoke = { showEmergencyRevokeDialog = device.pubkey }
                )
            }
        }
    }

    showRevokeDialog?.let { pubkey ->
        RevokeDeviceDialog(
            title = "吊销设备",
            description = "吊销后该设备将无法再用于管理操作。此操作不可撤销。",
            onDismiss = { showRevokeDialog = null },
            onConfirm = { reason ->
                viewModel.revokeDevice(pubkey, reason)
                showRevokeDialog = null
            }
        )
    }

    showEmergencyRevokeDialog?.let { pubkey ->
        RevokeDeviceDialog(
            title = "紧急吊销设备",
            description = "紧急吊销仅需当事人签名即可生效，用于设备已确认在攻击者手中的情况。此操作不可撤销。",
            onDismiss = { showEmergencyRevokeDialog = null },
            onConfirm = { reason ->
                viewModel.emergencyRevokeDevice(pubkey, reason)
                showEmergencyRevokeDialog = null
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: Device,
    currentPubkey: String,
    onRevoke: () -> Unit,
    onEmergencyRevoke: () -> Unit
) {
    val isCurrentDevice = device.pubkey == currentPubkey
    val isRevoked = device.status == "revoked"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRevoked) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.platform,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrentDevice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "本机",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Surface(
                    color = if (isRevoked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isRevoked) "已吊销" else "正常",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRevoked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "公钥: ${device.pubkey.truncate(32)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "注册时间: ${device.createdAt?.toShortDate() ?: "未知"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (device.lastSeenAt != null) {
                Text(
                    text = "最后在线: ${device.lastSeenAt.toShortDate()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isRevoked) {
                Text(
                    text = "吊销时间: ${device.revokedAt?.toShortDate() ?: "未知"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (device.revokedReason != null) {
                    Text(
                        text = "原因: ${device.revokedReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!isRevoked) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRevoke,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("吊销")
                    }
                    OutlinedButton(
                        onClick = onEmergencyRevoke,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("紧急吊销")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevokeDeviceDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("吊销原因") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text("确认吊销", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
