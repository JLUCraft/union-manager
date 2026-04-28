package com.jlucraft.console.ui.screens.audit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.model.outcomeColor
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.AuditLogViewModel
import com.jlucraft.console.viewmodel.ViewModelFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val auditDateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(viewModel: AuditLogViewModel = viewModel(factory = ViewModelFactory())) {
    val state = viewModel.uiState.value
    val entries = state.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("审计日志") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("刷新")
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
                state.chainVerification?.let { verification ->
                    val icon = if (verification.valid) Icons.Default.CheckCircle else Icons.Default.Error
                    val color = if (verification.valid) StatusGreen else MaterialTheme.colorScheme.error
                    val text = if (verification.valid) "审计链完整" else "审计链断裂 (${verification.brokenEntries.size} 条)"

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = color.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = if (verification.valid) "链完整" else "链断裂",
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = text,
                                color = color,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "最近记录 (${entries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = entries.isEmpty(),
                emptyText = "暂无审计记录"
            )

            items(entries, key = { it.id }) { entry ->
                AuditEntryCard(entry)
            }
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AuditEntry) {
    val outcomeColor = entry.outcomeColor()

    val dateStr = remember(entry.ts) { auditDateFormat.format(Instant.ofEpochMilli(entry.ts)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${entry.id} ${entry.cmdType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "目标: ${entry.target}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "执行者: ${entry.actorPubkey.truncate(16)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.outcome,
                    style = MaterialTheme.typography.labelSmall,
                    color = outcomeColor
                )
            }

            if (entry.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "错误: ${entry.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
