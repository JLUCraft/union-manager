package com.jlucraft.console.ui.screens.governance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.data.auth.AuthStateHolder
import com.jlucraft.console.data.auth.ReadOnlyMode
import com.jlucraft.console.data.model.MemberSummary
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ProposalSignature
import com.jlucraft.console.data.model.TestAuthChallengePayload
import com.jlucraft.console.data.model.description
import com.jlucraft.console.data.model.displayType
import com.jlucraft.console.data.model.statusText
import com.jlucraft.console.data.model.truncate
import com.jlucraft.console.ui.components.statusColor
import com.jlucraft.console.ui.components.DetailRow
import com.jlucraft.console.ui.components.ReadOnlyModeBanner
import com.jlucraft.console.ui.components.StatusChip
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.*
import com.jlucraft.console.viewmodel.CommandViewModel
import com.jlucraft.console.viewmodel.GovernanceViewModel
import com.jlucraft.console.viewmodel.StreamStatus


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernanceScreen(
    services: AppServices,
    viewModel: GovernanceViewModel = viewModel(),
) {
    val state = viewModel.uiState.value
    val readOnlyState by AuthStateHolder.readOnlyMode.collectAsState()
    val isReadOnly = readOnlyState is ReadOnlyMode.ReadOnly

    val commandViewModel: CommandViewModel = viewModel()
    val commandState = commandViewModel.uiState.value

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

                    when (state.streamStatus) {
                        StreamStatus.CONNECTED -> {
                            TextButton(onClick = {}) {
                                Text("● 实时", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        StreamStatus.CONNECTING,
                        StreamStatus.RECONNECTING -> {
                            TextButton(onClick = {}) {
                                Text("◌ 重连中", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        StreamStatus.OFFLINE -> {
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text("○ 离线", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.activeTab == "proposals" && !isReadOnly) {
                FloatingActionButton(onClick = { viewModel.showCreateDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "创建提案")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            PrimaryTabRow(selectedTabIndex = if (state.activeTab == "members") 1 else 0) {
                Tab(
                    selected = state.activeTab == "proposals",
                    onClick = { viewModel.setActiveTab("proposals") },
                    text = { Text("提案") }
                )
                Tab(
                    selected = state.activeTab == "members",
                    onClick = { viewModel.setActiveTab("members") },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("成员")
                        }
                    }
                )
            }

            if (isReadOnly) {
                ReadOnlyModeBanner(
                    readOnlyState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (state.lastEvent != null) {
                StreamActivityBanner(state = state)
            }

            when (state.activeTab) {
                "members" -> MembersTab(viewModel, state, readOnly = isReadOnly)
                else -> ProposalsTab(viewModel, state, commandViewModel, readOnly = isReadOnly)
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
            readOnly = isReadOnly,
            onDismiss = { viewModel.dismissProposalDetail() },
            onSign = { viewModel.signSelectedProposal() },
            onExecute = { viewModel.executeProposal(proposal.id) },
            onSubmitDraft = { viewModel.submitDraft(proposal.id) },
            onReject = { viewModel.rejectProposal(proposal.id) }
        )
    }


    commandState.challenge?.let { challenge ->
        AuthChallengeSheet(
            challenge = challenge,
            countdownSeconds = commandState.countdownSeconds,
            isExecuting = commandState.isExecuting,
            onConfirm = { commandViewModel.confirmChallenge() },
            onCancel = { commandViewModel.cancelChallenge() }
        )
    }


    commandState.resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { commandViewModel.clearResult() },
            title = { Text("命令结果") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { commandViewModel.clearResult() }) {
                    Text("确定")
                }
            }
        )
    }


    if (state.showVcResultSheet) {
        VcResultSheet(
            vcJson = state.issuedVcJson,
            vc = state.issuedVc,
            verificationResult = state.vcVerificationResult,
            onDismiss = { viewModel.dismissVcResult() }
        )
    }
}

@Composable
private fun StreamActivityBanner(state: com.jlucraft.console.viewmodel.GovernanceUiState) {
    val lastEvent = state.lastEvent ?: return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "最新治理事件：${lastEvent.eventType.name}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            state.lastEventTime?.let { time ->
                Text(
                    text = "时间：$time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            lastEvent.proposalId?.let { proposalId ->
                Text(
                    text = "提案：$proposalId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}



@Composable
private fun ProposalsTab(
    viewModel: GovernanceViewModel,
    state: com.jlucraft.console.viewmodel.GovernanceUiState,
    commandViewModel: CommandViewModel,
    readOnly: Boolean
) {
    val proposals = state.proposals
    val statusCounts = remember(proposals) {
        proposals.groupingBy { it.status }.eachCount()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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


        item {
            OutlinedButton(
                onClick = {
                    commandViewModel.initiateCommand(
                        cmdType = "test-auth-challenge",
                        payload = TestAuthChallengePayload(
                            action = "demo_test",
                            description = "测试签名命令挑战流程"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !readOnly
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("测试签名挑战 (AuthChallengeSheet 演示)")
            }
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



@Composable
private fun MembersTab(
    viewModel: GovernanceViewModel,
    state: com.jlucraft.console.viewmodel.GovernanceUiState,
    readOnly: Boolean
) {
    val members = state.members

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "成员列表",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        listStatePlaceholders(
            isLoading = state.membersLoading,
            error = state.membersError,
            isEmpty = members.isEmpty() && !state.membersLoading,
            emptyText = "暂无成员"
        )

        items(members, key = { it.subjectDid }) { member ->
            MemberCard(member = member, viewModel = viewModel, readOnly = readOnly)
        }
    }
}

@Composable
private fun MemberCard(member: MemberSummary, viewModel: GovernanceViewModel, readOnly: Boolean) {
    var showRoleDialog by remember { mutableStateOf(false) }
    var showIssueDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    val roleColor = when (member.role) {
        "president" -> MaterialTheme.colorScheme.primary
        "admin" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.subjectDid.truncate(24),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Surface(
                    color = roleColor.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = member.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = roleColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!readOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showRoleDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("角色", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showIssueDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("签发", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showRevokeDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("吊销", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showRoleDialog) {
        RoleGrantDialog(
            currentRole = member.role,
            onDismiss = { showRoleDialog = false },
            onGrant = { role -> viewModel.grantRole(member.subjectDid, role); showRoleDialog = false }
        )
    }

    if (showIssueDialog) {
        AlertDialog(
            onDismissRequest = { showIssueDialog = false },
            title = { Text("签发凭证") },
            text = { Text("确认向 ${member.subjectDid.truncate(24)} 签发 VC？") },
            confirmButton = {
                TextButton(onClick = { viewModel.issueCredential(member.subjectDid); showIssueDialog = false }) {
                    Text("签发")
                }
            },
            dismissButton = { TextButton(onClick = { showIssueDialog = false }) { Text("取消") } }
        )
    }

    if (showRevokeDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text("吊销凭证") },
            text = {
                Column {
                    Text("吊销 ${member.subjectDid.truncate(24)} 的凭证后，该成员将无法再进行管理操作。")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("吊销原因") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.revokeCredential(member.subjectDid, reason); showRevokeDialog = false },
                    enabled = reason.isNotBlank()
                ) {
                    Text("吊销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showRevokeDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun RoleGrantDialog(
    currentRole: String,
    onDismiss: () -> Unit,
    onGrant: (String) -> Unit
) {
    val roles = listOf("admin", "member", "guest")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("授予角色") },
        text = {
            Column {
                Text("当前角色: $currentRole")
                Spacer(modifier = Modifier.height(12.dp))
                roles.forEach { role ->
                    if (role != currentRole) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(role)
                            TextButton(onClick = { onGrant(role) }) {
                                Text("授予")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
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
        proposal.payload.description.ifBlank { displayType }
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
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onSign: () -> Unit,
    onExecute: () -> Unit,
    onSubmitDraft: () -> Unit,
    onReject: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val displayType = remember(proposal.proposalType) { proposal.displayType() }
    val payloadStr = remember(proposal.payload) { proposal.payload.toString() }
    val description = remember(proposal.payload) {
        proposal.payload.description.ifBlank { "无描述" }
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
                    when (proposal.status) {
                        "draft" -> {
                            Button(
                                onClick = onSubmitDraft,
                                modifier = Modifier.weight(1f),
                                enabled = !readOnly
                            ) {
                                Text("提交草案")
                            }
                        }
                        "pending" -> {
                            Button(
                                onClick = onSign,
                                modifier = Modifier.weight(1f),
                                enabled = !isSigning && !readOnly
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
                            OutlinedButton(
                                onClick = onReject,
                                modifier = Modifier.weight(1f),
                                enabled = !readOnly,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("否决")
                            }
                        }
                        "approved" -> {
                            Button(
                                onClick = onExecute,
                                modifier = Modifier.weight(1f),
                                enabled = !readOnly
                            ) {
                                Text("执行提案")
                            }
                        }

                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
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
    onCreate: (String, com.jlucraft.console.data.model.ProposalPayload) -> Unit,
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
                    val payload = com.jlucraft.console.data.model.proposalPayloadFor(selectedType, description, target)
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
