package com.jlucraft.console.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.Node
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.DashboardViewModel
import com.jlucraft.console.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel(factory = ViewModelFactory())) {
    val state = viewModel.uiState.value

    val nodes = remember(state.nodeScores, state.health) {
        if (state.nodeScores.isNotEmpty()) {
            state.nodeScores.map { score ->
                Node(
                    peerId = score.peer_id,
                    role = "server",
                    status = "online",
                    cpuUsage = score.cpu_usage ?: 0f,
                    memoryUsage = score.memory_usage ?: 0f,
                    diskUsage = score.disk_usage ?: 0f,
                    instanceCount = state.health?.running_instances ?: 0,
                    nodeScore = score.final_score.toFloat(),
                    labels = score.labels ?: emptyMap(),
                    lastSeenAt = score.last_updated
                )
            }
        } else {
            listOf(
                Node(
                    peerId = state.health?.peer_id ?: "-",
                    role = state.health?.consensus_role ?: "unknown",
                    status = state.health?.status ?: "unknown",
                    cpuUsage = 0f,
                    memoryUsage = 0f,
                    diskUsage = 0f,
                    instanceCount = state.health?.running_instances ?: 0,
                    nodeScore = 0f,
                    labels = emptyMap(),
                    lastSeenAt = state.health?.last_announcement_at ?: "-"
                )
            )
        }
    }

    val alerts = state.alerts

    val instances = state.instances
    val statusCounts = remember(instances) {
        instances.groupingBy { it.status.lowercase() }.eachCount()
    }
    val runningCount = statusCounts["running"] ?: 0
    val stoppedCount = statusCounts["stopped"] ?: 0
    val migratingCount = statusCounts["migrating"] ?: 0
    val degradedCount = statusCounts["degraded"] ?: 0

    val totalInstances = instances.size
    val peerCount = state.network?.connected_peers?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("控制台") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (state.isLoading) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AlertBanner(alerts) }
            item { NodeStatusPanel(peerCount, peerCount.coerceAtLeast(1), totalInstances) }
            item { NodeListPanel(nodes) }
            item { InstanceOverviewPanel(runningCount, stoppedCount, migratingCount, degradedCount) }
            item { NetworkTopologyPanel(peerCount.coerceAtLeast(1), peerCount) }
        }
    }
}

@Composable
private fun AlertBanner(alerts: List<com.jlucraft.console.data.model.Alert>) {
    val hasAlerts = alerts.isNotEmpty()
    val errorColor = MaterialTheme.colorScheme.error
    val severityColor = when {
        !hasAlerts -> StatusGreen
        alerts.any { it.severity == "critical" || it.severity == "error" } -> errorColor
        alerts.any { it.severity == "warning" } -> StatusAmber
        else -> StatusBlue
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasAlerts) severityColor.copy(alpha = 0.08f) else StatusGreen.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                    Icon(
                                imageVector = if (hasAlerts) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = if (hasAlerts) "告警" else "正常",
                tint = severityColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (hasAlerts) "${alerts.size} 条活跃告警" else "系统运行正常",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = alerts.firstOrNull()?.message ?: "所有节点在线，无活跃告警",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NodeStatusPanel(online: Int, total: Int, instances: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "节点状态",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusMetric("在线", online.toString(), StatusGreen)
                StatusMetric("总数", total.toString(), MaterialTheme.colorScheme.onSurface)
                StatusMetric("实例", instances.toString(), StatusBlue)
            }
        }
    }
}

@Composable
private fun NodeListPanel(nodes: List<Node>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "节点列表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            nodes.forEach { node ->
                NodeListItem(node)
                if (node != nodes.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun NodeListItem(node: Node) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.peerId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${node.role} · ${node.instanceCount} 实例 · 信誉 ${node.nodeScore.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResourceIndicator("CPU", node.cpuUsage)
            ResourceIndicator("MEM", node.memoryUsage)
        }
    }
}

@Composable
private fun ResourceIndicator(label: String, value: Float) {
    val color = when {
        value < 0.6f -> StatusGreen
        value < 0.85f -> StatusAmber
        else -> StatusRed
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InstanceOverviewPanel(running: Int, stopped: Int, migrating: Int, degraded: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "实例全景",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusMetric("运行中", running.toString(), StatusGreen)
                StatusMetric("已停止", stopped.toString(), MaterialTheme.colorScheme.outline)
                StatusMetric("迁移中", migrating.toString(), StatusBlue)
                StatusMetric("已降级", degraded.toString(), StatusAmber)
            }
        }
    }
}

@Composable
private fun NetworkTopologyPanel(nodeCount: Int, relayCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "网络拓扑",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "网络拓扑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Raft 共识组: ${nodeCount} 节点",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "活跃中继: ${relayCount} 个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
