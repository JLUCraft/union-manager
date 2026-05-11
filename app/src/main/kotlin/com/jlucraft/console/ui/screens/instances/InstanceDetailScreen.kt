package com.jlucraft.console.ui.screens.instances

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.SchedulingConstraints
import com.jlucraft.console.data.model.isStopped
import com.jlucraft.console.data.model.kindText
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.components.statusColor
import com.jlucraft.console.ui.components.statusText
import com.jlucraft.console.ui.screens.admin.SchedulerSimulationSheet
import com.jlucraft.console.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onRefreshLogs: () -> Unit,
    client: Libp2pClient? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSimulationSheet by remember { mutableStateOf(false) }
    var schedulingConstraints by remember { mutableStateOf<SchedulingConstraints?>(null) }
    var constraintsLoading by remember { mutableStateOf(false) }
    var constraintsError by remember { mutableStateOf<String?>(null) }

    // --- Log tool state ---
    val lazyListState = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    // Client-side filtered logs
    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    // Auto-follow: scroll to bottom when followTail is on and new logs arrive
    LaunchedEffect(logs.size, followTail, isPaused) {
        if (followTail && !isPaused && filteredLogs.isNotEmpty()) {
            snapshotFlow { lazyListState.layoutInfo.totalItemsCount }
                .first { it > 0 }
            lazyListState.animateScrollToItem(lazyListState.layoutInfo.totalItemsCount - 1)
        }
    }

    // Clear export status after a delay
    LaunchedEffect(exportStatus) {
        if (exportStatus != null) {
            delay(4000)
            exportStatus = null
        }
    }

    // Export launcher: system file picker for saving logs
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            logs.forEach { line ->
                                outputStream.write("${line}\n".toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                    exportStatus = "已导出 ${logs.size} 行日志"
                } catch (e: Exception) {
                    exportStatus = "导出失败: ${e.message}"
                }
            }
        }
    }

    fun openSimulationSheet() {
        if (client == null) return
        scope.launch {
            constraintsLoading = true
            constraintsError = null
            client.getSchedulingConstraints(instance.id)
                .onSuccess { constraints ->
                    schedulingConstraints = constraints
                    showSimulationSheet = true
                }
                .onFailure { e ->
                    constraintsError = e.message
                }
            constraintsLoading = false
        }
    }

    val constraints = schedulingConstraints
    if (showSimulationSheet && constraints != null && client != null) {
        SchedulerSimulationSheet(
            client = client,
            instanceId = instance.id,
            currentConstraints = constraints,
            onDismiss = { showSimulationSheet = false }
        )
    }

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
                    if (client != null) {
                        IconButton(
                            onClick = { openSimulationSheet() },
                            enabled = !constraintsLoading
                        ) {
                            if (constraintsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Build, contentDescription = "调度模拟")
                            }
                        }
                    }
                    IconButton(onClick = onRefreshLogs, enabled = !isPaused) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新日志")
                    }
                }
            )
        }
    ) { padding ->
        val statusColor = instance.statusColor()
        val statusText = instance.statusText()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Instance detail card ---
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
                        DetailRow("当前节点", instance.currentHost ?: "unknown")
                        DetailRow("创建时间", instance.createdAt ?: "unknown")
                        DetailRow("在线玩家", instance.playerCount.toString())

                        Spacer(modifier = Modifier.height(4.dp))

                        // Scheduling constraints quick view
                        if (constraintsError != null) {
                            Text(
                                text = "调度约束加载失败: $constraintsError",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            schedulingConstraints?.let { c ->
                                Text(
                                    text = "调度约束 · 最低节点分 ${"%.0f".format(c.minNodeScore)} · 优先级 ${c.priorityClass}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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

            // --- Log control bar ---
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pause / Resume
                        IconButton(onClick = { isPaused = !isPaused }) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "继续" else "暂停",
                                tint = if (isPaused) MaterialTheme.colorScheme.tertiary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Follow-tail toggle
                        IconButton(
                            onClick = { followTail = !followTail },
                            enabled = !isPaused
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (followTail) "自动跟随：已开启" else "自动跟随：已关闭",
                                tint = if (followTail) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        // Search toggle
                        IconButton(onClick = { showSearchField = !showSearchField }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索日志",
                                tint = if (showSearchField) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Export
                        IconButton(
                            onClick = {
                                createDocumentLauncher.launch("${instance.name}_logs.txt")
                            },
                            enabled = logs.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "导出日志",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- Search field (collapsible) ---
            if (showSearchField) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("筛选日志...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除筛选")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            // --- Paused indicator ---
            if (isPaused) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已暂停",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // --- Log title ---
            item {
                val countText = if (searchQuery.isNotBlank()) {
                    "${filteredLogs.size}/${logs.size} 行"
                } else {
                    "${logs.size} 行"
                }
                Text(
                    text = "控制台日志 ($countText)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // --- Placeholders for server-side states ---
            listStatePlaceholders(
                isLoading = logsLoading,
                error = logsError,
                isEmpty = logs.isEmpty(),
                emptyText = "暂无日志",
                errorPrefix = "加载日志失败"
            )

            // --- No-match indicator when filter exists but yields no results ---
            if (logs.isNotEmpty() && filteredLogs.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Text(
                        text = "无匹配日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // --- Log lines ---
            itemsIndexed(filteredLogs, key = { index, _ -> "log_$index" }) { _, line ->
                val isMatch = searchQuery.isNotBlank() && line.contains(searchQuery, ignoreCase = true)
                Surface(
                    color = if (isMatch) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // --- Export status message ---
            if (exportStatus != null) {
                item {
                    Surface(
                        color = if (exportStatus!!.startsWith("已导出")) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (exportStatus!!.startsWith("已导出")) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Error
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (exportStatus!!.startsWith("已导出")) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = exportStatus!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (exportStatus!!.startsWith("已导出")) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }
            }

            // Bottom spacer so the last line is not flush with the screen edge
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
