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
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.SchedulingConstraints
import com.jlucraft.console.data.model.SchedulingPreset
import com.jlucraft.console.data.model.SchedulingSimulation
import com.jlucraft.console.data.model.kindText
import com.jlucraft.console.ui.components.ReadOnlyModeBanner
import com.jlucraft.console.ui.components.statusColor
import com.jlucraft.console.ui.components.statusText
import com.jlucraft.console.ui.theme.StatusAmber
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.SchedulingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingScreen(
    services: AppServices,
    viewModel: SchedulingViewModel = viewModel(),
    initialInstanceId: String = "",
    onBack: (() -> Unit)? = null
) {
    val state = viewModel.uiState.value
    val readOnlyState by AuthStateHolder.readOnlyMode.collectAsState()
    val isReadOnly = readOnlyState is ReadOnlyMode.ReadOnly
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialInstanceId) {
        if (initialInstanceId.isNotBlank() && state.instanceId.isBlank()) {
            viewModel.setInstanceId(initialInstanceId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadInstances()
    }

    LaunchedEffect(state.applySuccess) {
        val msg = state.applySuccess
        if (msg != null) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearApplyState()
        }
    }
    LaunchedEffect(state.applyError) {
        val msg = state.applyError
        if (msg != null) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearApplyState()
        }
    }

    val listError = state.error

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调度策略") },
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
                    IconButton(onClick = {
                        if (state.instanceId.isNotBlank()) {
                            viewModel.loadConstraints(state.instanceId)
                        }
                    }) {
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
            if (isReadOnly) {
                item {
                    ReadOnlyModeBanner(readOnlyState)
                }
            }


            item {
                var instanceIdInput by rememberSaveable { mutableStateOf(state.instanceId) }
                OutlinedTextField(
                    value = instanceIdInput,
                    onValueChange = { instanceIdInput = it },
                    label = { Text("实例 ID") },
                    placeholder = { Text("输入要管理调度约束的实例 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isLoading,
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.setInstanceId(instanceIdInput) },
                            enabled = instanceIdInput.isNotBlank() && !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "查询")
                            }
                        }
                    }
                )
            }


            if (listError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(listError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }


            if (state.instanceId.isBlank()) {
                item {
                    Text(
                        text = "选择实例",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (state.instancesLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                } else if (state.instancesError != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "加载实例列表失败: ${state.instancesError}",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    item {
                        TextButton(
                            onClick = { viewModel.loadInstances() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重试")
                        }
                    }
                } else if (state.allInstances.isNullOrEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "没有可用的实例。请在上方输入实例 ID 后点击搜索按钮加载调度约束。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    val instances = state.allInstances
                    items(instances) { instance ->
                        InstancePickerCard(
                            instance = instance,
                            onClick = { viewModel.selectInstance(instance) }
                        )
                    }
                }
            }


            if (state.instanceId.isNotBlank()) {
                item {
                    Text(
                        text = "预设方案",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(SchedulingPreset.entries) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.applyPreset(preset) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.preset == preset)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = state.preset == preset,
                                    onClick = { viewModel.applyPreset(preset) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }


                state.constraints?.let { constraints ->
                    item {
                        Text(
                            text = "当前约束",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    item {
                        ConstraintsDetailCard(constraints)
                    }
                }


                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.simulateScheduling() },
                            modifier = Modifier.weight(1f),
                            enabled = state.constraints != null && !state.isSimulating
                        ) {
                            if (state.isSimulating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("模拟调度")
                        }
                        Button(
                            onClick = {
                                viewModel.applyConstraints("手动调整调度约束")
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.constraints != null && !state.isApplying && !isReadOnly
                        ) {
                            if (state.isApplying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("应用约束")
                        }
                    }
                }


                if (state.simError != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("模拟失败: ${state.simError}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                state.simResult?.let { simResult ->
                    item {
                        Text(
                            text = "模拟结果",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    item { SimulationResultCard(simResult) }
                }


                state.applyResult?.let { applyResult ->
                    item {
                        Text(
                            text = "应用结果",
                            style = MaterialTheme.typography.titleSmall,
                            color = StatusGreen,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = StatusGreen.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("约束已应用", fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "实例: ${applyResult.instanceId}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "应用时间: ${applyResult.appliedAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                applyResult.warnings.forEach { warning ->
                                    Text(
                                        text = "警告: $warning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstancePickerCard(instance: Instance, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = instance.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = instance.statusColor().copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = instance.statusText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = instance.statusColor(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = instance.kindText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = instance.currentHost ?: "未知",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConstraintsDetailCard(constraints: SchedulingConstraints) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ConstraintRow("最低节点分数", "${constraints.minNodeScore}")
            if (constraints.minCpuCores != null) ConstraintRow("最低 CPU 核数", "${constraints.minCpuCores}")
            if (constraints.minMemoryGb != null) ConstraintRow("最低内存 (GB)", "${constraints.minMemoryGb}")
            if (constraints.minDiskGb != null) ConstraintRow("最低磁盘 (GB)", "${constraints.minDiskGb}")
            ConstraintRow("优先级", constraints.priorityClass)
            ConstraintRow("专用宿主机", if (constraints.dedicatedHost) "是" else "否")
            ConstraintRow("可抢占", if (constraints.preemptible) "是" else "否")
            if (constraints.maxCoLocated > 0) ConstraintRow("最大共置数", "${constraints.maxCoLocated}")
            if (constraints.architecture != null) ConstraintRow("架构", constraints.architecture)
            if (constraints.region != null) ConstraintRow("区域", constraints.region)
            if (constraints.zone != null) ConstraintRow("可用区", constraints.zone)
            if (constraints.requiredLabels.isNotEmpty()) {
                ConstraintRow("必需标签", constraints.requiredLabels.entries.joinToString { "${it.key}=${it.value}" })
            }
            if (constraints.forbiddenLabels.isNotEmpty()) {
                ConstraintRow("禁止标签", constraints.forbiddenLabels.entries.joinToString { "${it.key}=${it.value}" })
            }
            if (constraints.preferredHosts.isNotEmpty()) {
                ConstraintRow("首选宿主机", constraints.preferredHosts.joinToString(", "))
            }
            if (constraints.blacklistedHosts.isNotEmpty()) {
                ConstraintRow(
                    "黑名单宿主机",
                    constraints.blacklistedHosts.joinToString(", "),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ConstraintRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun SimulationResultCard(sim: SchedulingSimulation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ConstraintRow("当前宿主机", sim.currentHost)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (sim.wouldMigrate) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (sim.wouldMigrate) StatusAmber else StatusGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (sim.wouldMigrate) "需要迁移" else "当前宿主机满足约束",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            sim.targetHost?.let { target ->
                ConstraintRow("推荐目标", target)
            }

            if (sim.eligibleHosts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "合格节点 (${sim.eligibleHosts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusGreen
                )
                sim.eligibleHosts.take(5).forEach { host ->
                    Text(
                        text = "  • $host",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (sim.constraintViolations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "约束冲突 (${sim.constraintViolations.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                sim.constraintViolations.forEach { violation ->
                    Text(
                        text = "  ✗ $violation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
