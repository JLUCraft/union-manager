package com.jlucraft.console.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.toShortDate
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.screens.audit.AuditLogScreen
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.SettingsViewModel
import com.jlucraft.console.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = ViewModelFactory())) {
    val state = viewModel.uiState.value
    var serverUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var showBiometricTest by rememberSaveable { mutableStateOf(false) }
    var showAuditLog by rememberSaveable { mutableStateOf(false) }
    var showDeviceManagement by rememberSaveable { mutableStateOf(false) }

    when {
        showAuditLog -> AuditLogSubScreen(onBack = { showAuditLog = false })
        showDeviceManagement -> DeviceManagementSubScreen(
            viewModel = viewModel,
            onBack = { showDeviceManagement = false }
        )
        else -> SettingsListScreen(
            state = state,
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            onSaveServerUrl = { viewModel.setServerUrl(serverUrl) },
            onShowBiometricTest = { showBiometricTest = true },
            onShowAuditLog = { showAuditLog = true },
            onShowDeviceManagement = { showDeviceManagement = true },
            onClearBiometricResult = { viewModel.clearBiometricResult() }
        )
    }

    if (showBiometricTest) {
        LaunchedEffect(Unit) {
            viewModel.testBiometric()
            showBiometricTest = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsListScreen(
    state: com.jlucraft.console.viewmodel.SettingsUiState,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onSaveServerUrl: () -> Unit,
    onShowBiometricTest: () -> Unit,
    onShowAuditLog: () -> Unit,
    onShowDeviceManagement: () -> Unit,
    onClearBiometricResult: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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
                    text = "节点连接",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://10.0.2.2:8080") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = "链接") },
                    trailingIcon = {
                        IconButton(onClick = onSaveServerUrl) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = "管理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("审计日志") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "审计") },
                        supportingContent = { Text("查看操作审计记录") },
                        trailingContent = {
                            IconButton(onClick = onShowAuditLog) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text("设备管理") },
                        leadingContent = { Icon(Icons.Default.Devices, contentDescription = "设备") },
                        supportingContent = { Text("查看和管理已注册设备，吊销丢失或被盗设备") },
                        trailingContent = {
                            IconButton(onClick = onShowDeviceManagement) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    text = "安全",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("TEE 密钥状态") },
                        leadingContent = {
                            Icon(
                                if (state.hasTeeKey) Icons.Default.VerifiedUser else Icons.Default.Warning,
                                contentDescription = if (state.hasTeeKey) "安全" else "警告",
                            tint = if (state.hasTeeKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = {
                        Text(
                            if (state.hasTeeKey) "密钥已生成并安全存储" else "密钥未初始化"
                        )
                    }
                )
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text("测试生物认证") },
                        leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = "生物认证") },
                    supportingContent = { Text("验证设备生物识别功能") },
                    trailingContent = {
                        TextButton(onClick = onShowBiometricTest) {
                            Text("测试")
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    text = "推送通道",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(state.distributorInfo) },
                        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = "推送") },
                    supportingContent = {
                        val statusText = when {
                            state.distributorInfo.startsWith("Embedded") -> "兜底方案 · 应用内置 FCM 分发器"
                            state.distributorInfo == "未安装" -> "未检测到推送分发器"
                            state.distributorInfo == "未选择" -> "请在系统中选择推送分发器"
                            else -> "外部分发器 · 由系统提供"
                        }
                        Text(statusText)
                    },
                    trailingContent = {
                        val badge = if (state.distributorInfo.startsWith("Embedded")) "FCM" else if (state.distributorInfo != "检测中..." && state.distributorInfo != "未安装" && state.distributorInfo != "未选择") "外部" else ""
                        if (badge.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Union Manager") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "关于") },
                    supportingContent = { Text("v0.1.0 · JLUCraft 管理终端") }
                )
            }

            if (state.biometricResult != null) {
                item {
                    AlertDialog(
                        onDismissRequest = onClearBiometricResult,
                        title = { Text("生物认证") },
                        text = { Text(state.biometricResult) },
                        confirmButton = {
                            TextButton(onClick = onClearBiometricResult) {
                                Text("确定")
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogSubScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("审计日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AuditLogScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceManagementSubScreen(
    viewModel: SettingsViewModel = viewModel(factory = ViewModelFactory()),
    onBack: () -> Unit
) {
    val state = viewModel.uiState.value
    var showRevokeDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showEmergencyRevokeDialog by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadDevices() }) {
                        if (state.devicesLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
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

            if (state.devicesError != null) {
                item {
                    Text(
                        text = "加载失败: ${state.devicesError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            if (state.devices.isEmpty() && !state.devicesLoading && state.devicesError == null) {
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
                    currentPubkey = viewModel.teeAuth.getPublicKey(),
                    onRevoke = { showRevokeDialog = device.pubkey },
                    onEmergencyRevoke = { showEmergencyRevokeDialog = device.pubkey }
                )
            }
        }
    }

    if (state.revokeSuccess != null) {
        LaunchedEffect(state.revokeSuccess) {
            viewModel.clearRevokeSuccess()
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
                text = "注册时间: ${device.createdAt.toShortDate()}",
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
