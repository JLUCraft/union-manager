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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.data.local.SettingsStore
import com.jlucraft.console.ui.components.ReadOnlyModeBanner
import com.jlucraft.console.ui.navigation.AppRoute
import com.jlucraft.console.ui.screens.audit.AuditLogScreen
import com.jlucraft.console.ui.screens.oracle.OracleProofPanel
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.OracleViewModel
import com.jlucraft.console.viewmodel.SettingsViewModel

private val pushEventTypeOptions = listOf(
    "ProposalCreated" to "提案创建",
    "ProposalExecuted" to "提案执行",
    "InstanceMigrated" to "实例迁移",
    "TournamentCreated" to "赛事创建",
    "MatchResult" to "比赛结果",
    "AuthChallenge" to "认证挑战 (强制)",
    "InstanceCrash" to "实例崩溃 (强制)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    services: AppServices,
    viewModel: SettingsViewModel = viewModel(),
    onNavigate: ((AppRoute) -> Unit)? = null,
) {
    val state = viewModel.uiState.value
    var showBiometricTest by rememberSaveable { mutableStateOf(false) }
    var showAuditLog by rememberSaveable { mutableStateOf(false) }
    var showOracleProof by rememberSaveable { mutableStateOf(false) }

    when {
        showAuditLog -> AuditLogSubScreen(services = services, onBack = { showAuditLog = false })
        showOracleProof -> OracleProofSubScreen(
            services = services,
            onBack = { showOracleProof = false }
        )
        else -> SettingsListScreen(
            state = state,
            viewModel = viewModel,
            onShowBiometricTest = { showBiometricTest = true },
            onShowAuditLog = { showAuditLog = true },
            onShowOracleProof = { showOracleProof = true },
            onClearBiometricResult = { viewModel.clearBiometricResult() },
            onNavigate = onNavigate,
        )
    }

    if (showBiometricTest) {
        LaunchedEffect(Unit) {
            viewModel.testBiometric()
            showBiometricTest = false
        }
    }
}

private data class DistributorInfo(val headline: String, val supporting: String)

