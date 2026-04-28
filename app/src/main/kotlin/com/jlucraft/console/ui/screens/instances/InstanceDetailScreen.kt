package com.jlucraft.console.ui.screens.instances

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.isStopped
import com.jlucraft.console.data.model.kindText
import com.jlucraft.console.data.model.statusColor
import com.jlucraft.console.data.model.statusText
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceDetailScreen(
    instance: Instance,
    logs: List<String>,
    logsLoading: Boolean,
    logsError: String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefreshLogs: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instance.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onRefreshLogs) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新日志")
                    }
                }
            )
        }
    ) { padding ->
        val statusColor = instance.statusColor()
        val statusText = instance.statusText()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
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
                                text = instance.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = statusColor.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        DetailRow("ID", instance.id)
                        DetailRow("类型", instance.kindText())
                        DetailRow("状态", statusText)
                        DetailRow("当前节点", instance.currentHost)
                        DetailRow("创建时间", instance.createdAt)
                        DetailRow("在线玩家", instance.playerCount.toString())

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (instance.isStopped) {
                                Button(
                                    onClick = onStart,
                                    modifier = Modifier.weight(1f)
                                ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "启动")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("启动")
                                    }
                                } else {
                                    Button(
                                        onClick = onStop,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "停止")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("停止")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "控制台日志 (${logs.size} 行)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            listStatePlaceholders(
                isLoading = logsLoading,
                error = logsError,
                isEmpty = logs.isEmpty(),
                emptyText = "暂无日志",
                errorPrefix = "加载日志失败"
            )

            items(logs) { line ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
