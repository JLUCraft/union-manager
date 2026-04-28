package com.jlucraft.console.ui.screens.governance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ProposalSignature
import com.jlucraft.console.data.model.displayType
import com.jlucraft.console.data.model.statusColor
import com.jlucraft.console.data.model.statusText
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.StatusChip
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.GovernanceViewModel
import com.jlucraft.console.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernanceScreen(viewModel: GovernanceViewModel = viewModel(factory = ViewModelFactory())) {
    val state = viewModel.uiState.value
    val proposals = state.proposals
    val statusCounts = remember(proposals) {
        proposals.groupingBy { it.status }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("治理中心") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "创建提案")
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
                    StatusChip("待处理", statusCounts["pending"].toString(), MaterialTheme.colorScheme.primary)
                    StatusChip("已通过", statusCounts["approved"].toString(), StatusGreen)
                    StatusChip("已执行", statusCounts["executed"].toString(), StatusBlue)
                    StatusChip("已过期", statusCounts["expired"].toString(), MaterialTheme.colorScheme.error)
                }
            }

            item {
                Text(
                    text = "提案列表",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            listStatePlaceholders(
                isLoading = state.isLoading,
                error = state.error,
                isEmpty = proposals.isEmpty(),
                emptyText = "暂无提案"
            )

            items(proposals, key = { it.id }) { proposal ->
                ProposalCard(
                    proposal = proposal,
                    onClick = { viewModel.showProposalDetail(proposal) }
                )
            }
        }
    }

    if (state.showCreateDialog) {
        CreateProposalDialog(
            onDismiss = { viewModel.dismissCreateDialog() },
            onCreate = { type, payload ->
                viewModel.createProposal(type, payload)
            },
            error = state.createError
        )
    }

    state.selectedProposal?.let { proposal ->
        ProposalDetailBottomSheet(
            proposal = proposal,
            isSigning = state.isSigning,
            signError = state.signError,
            onDismiss = { viewModel.dismissProposalDetail() },
            onSign = { viewModel.signSelectedProposal() },
            onExecute = { viewModel.executeProposal(proposal.id) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalCard(
    proposal: Proposal,
    onClick: () -> Unit
) {
    val statusColor = proposal.statusColor()

    val statusIcon = when (proposal.status) {
        "pending" -> Icons.Default.Schedule
        "approved", "executed" -> Icons.Default.CheckCircle
        else -> Icons.Default.Pending
    }

    val displayType = remember(proposal.proposalType) { proposal.displayType() }
    val description = remember(proposal.payload, displayType) {
        proposal.payload["description"]?.toString() ?: displayType
    }
    val proposerLabel = remember(proposal.proposer) { proposal.proposer.truncate(20) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = proposal.statusText(),
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = proposal.statusText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "提议者: $proposerLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${proposal.signatures.size} 签名",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalDetailBottomSheet(
    proposal: Proposal,
    isSigning: Boolean,
    signError: String?,
    onDismiss: () -> Unit,
    onSign: () -> Unit,
    onExecute: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val displayType = remember(proposal.proposalType) { proposal.displayType() }
    val payloadStr = remember(proposal.payload) { proposal.payload.toString() }
    val description = remember(proposal.payload) {
        proposal.payload["description"]?.toString() ?: "无描述"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = displayType,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                DetailSection("描述") {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item {
                DetailSection("Payload") {
                    Text(
                        text = payloadStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            item {
                DetailSection("元数据") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailRow("提议者", proposal.proposer, compact = true)
                        DetailRow("创建时间", proposal.createdAt, compact = true)
                        DetailRow("过期时间", proposal.expiresAt, compact = true)
                        DetailRow("状态", proposal.statusText(), compact = true)
                    }
                }
            }

            item {
                DetailSection("签名记录 (${proposal.signatures.size})") {
                    if (proposal.signatures.isEmpty()) {
                        Text(
                            text = "暂无签名",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            proposal.signatures.forEach { sig ->
                                SignatureRow(sig)
                            }
                        }
                    }
                }
            }

            if (signError != null) {
                item {
                    Text(
                        text = signError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (proposal.status == "pending") {
                        Button(
                            onClick = onSign,
                            modifier = Modifier.weight(1f),
                            enabled = !isSigning
                        ) {
                            if (isSigning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("签名提案")
                            }
                        }
                    }

                    if (proposal.status == "approved") {
                        Button(
                            onClick = onExecute,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("执行提案")
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = if (proposal.status == "pending" || proposal.status == "approved") Modifier.weight(1f) else Modifier.fillMaxWidth()
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SignatureRow(signature: ProposalSignature) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = signature.pubkey.truncate(16),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = signature.signedAt.truncate(16),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProposalDialog(
    onDismiss: () -> Unit,
    onCreate: (String, kotlinx.serialization.json.JsonObject) -> Unit,
    error: String?
) {
    var selectedType by rememberSaveable { mutableStateOf("add-node") }
    var description by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }

    val proposalTypes = remember {
        listOf(
            "add-node" to "添加节点",
            "remove-node" to "移除节点",
            "grant-admin" to "授予管理员",
            "revoke-admin" to "撤销管理员",
            "update-config" to "更新配置",
            "payout-reward" to "发放奖励",
            "emergency-revoke" to "紧急吊销",
            "update-governance-params" to "更新治理参数"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建提案") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = proposalTypes.find { it.first == selectedType }?.second ?: selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("提案类型") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        proposalTypes.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = value
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("目标 (节点ID / 用户ID / 配置项)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val payload = kotlinx.serialization.json.JsonObject(
                        mapOf(
                            "description" to kotlinx.serialization.json.JsonPrimitive(description),
                            "target" to kotlinx.serialization.json.JsonPrimitive(target)
                        )
                    )
                    onCreate(selectedType, payload)
                },
                enabled = description.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
