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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jlucraft.console.ui.components.StatusChip
import com.jlucraft.console.ui.theme.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.isStopped
import com.jlucraft.console.data.model.kindText
import com.jlucraft.console.data.model.statusColor
import com.jlucraft.console.data.model.statusText
import com.jlucraft.console.viewmodel.InstancesViewModel
import com.jlucraft.console.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen(viewModel: InstancesViewModel = viewModel(factory = ViewModelFactory())) {
    val state = viewModel.uiState.value

    when {
        state.selectedInstance != null -> InstanceDetailScreen(
            instance = state.selectedInstance,
            logs = state.logs,
            logsLoading = state.logsLoading,
            logsError = state.logsError,
            onBack = { viewModel.clearSelection() },
            onStart = { viewModel.startInstance(state.selectedInstance.id) },
            onStop = { viewModel.stopInstance(state.selectedInstance.id) },
            onRefreshLogs = { viewModel.fetchLogs(state.selectedInstance.id) }
        )
        else -> InstanceListScreen(
            state = state,
            viewModel = viewModel,
            onShowCreate = { /* handled internally */ },
            onShowMigrate = { /* handled internally */ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstanceListScreen(
    state: com.jlucraft.console.viewmodel.InstancesUiState,
    viewModel: InstancesViewModel,
    onShowCreate: () -> Unit,
    onShowMigrate: () -> Unit
) {
    val instances = state.instances
    val statusCounts = remember(instances) {
        instances.groupingBy { it.status.lowercase() }.eachCount()
    }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showMigrateDialog by rememberSaveable { mutableStateOf(false) }
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
                    } else {
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
            if (!state.isBatchMode) {
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusChip("运行中", statusCounts["running"].toString(), StatusGreen)
                    StatusChip("已停止", statusCounts["stopped"].toString(), MaterialTheme.colorScheme.outline)
                    StatusChip("迁移中", statusCounts["migrating"].toString(), MaterialTheme.colorScheme.primary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusChip("预配中", statusCounts["provisioning"].toString(), MaterialTheme.colorScheme.tertiary)
                    StatusChip("已销毁", statusCounts["destroyed"].toString(), MaterialTheme.colorScheme.outline)
                    StatusChip("失败", statusCounts["failed"].toString(), MaterialTheme.colorScheme.error)
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

            items(instances, key = { it.id }) { instance ->
                InstanceCard(
                    instance = instance,
                    enabled = !state.isAuthing,
                    isBatchMode = state.isBatchMode,
                    isSelected = state.selectedInstanceIds.contains(instance.id),
                    onToggleSelect = { viewModel.toggleInstanceSelection(instance.id) },
                    onClick = { viewModel.selectInstance(instance) },
                    onStart = { viewModel.startInstance(instance.id) },
                    onStop = { viewModel.stopInstance(instance.id) },
                    onMigrate = {
                        selectedInstanceId = instance.id
                        showMigrateDialog = true
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateInstanceDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, kind, image, owner, club, cpuCores, memoryGb, diskGb, admissionMode ->
                viewModel.createInstance(name, kind, image, owner, club, cpuCores, memoryGb, diskGb, admissionMode)
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
                    viewModel.migrateInstance(selectedInstanceId, targetHost)
                }
                showMigrateDialog = false
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
    onMigrate: () -> Unit = {}
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
                if (instance.currentHost.isNotEmpty()) {
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
                    enabled = selectedCount > 0
                ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "启动", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("启动")
                    }
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        enabled = selectedCount > 0,
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
                    enabled = selectedCount > 0
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
    onConfirm: (String, String, String, String, String, Int, Int, Int, String) -> Unit
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
    var expandedKind by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(false) }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name, kind, image, owner, club,
                        cpuCores.toInt(), memoryGb.toInt(), diskGb.toInt(),
                        admissionMode
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBatch) "批量迁移实例" else "迁移实例") },
        text = {
            OutlinedTextField(
                value = targetHost,
                onValueChange = { targetHost = it },
                label = { Text("目标节点 PeerID") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(targetHost) },
                enabled = targetHost.isNotBlank()
            ) {
                Text("迁移")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
