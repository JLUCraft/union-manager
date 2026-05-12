package com.jlucraft.console.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.ui.components.ReadOnlyModeBanner
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.StatusAmber
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.AlertsViewModel

private val severityOptions = listOf(null to "全部", "critical" to "严重", "warning" to "警告", "info" to "信息")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    services: AppServices,
    viewModel: AlertsViewModel = viewModel(),
) {
    val state = viewModel.uiState.value
    val readOnlyState by AuthStateHolder.readOnlyMode.collectAsState()
    val isReadOnly = readOnlyState is ReadOnlyMode.ReadOnly
    var showFilterMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionError) {
        val error = state.actionError
        if (error != null) {
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
            viewModel.clearActionState()
        }
    }
    LaunchedEffect(state.actionSuccess) {
        if (state.actionSuccess != null) {
            val message = when (state.actionSuccess) {
                "alert_acknowledged" -> "告警已确认"
                "alert_resolved" -> "告警已解决"
                else -> "操作成功"
            }
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearActionState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("告警") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    Box {
                        TextButton(onClick = { showFilterMenu = true }) {
                            Text(
                                severityOptions.firstOrNull { it.first == state.severityFilter }?.second
                                    ?: "全部"
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            severityOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setSeverityFilter(value)
                                        showFilterMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.includeResolved) "隐藏已解决" else "包含已解决")
                                },
                                onClick = {
                                    viewModel.setIncludeResolved(!state.includeResolved)
                                    showFilterMenu = false
                                }
                            )
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
                val counts = remember(state.alerts) {
                    val bySeverity = state.alerts.groupBy { it.severity }
                    "严重 ${bySeverity["critical"]?.size ?: 0} · 警告 ${bySeverity["warning"]?.size ?: 0} · 信息 ${bySeverity["info"]?.size ?: 0}"
                }
                Text(
                    text = "共 ${state.alerts.size} 条 ($counts)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = state.alerts.isEmpty(),
                emptyText = "暂无告警"
            )

            items(state.alerts, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    isActionLoading = state.actionLoading == alert.id,
                    readOnly = isReadOnly,
                    onAcknowledge = { viewModel.acknowledgeAlert(alert.id) },
                    onResolve = { viewModel.resolveAlert(alert.id) }
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    isActionLoading: Boolean = false,
    readOnly: Boolean = false,
    onAcknowledge: () -> Unit = {},
    onResolve: () -> Unit = {}
) {
    val severityColor = when (alert.severity) {
        "critical", "error" -> MaterialTheme.colorScheme.error
        "warning" -> StatusAmber
        "info" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val isResolved = alert.resolvedAt != null
    val isAcknowledged = alert.acknowledgedAt != null
    var showActionMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isResolved)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                severityColor.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = severityColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = alert.alertType.replace("_", " "),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = alert.target.take(32),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAcknowledged) {
                        Text(
                            text = "已确认",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusGreen
                        )
                    }
                    if (isResolved) {
                        Text(
                            text = "已解决",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "创建: ${alert.createdAt.take(19)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )


            if (!isResolved && !readOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box {
                        TextButton(onClick = { showActionMenu = true }) {
                            Text("操作")
                        }
                        DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false }
                        ) {
                            if (!isAcknowledged) {
                                DropdownMenuItem(
                                    text = { Text("确认告警") },
                                    onClick = {
                                        showActionMenu = false
                                        onAcknowledge()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("解决告警") },
                                onClick = {
                                    showActionMenu = false
                                    onResolve()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