private fun distributorInfoFrom(raw: String): DistributorInfo = when {
    raw.startsWith("Embedded") -> DistributorInfo("推送服务：内置兜底", "应用内置 FCM 分发器 · 自动兜底")
    raw == "检测中..." -> DistributorInfo("推送服务：检测中...", "正在检测可用推送分发器")
    raw == "未安装" || raw == "未选择" -> DistributorInfo("推送暂不可用，将在可用时自动重试", "建议安装任一 UnifiedPush 分发器（如 ntfy）以启用推送")
    else -> DistributorInfo("推送服务：UnifiedPush", "外部 UnifiedPush 分发器 · $raw")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsListScreen(
    state: com.jlucraft.console.viewmodel.SettingsUiState,
    viewModel: SettingsViewModel,
    onShowBiometricTest: () -> Unit,
    onShowAuditLog: () -> Unit,
    onShowOracleProof: () -> Unit,
    onClearBiometricResult: () -> Unit,
    onNavigate: ((AppRoute) -> Unit)? = null,
) {
    val readOnlyState by AuthStateHolder.readOnlyMode.collectAsState()
    val isReadOnly = readOnlyState is ReadOnlyMode.ReadOnly
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
                    text = "管理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (isReadOnly) {
                item {
                    ReadOnlyModeBanner(readOnlyState)
                }
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
                            IconButton(onClick = {
                                if (onNavigate != null) onNavigate(AppRoute.Devices)
                            }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text("预言机证明验证") },
                        leadingContent = { Icon(Icons.Default.VerifiedUser, contentDescription = "预言机") },
                        supportingContent = { Text("查询玩家分数并验证 Merkle 证明") },
                        trailingContent = {
                            IconButton(onClick = onShowOracleProof) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                        }
                    }
                )
                HorizontalDivider()
            }

            if (onNavigate != null) {
                item {
                    ListItem(
                        headlineContent = { Text("赛季管理") },
                        leadingContent = { Icon(Icons.Default.EmojiEvents, contentDescription = "赛季") },
                        supportingContent = { Text("赛季生命周期、排名与存档状态") },
                        trailingContent = {
                            IconButton(onClick = { onNavigate(AppRoute.Season) }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                            }
                        }
                    )
                    HorizontalDivider()
                }

                item {
                    ListItem(
                        headlineContent = { Text("系统告警") },
                        leadingContent = { Icon(Icons.Default.Warning, contentDescription = "告警") },
                        supportingContent = { Text("告警列表、严重度筛选与确认") },
                        trailingContent = {
                            IconButton(onClick = { onNavigate(AppRoute.Alerts) }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                            }
                        }
                    )
                    HorizontalDivider()
                }

                item {
                    ListItem(
                        headlineContent = { Text("节点信誉评分") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = "节点") },
                        supportingContent = { Text("节点评分/声誉排行榜与健康摘要") },
                        trailingContent = {
                            IconButton(onClick = { onNavigate(AppRoute.NodeScores) }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                            }
                        }
                    )
                    HorizontalDivider()
                }

                item {
                    ListItem(
                        headlineContent = { Text("DID 解析") },
                        leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = "DID") },
                        supportingContent = { Text("解析 DID 文档并显示结构化元数据") },
                        trailingContent = {
                            IconButton(onClick = { onNavigate(AppRoute.DidResolution) }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                            }
                        }
                    )
                    HorizontalDivider()
                }

                item {
                    ListItem(
                        headlineContent = { Text("调度策略") },
                        leadingContent = { Icon(Icons.Default.Schedule, contentDescription = "调度") },
                        supportingContent = { Text("查看、模拟和应用实例调度约束") },
                        trailingContent = {
                            IconButton(onClick = { onNavigate(AppRoute.Scheduling) }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "查看")
                            }
                        }
                    )
                    HorizontalDivider()
                }
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
                val info = distributorInfoFrom(state.distributorInfo)
                ListItem(
                    headlineContent = { Text(info.headline) },
                    leadingContent = { Icon(Icons.Default.Notifications, contentDescription = "推送") },
                    supportingContent = { Text(info.supporting) }
                )
                HorizontalDivider()
            }



            item {
                Text(
                    text = "设备状态",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = {
                        Text(state.teeCapability)
                    },
                    leadingContent = {
                        Icon(
                            if (state.teeCapability.startsWith("无硬件安全能力")) Icons.Default.RemoveCircle else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.teeCapability.startsWith("无硬件安全能力")) MaterialTheme.colorScheme.error else StatusGreen
                        )
                    },
                    supportingContent = {
                        Text(
                            if (state.teeCapability.startsWith("无硬件安全能力"))
                                "设备未提供可用的硬件安全 Ed25519 密钥，需更换满足要求的设备。"
                            else
                                "设备支持硬件安全 Ed25519 签名，可执行完整管理操作。"
                        )
                    }
                )
                HorizontalDivider()
            }



            item {
                Text(
                    text = "推送偏好",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                val pushState = state

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("勿扰时段", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("启用勿扰模式")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(
                                checked = pushState.dndEnabled,
                                onCheckedChange = { viewModel.setDndEnabled(it) },
                                enabled = !isReadOnly
                            )
                        }
                        if (pushState.dndEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("静音时段: ${pushState.dndStartHour}:00 - ${pushState.dndEndHour}:00",
                                style = MaterialTheme.typography.bodySmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("开始", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = pushState.dndStartHour.toFloat(),
                                    onValueChange = { viewModel.setDndStartHour(it.toInt()) },
                                    valueRange = 0f..23f,
                                    steps = 22,
                                    enabled = !isReadOnly,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${pushState.dndStartHour}h", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("结束", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = pushState.dndEndHour.toFloat(),
                                    onValueChange = { viewModel.setDndEndHour(it.toInt()) },
                                    valueRange = 0f..23f,
                                    steps = 22,
                                    enabled = !isReadOnly,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${pushState.dndEndHour}h", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "事件类型",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(pushEventTypeOptions) { (eventType, label) ->
                val isForced = SettingsStore.FORCE_ENABLED_EVENT_TYPES.contains(eventType)
                val isEnabled = state.pushEnabledEventTypes.contains(eventType)
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = {
                        if (isForced) {
                            Text("始终启用", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.togglePushEventType(eventType) },
                            enabled = !isForced && !isReadOnly
                        )
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
        }
    }

    if (state.biometricResult != null) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogSubScreen(services: AppServices, onBack: () -> Unit) {
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
            AuditLogScreen(services = services)
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OracleProofSubScreen(
    services: AppServices,
    onBack: () -> Unit
) {
    val oracleViewModel: OracleViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预言机证明验证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            OracleProofPanel(
                viewModel = oracleViewModel,
                onDismiss = onBack
            )
        }
    }
}
