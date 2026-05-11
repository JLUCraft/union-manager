package com.jlucraft.console.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.StatusAmber
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.NodeScoresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScoresScreen(
    services: AppServices,
    viewModel: NodeScoresViewModel = viewModel(),
) {
    val state = viewModel.uiState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点信誉") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val scores = state.nodeScores
                val avgScore = if (scores.isNotEmpty()) scores.map { it.final_score }.average() else 0.0
                Text(
                    text = "${scores.size} 节点 · 均分 ${"%.1f".format(avgScore)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = state.nodeScores.isEmpty(),
                emptyText = "暂无节点分数数据"
            )

            itemsIndexed(state.nodeScores, key = { _, score -> score.peer_id }) { index, score ->
                NodeScoreCard(rank = index + 1, score = score)
            }

            // Detail for selected node
            state.selectedNodeScore?.let { selected ->
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "节点详情: ${selected.peer_id.take(16)}...",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item {
                    NodeScoreDetailCard(selected)
                }
            }
        }
    }
}

@Composable
private fun NodeScoreCard(rank: Int, score: NodeScore) {
    val scoreColor = when {
        score.final_score >= 80.0 -> StatusGreen
        score.final_score >= 50.0 -> StatusAmber
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(36.dp)
                )
                Column {
                    Text(
                        text = score.peer_id.take(16) + "...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "在线率 ${"%.0f".format(score.uptime_score)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "性能 ${"%.0f".format(score.performance_score)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "治理 ${"%.0f".format(score.governance_score)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.1f".format(score.final_score),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                if (score.penalty > 0.0) {
                    Text(
                        text = "惩罚 -${"%.1f".format(score.penalty)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeScoreDetailCard(score: NodeScore) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailRow("节点 ID", score.peer_id)
            DetailRow("在线率分数", "%.1f".format(score.uptime_score))
            DetailRow("性能分数", "%.1f".format(score.performance_score))
            DetailRow("治理分数", "%.1f".format(score.governance_score))
            DetailRow("惩罚", "%.1f".format(score.penalty))
            DetailRow("最终分数", "%.1f".format(score.final_score))
            DetailRow("最后更新", score.last_updated.take(19))
            score.cpu_usage?.let { DetailRow("CPU", "${"%.0f".format(it * 100)}%") }
            score.memory_usage?.let { DetailRow("内存", "${"%.0f".format(it * 100)}%") }
            score.disk_usage?.let { DetailRow("磁盘", "${"%.0f".format(it * 100)}%") }
            score.labels?.takeIf { it.isNotEmpty() }?.let { labels ->
                Text(
                    text = "标签: ${labels.entries.joinToString { (k, v) -> "$k=$v" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
