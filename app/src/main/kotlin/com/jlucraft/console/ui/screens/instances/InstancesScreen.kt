package com.jlucraft.console.ui.screens.instances

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.ui.components.StatusChip
import com.jlucraft.console.ui.navigation.AppRoute
import com.jlucraft.console.ui.theme.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.AdmissionPolicy
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.isStopped
import com.jlucraft.console.data.model.kindText
import com.jlucraft.console.ui.components.AdminQuickLinksCard
import com.jlucraft.console.ui.components.ReadOnlyModeBanner
import com.jlucraft.console.ui.components.statusColor
import com.jlucraft.console.ui.components.statusText
import com.jlucraft.console.viewmodel.InstancesViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen(
    services: AppServices,
    viewModel: InstancesViewModel = viewModel(),
    onNavigate: ((AppRoute) -> Unit)? = null,
) {
    val state = viewModel.uiState.value
    val readOnlyState by AuthStateHolder.readOnlyMode.collectAsState()
    val isReadOnly = readOnlyState is ReadOnlyMode.ReadOnly

    if (state.selectedInstance != null) {
        InstanceDetailScreen(
            instance = state.selectedInstance,
            logs = state.logs,
            logsLoading = state.logsLoading,
            logsError = state.logsError,
            onBack = { viewModel.clearSelection() },
            onStart = { viewModel.startInstance(state.selectedInstance.id) },
            onStop = { viewModel.stopInstance(state.selectedInstance.id) },
            onRefreshLogs = { viewModel.fetchLogs(state.selectedInstance.id) },
            client = services.client,
        )
        return
    }

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showMigrateDialog by rememberSaveable { mutableStateOf(false) }
    var showStopReasonDialog by rememberSaveable { mutableStateOf(false) }
    var showConfigDialog by rememberSaveable { mutableStateOf(false) }
    var selectedInstanceId by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    if (state.authError != null) {
        LaunchedEffect(state.authError) {
            snackbarHostState.showSnackbar("认证失败: ${state.authError}")
            viewModel.clearAuthError()
        }
    }

    if (state.operationError != null) {
        LaunchedEffect(state.operationError) {
            snackbarHostState.showSnackbar("操作失败: ${state.operationError}")
            viewModel.clearOperationError()
        }
    }

    if (state.batchErrors.isNotEmpty()) {
        LaunchedEffect(state.batchErrors) {
            val errorText = if (state.batchErrors.size == 1) {
                state.batchErrors.first()
            } else {
                "批量操作完成，${state.batchErrors.size} 个错误"
            }
            snackbarHostState.showSnackbar(errorText)
            viewModel.clearBatchErrors()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isBatchMode) {
                        Text("已选择 ${state.selectedInstanceIds.size}/20")
                    } else {
                        Text("实例管理")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (state.isBatchMode) {
                        IconButton(onClick = { viewModel.exitBatchMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出批量模式")
                        }
                    }
                },
                actions = {
                    if (state.isBatchMode) {
                        TextButton(onClick = { viewModel.selectAllInstances() }) {
                            Text("全选")
                        }
                    } else if (!isReadOnly) {
                        TextButton(onClick = { viewModel.toggleBatchMode() }) {
                            Text("批量")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isBatchMode && !isReadOnly) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "创建实例")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.isBatchMode) {
                BatchOperationBar(
                    selectedCount = state.selectedInstanceIds.size,
                    enabled = !isReadOnly,
                    onStart = { viewModel.batchStart() },
                    onStop = { viewModel.batchStop() },
                    onMigrate = {
                        selectedInstanceId = "batch"
                        showMigrateDialog = true
                    }
                )
            }
            if (state.batchOperationInProgress) {
                BatchProgressBar(
                    operationType = state.batchOperationType ?: "",
                    progress = state.batchProgress,
                    total = state.batchTotal,
                    onAbort = { viewModel.abortBatchOperation() }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (isReadOnly) {
                item {
                    ReadOnlyModeBanner(readOnlyState)
                }
            }

            item {
                val statusCounts = state.instances.groupBy { it.status }.mapValues { it.value.size }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusChip("运行中", (statusCounts["running"] ?: 0).toString(), StatusGreen)
                    StatusChip("已停止", (statusCounts["stopped"] ?: 0).toString(), MaterialTheme.colorScheme.outline)
                    StatusChip("迁移中", (statusCounts["migrating"] ?: 0).toString(), MaterialTheme.colorScheme.primary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusChip("预配中", (statusCounts["provisioning"] ?: 0).toString(), MaterialTheme.colorScheme.tertiary)
                    StatusChip("已销毁", (statusCounts["destroyed"] ?: 0).toString(), MaterialTheme.colorScheme.outline)
                    StatusChip("失败", (statusCounts["failed"] ?: 0).toString(), MaterialTheme.colorScheme.error)
                }
            }


            if (onNavigate != null) {
                item {
                    AdminQuickLinksCard(onNavigate)
                }
            }

            item {
                Text(
                    text = "实例列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
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

            items(state.instances, key = { it.id }) { instance ->
                InstanceCard(
                    instance = instance,
                    enabled = !state.isAuthing,
                    isBatchMode = state.isBatchMode,
                    isSelected = state.selectedInstanceIds.contains(instance.id),
                    onToggleSelect = { viewModel.toggleInstanceSelection(instance.id) },
                    onClick = { viewModel.selectInstance(instance) },
                    onStart = { viewModel.startInstance(instance.id) },
                    onStop = {
                        selectedInstanceId = instance.id
                        showStopReasonDialog = true
                    },
                    onMigrate = {
                        selectedInstanceId = instance.id
                        showMigrateDialog = true
                    },
                    onConfig = {
                        selectedInstanceId = instance.id
                        showConfigDialog = true
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateInstanceDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, kind, image, owner, club, cpuCores, memoryGb, diskGb, admission ->
                viewModel.createInstance(name, kind, image, owner, club, cpuCores, memoryGb, diskGb, admission)
                showCreateDialog = false
            }
        )
    }

    if (showMigrateDialog) {
        MigrateDialog(
            onDismiss = { showMigrateDialog = false },
            isBatch = selectedInstanceId == "batch",
            onConfirm = { targetHost ->
                if (selectedInstanceId == "batch") {
                    viewModel.batchMigrate(targetHost)
                } else {
                    if (targetHost.isBlank()) {
                        viewModel.autoMigrateInstance(selectedInstanceId)
                    } else {
                        viewModel.migrateInstance(selectedInstanceId, targetHost)
                    }
                }
                showMigrateDialog = false
            }
        )
    }

    if (showStopReasonDialog) {
        StopReasonDialog(
            onDismiss = { showStopReasonDialog = false },
            onConfirm = { reason ->
                viewModel.stopInstance(selectedInstanceId, reason)
                showStopReasonDialog = false
            }
        )
    }

    if (showConfigDialog) {
        val currentInstance = state.instances.find { it.id == selectedInstanceId }
        ConfigUpdateDialog(
            onDismiss = { showConfigDialog = false },
            currentAdmission = currentInstance?.admission,
            onConfirm = { config, admission ->
                viewModel.updateInstanceConfig(selectedInstanceId, config)
                showConfigDialog = false
            }
        )
    }
}

@Composable
private fun InstanceCard(
    instance: Instance,
    enabled: Boolean = true,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit = {},
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onMigrate: () -> Unit = {},
    onConfig: () -> Unit = {}
) {
    Card(
        onClick = {
            if (isBatchMode) {
                onToggleSelect()
            } else {
                onClick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
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
                    if (isBatchMode) {
                        IconButton(onClick = onToggleSelect, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = if (isSelected) "已选择" else "未选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = instance.kindText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    color = instance.statusColor().copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = instance.statusText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = instance.statusColor(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "内存",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${instance.playerCount} 人在线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!instance.currentHost.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "节点: ${instance.currentHost}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isBatchMode) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (instance.isStopped) {
                        OutlinedButton(
                            onClick = onStart,
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "启动")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("启动")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onStop,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "停止")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止")
                        }
                    }
                    OutlinedButton(
                        onClick = onMigrate,
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    ) {
                        Text("迁移")
                    }
                    OutlinedButton(
                        onClick = onConfig,
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    ) {
                        Text("配置")
                    }
                    OutlinedButton(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    ) {
                        Text("详情")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchOperationBar(
    selectedCount: Int,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMigrate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "已选择 $selectedCount 个实例${if (selectedCount >= 20) " (已达上限)" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedCount >= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    enabled = enabled && selectedCount > 0
                ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "启动", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("启动")
                    }
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        enabled = enabled && selectedCount > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "停止", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("停止")
                }
                OutlinedButton(
                    onClick = onMigrate,
                    modifier = Modifier.weight(1f),
                    enabled = enabled && selectedCount > 0
                ) {
                    Text("迁移")
                }
            }
        }
    }
}

@Composable
private fun BatchProgressBar(
    operationType: String,
    progress: Int,
    total: Int,
    onAbort: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "批量${when (operationType) {
                        "start" -> "启动"
                        "stop" -> "停止"
                        "migrate" -> "迁移"
                        else -> operationType
                    }}中: $progress / $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TextButton(onClick = onAbort) {
                    Text("中止", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateInstanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, Int, Int, Int, AdmissionPolicy) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("service") }
    var image by rememberSaveable { mutableStateOf("itzg/minecraft-server:java21") }
    var owner by rememberSaveable { mutableStateOf("") }
    var club by rememberSaveable { mutableStateOf("") }
    var cpuCores by rememberSaveable { mutableStateOf("") }
    var memoryGb by rememberSaveable { mutableStateOf("") }
    var diskGb by rememberSaveable { mutableStateOf("") }
    var admissionMode by rememberSaveable { mutableStateOf("public") }
    var allowedClubs by rememberSaveable { mutableStateOf("") }
    var allowedPlayers by rememberSaveable { mutableStateOf("") }
    var requiresVerifiedEmail by rememberSaveable { mutableStateOf(false) }
    var allowedEmailDomains by rememberSaveable { mutableStateOf("") }
    var expandedKind by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(false) }
    var showAdmissionValidationError by remember { mutableStateOf(false) }

    val admissionModes = remember {
        listOf(
            "public" to "公开",
            "vc-only" to "VC 准入",
            "mua-member" to "MUA 成员",
            "club-only" to "社团专用"
        )
    }

    val resourcesValid = cpuCores.toIntOrNull()?.let { it > 0 } == true &&
        memoryGb.toIntOrNull()?.let { it > 0 } == true &&
        diskGb.toIntOrNull()?.let { it > 0 } == true

    fun parseCommaSeparated(input: String): List<String> =
        input.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建实例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("所有者") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = club,
                    onValueChange = { club = it },
                    label = { Text("社团") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = image,
                    onValueChange = { image = it },
                    label = { Text("镜像") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = cpuCores,
                    onValueChange = { cpuCores = it },
                    label = { Text("CPU 核数") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = memoryGb,
                    onValueChange = { memoryGb = it },
                    label = { Text("内存 (GB)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = diskGb,
                    onValueChange = { diskGb = it },
                    label = { Text("磁盘 (GB)") },
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = expandedKind,
                    onExpandedChange = { expandedKind = !expandedKind }
                ) {
                    OutlinedTextField(
                        value = if (kind == "service") "服务" else "房间",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKind) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedKind,
                        onDismissRequest = { expandedKind = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("服务") },
                            onClick = { kind = "service"; expandedKind = false }
                        )
                        DropdownMenuItem(
                            text = { Text("房间") },
                            onClick = { kind = "room"; expandedKind = false }
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedMode,
                    onExpandedChange = { expandedMode = !expandedMode }
                ) {
                    OutlinedTextField(
                        value = admissionModes.firstOrNull { it.first == admissionMode }?.second ?: admissionMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("准入模式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedMode,
                        onDismissRequest = { expandedMode = false }
                    ) {
                        admissionModes.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { admissionMode = mode; expandedMode = false }
                            )
                        }
                    }
                }


                if (admissionMode == "club-only" || admissionMode == "mua-member") {
                    OutlinedTextField(
                        value = allowedClubs,
                        onValueChange = { allowedClubs = it },
                        label = { Text("允许的社团代码（逗号分隔）") },
                        singleLine = true,
                        placeholder = { Text("例如: jlu, nju") },
                        isError = showAdmissionValidationError && admissionMode == "club-only" && allowedClubs.isBlank(),
                        supportingText = if (showAdmissionValidationError && admissionMode == "club-only" && allowedClubs.isBlank()) {
                            { Text("社团专用模式必须指定至少一个社团代码") }
                        } else null
                    )
                }


                OutlinedTextField(
                    value = allowedPlayers,
                    onValueChange = { allowedPlayers = it },
                    label = { Text("允许的玩家 PeerID（逗号分隔）") },
                    singleLine = true,
                    placeholder = { Text("PeerID 白名单，留空表示不限制") }
                )


                if (admissionMode == "vc-only") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = requiresVerifiedEmail,
                            onCheckedChange = { requiresVerifiedEmail = it }
                        )
                        Text("要求已验证邮箱")
                    }
                    OutlinedTextField(
                        value = allowedEmailDomains,
                        onValueChange = { allowedEmailDomains = it },
                        label = { Text("允许的邮箱域名（逗号分隔）") },
                        singleLine = true,
                        placeholder = { Text("例如: mails.jlu.edu.cn") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (admissionMode == "club-only" && allowedClubs.isBlank()) {
                        showAdmissionValidationError = true
                        return@TextButton
                    }
                    val admission = AdmissionPolicy(
                        mode = admissionMode,
                        allowed_clubs = parseCommaSeparated(allowedClubs),
                        allowed_players = parseCommaSeparated(allowedPlayers),
                        requires_verified_email = requiresVerifiedEmail,
                        allowed_email_domains = parseCommaSeparated(allowedEmailDomains)
                    )
                    onConfirm(
                        name, kind, image, owner, club,
                        cpuCores.toInt(), memoryGb.toInt(), diskGb.toInt(),
                        admission
                    )
                },
                enabled = name.isNotBlank() && owner.isNotBlank() && club.isNotBlank() && resourcesValid
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MigrateDialog(
    onDismiss: () -> Unit,
    isBatch: Boolean = false,
    onConfirm: (String) -> Unit
) {
    var targetHost by rememberSaveable { mutableStateOf("") }
    var autoMigrate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBatch) "批量迁移实例" else "迁移实例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoMigrate,
                        onCheckedChange = { autoMigrate = it }
                    )
                    Text("自动迁移（由调度器选择目标节点）")
                }
                if (!autoMigrate) {
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("目标节点 PeerID") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (autoMigrate) onConfirm("") else onConfirm(targetHost)
                },
                enabled = autoMigrate || targetHost.isNotBlank()
            ) {
                Text(if (autoMigrate) "自动迁移" else "迁移")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var impactSummary by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("停止实例") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("停止实例将中断所有在线玩家连接。请填写停止原因（用于审计记录）：")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("停止原因（必填）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1
                )
                OutlinedTextField(
                    value = impactSummary,
                    onValueChange = { impactSummary = it },
                    label = { Text("影响说明（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fullReason = if (impactSummary.isNotBlank()) "$reason — $impactSummary" else reason
                    onConfirm(fullReason)
                },
                enabled = reason.isNotBlank()
            ) {
                Text("停止实例", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigUpdateDialog(
    onDismiss: () -> Unit,
    currentAdmission: AdmissionPolicy?,
    onConfirm: (Map<String, String>, AdmissionPolicy?) -> Unit
) {
    var envKey by rememberSaveable { mutableStateOf("") }
    var envValue by rememberSaveable { mutableStateOf("") }
    val envEntries = remember { mutableListOf<Pair<String, String>>() }


    var admissionMode by rememberSaveable { mutableStateOf(currentAdmission?.mode ?: "public") }
    var allowedClubs by rememberSaveable { mutableStateOf(currentAdmission?.allowed_clubs?.joinToString(", ") ?: "") }
    var allowedPlayers by rememberSaveable { mutableStateOf(currentAdmission?.allowed_players?.joinToString(", ") ?: "") }
    var requiresVerifiedEmail by rememberSaveable { mutableStateOf(currentAdmission?.requires_verified_email ?: false) }
    var allowedEmailDomains by rememberSaveable { mutableStateOf(currentAdmission?.allowed_email_domains?.joinToString(", ") ?: "") }
    var expandedMode by remember { mutableStateOf(false) }
    var showAdmissionValidationError by remember { mutableStateOf(false) }

    val admissionModes = remember {
        listOf(
            "public" to "公开",
            "vc-only" to "VC 准入",
            "mua-member" to "MUA 成员",
            "club-only" to "社团专用"
        )
    }

    fun parseCommaSeparated(input: String): List<String> =
        input.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新实例配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("修改环境变量或服务配置。更改可能需要重启实例生效。")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = envKey,
                        onValueChange = { envKey = it },
                        label = { Text("键") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = envValue,
                        onValueChange = { envValue = it },
                        label = { Text("值") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                TextButton(
                    onClick = {
                        if (envKey.isNotBlank()) {
                            envEntries.add(envKey to envValue)
                            envKey = ""
                            envValue = ""
                        }
                    },
                    enabled = envKey.isNotBlank()
                ) {
                    Text("添加")
                }
                if (envEntries.isNotEmpty()) {
                    Text("待更新项：")
                    envEntries.forEach { (k, v) ->
                        Text(
                            "  $k = $v",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }


                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "准入策略",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                ExposedDropdownMenuBox(
                    expanded = expandedMode,
                    onExpandedChange = { expandedMode = !expandedMode }
                ) {
                    OutlinedTextField(
                        value = admissionModes.firstOrNull { it.first == admissionMode }?.second ?: admissionMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("准入模式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMode) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedMode,
                        onDismissRequest = { expandedMode = false }
                    ) {
                        admissionModes.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { admissionMode = mode; expandedMode = false }
                            )
                        }
                    }
                }


                if (admissionMode == "club-only" || admissionMode == "mua-member") {
                    OutlinedTextField(
                        value = allowedClubs,
                        onValueChange = { allowedClubs = it },
                        label = { Text("允许的社团代码（逗号分隔）") },
                        singleLine = true,
                        placeholder = { Text("例如: jlu, nju") },
                        isError = showAdmissionValidationError && admissionMode == "club-only" && allowedClubs.isBlank(),
                        supportingText = if (showAdmissionValidationError && admissionMode == "club-only" && allowedClubs.isBlank()) {
                            { Text("社团专用模式必须指定至少一个社团代码") }
                        } else null
                    )
                }


                OutlinedTextField(
                    value = allowedPlayers,
                    onValueChange = { allowedPlayers = it },
                    label = { Text("允许的玩家 PeerID（逗号分隔）") },
                    singleLine = true,
                    placeholder = { Text("PeerID 白名单，留空表示不限制") }
                )


                if (admissionMode == "vc-only") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = requiresVerifiedEmail,
                            onCheckedChange = { requiresVerifiedEmail = it }
                        )
                        Text("要求已验证邮箱")
                    }
                    OutlinedTextField(
                        value = allowedEmailDomains,
                        onValueChange = { allowedEmailDomains = it },
                        label = { Text("允许的邮箱域名（逗号分隔）") },
                        singleLine = true,
                        placeholder = { Text("例如: mails.jlu.edu.cn") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (admissionMode == "club-only" && allowedClubs.isBlank()) {
                        showAdmissionValidationError = true
                        return@TextButton
                    }
                    val admission = AdmissionPolicy(
                        mode = admissionMode,
                        allowed_clubs = parseCommaSeparated(allowedClubs),
                        allowed_players = parseCommaSeparated(allowedPlayers),
                        requires_verified_email = requiresVerifiedEmail,
                        allowed_email_domains = parseCommaSeparated(allowedEmailDomains)
                    )
                    onConfirm(envEntries.toMap(), admission)
                },
                enabled = envEntries.isNotEmpty()
            ) {
                Text("更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

