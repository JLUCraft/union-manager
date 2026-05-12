package com.jlucraft.console.ui.screens.audit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.model.AuditSignature
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.components.outcomeColor
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.AuditLogViewModel
import com.jlucraft.console.viewmodel.LocalAnomaly
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val auditDateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())


private val resultOptions = listOf(
    "all" to "全部",
    "success" to "成功",
    "rejected" to "拒绝",
    "error" to "错误"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    services: AppServices,
    viewModel: AuditLogViewModel = viewModel(),
) {
    val state = viewModel.uiState.value
    val entries = state.entries
    val displayEntries = if (state.isFiltered) state.filteredEntries else entries


    var timeStartText by remember(state.timeRangeStart) {
        mutableStateOf(state.timeRangeStart?.toString() ?: "")
    }
    var timeEndText by remember(state.timeRangeEnd) {
        mutableStateOf(state.timeRangeEnd?.toString() ?: "")
    }
    var triggerText by remember(state.triggerFilter) {
        mutableStateOf(state.triggerFilter ?: "")
    }
    var targetText by remember(state.targetFilter) {
        mutableStateOf(state.targetFilter ?: "")
    }
    var searchText by remember(state.searchText) {
        mutableStateOf(state.searchText ?: "")
    }


    val cmdTypeOptions = remember(entries) {
        listOf(null to "全部") + entries.map { it.cmdType }.distinct().sorted().map { it to it }
    }


    val selectedEntry = state.selectedEntry
    if (selectedEntry != null) {
        AuditDetailSheet(
            entry = selectedEntry,
            onDismiss = { viewModel.clearSelection() }
        )
    }

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
                    val text = if (verification.valid) "审计链完整" else "审计链断裂 (${verification.brokenCount} 条)"

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


            val localAnomalies = state.localAnomalies
            if (localAnomalies.isNotEmpty()) {
                item {
                    Text(
                        text = "本地异常检测 (${localAnomalies.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(localAnomalies) { anomaly ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "异常",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = anomaly.type,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = anomaly.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }


            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Text("时间范围 (epoch 毫秒)", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = timeStartText,
                                onValueChange = { timeStartText = it },
                                placeholder = { Text("起始", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = timeEndText,
                                onValueChange = { timeEndText = it },
                                placeholder = { Text("结束", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterDropdown(
                                label = "类型",
                                selectedValue = state.cmdTypeFilter,
                                options = cmdTypeOptions.map { it.first to it.second },
                                onSelect = { viewModel.setCmdTypeFilter(it) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterDropdown(
                                label = "结果",
                                selectedValue = state.resultFilter,
                                options = resultOptions,
                                onSelect = { viewModel.setResultFilter(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = triggerText,
                                onValueChange = { triggerText = it },
                                label = { Text("触发者", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("公钥", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = targetText,
                                onValueChange = { targetText = it },
                                label = { Text("目标", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }


                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            label = { Text("全文检索", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall
                        )


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setTimeRange(
                                        start = timeStartText.toLongOrNull(),
                                        end = timeEndText.toLongOrNull()
                                    )
                                    viewModel.setTriggerFilter(
                                        triggerText.ifBlank { null }
                                    )
                                    viewModel.setTargetFilter(
                                        targetText.ifBlank { null }
                                    )
                                    viewModel.setSearchText(
                                        searchText.ifBlank { null }
                                    )
                                    viewModel.applyFilters()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text("应用筛选", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    timeStartText = ""
                                    timeEndText = ""
                                    triggerText = ""
                                    targetText = ""
                                    searchText = ""
                                    viewModel.clearFilters()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Text("清除", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }


            item {
                val headerText = if (state.isFiltered) {
                    "显示 ${displayEntries.size}/${entries.size} 条记录"
                } else {
                    "最近记录 (${entries.size})"
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = displayEntries.isEmpty(),
                emptyText = if (state.isFiltered) "没有匹配的记录" else "暂无审计记录"
            )

            items(displayEntries, key = { it.id }) { entry ->
                AuditEntryCard(
                    entry = entry,
                    onClick = { viewModel.selectEntry(entry) }
                )
            }
        }
    }
}



@Composable
private fun AuditEntryCard(entry: AuditEntry, onClick: () -> Unit) {
    val outcomeColor = entry.outcomeColor()
    val dateStr = remember(entry.ts) { auditDateFormat.format(Instant.ofEpochMilli(entry.ts)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selectedValue: String?,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second ?: "全部"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditDetailSheet(
    entry: AuditEntry,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateStr = remember(entry.ts) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(entry.ts))
    }
    val outcomeColor = entry.outcomeColor()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "审计条目 #${entry.id}",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DetailRow("ID", entry.id.toString())
            DetailRow("命令类型", entry.cmdType)
            DetailRow("时间", dateStr)
            DetailRow("目标", entry.target)
            DetailRow("执行者", entry.actorPubkey)
            DetailRow("执行者角色", entry.actorRole)
            DetailRow("结果", entry.outcome)
            DetailRow("Payload Hash", entry.payloadHash)
            DetailRow("前驱 Hash", entry.prevHash.ifEmpty { "（无 - 创世条目）" })

            if (entry.error != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "错误信息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = entry.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (entry.signatures.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "签名 (${entry.signatures.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                entry.signatures.forEachIndexed { index, sig ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "签名 ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DetailRow("  公钥", sig.pubkey.truncate(32), compact = true)
                    DetailRow("  签名", sig.signature.truncate(32), compact = true)
                    DetailRow("  签名时间", sig.signedAt, compact = true)
                }
            }
        }
    }
}
