package com.jlucraft.console.ui.screens.oracle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jlucraft.console.data.model.OracleProof
import com.jlucraft.console.data.model.OracleScore
import com.jlucraft.console.data.model.OracleVerificationResult
import com.jlucraft.console.ui.components.listStatePlaceholders
import com.jlucraft.console.ui.theme.StatusAmber
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.viewmodel.OracleViewModel

/**
 * Oracle proof verification screen.
 *
 * Allows the user to:
 *   1. Enter a player ID
 *   2. Fetch the oracle score + Merkle proof from GET /v1/oracle/scores/{player_id}
 *   3. Display the proof details: root, proof nodes, leaf index, leaf hash
 *   4. Show local (client-side) Merkle proof verification result
 *   5. Show server-local verification status
 *   6. Re-verify locally
 *
 * This composable can be embedded in any screen (League, Settings, etc.)
 * or displayed as a standalone dialog/sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OracleProofPanel(
    viewModel: OracleViewModel,
    onDismiss: (() -> Unit)? = null
) {
    val state = viewModel.uiState.value
    var playerIdInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header (only when embedded without parent Scaffold) ──
        if (onDismiss == null) {
            Text(
                text = "预言机证明验证",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // ── Player ID input ──
        OutlinedTextField(
            value = playerIdInput,
            onValueChange = { playerIdInput = it },
            label = { Text("玩家 ID") },
            placeholder = { Text("输入玩家 peer_id 或 DID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (playerIdInput.isNotBlank()) {
                            viewModel.loadOracleScore(playerIdInput.trim())
                        }
                    },
                    enabled = playerIdInput.isNotBlank() && !state.isLoading
                ) {
                    Icon(Icons.Default.Search, contentDescription = "查询")
                }
            }
        )

        // ── Loading state ──
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // ── Error state ──
        state.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // ── Oracle Score Display ──
        state.oracleScore?.let { score ->
            OracleScoreCard(score)
            Spacer(modifier = Modifier.height(8.dp))
            ProofDetailCard(score.proof)
            Spacer(modifier = Modifier.height(8.dp))
            VerificationResultCard(state.verificationResult, score.serverVerified)

            // Re-verify button
            OutlinedButton(
                onClick = { viewModel.verifyLocally() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新本地验证")
            }
        }
    }
}

@Composable
private fun OracleScoreCard(score: OracleScore) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "玩家分数",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow("Player ID", score.playerId)
            DetailRow("综合分数", "%.2f".format(score.aggregateScore))
            if (score.scores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "分类分数:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                score.scores.forEach { (gameType, gameScore) ->
                    DetailRow("  $gameType", "%.2f".format(gameScore))
                }
            }
            DetailRow("验证时间", score.verifiedAt)
        }
    }
}

@Composable
private fun ProofDetailCard(proof: OracleProof) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Merkle 证明",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow("算法", proof.algorithm)
            DetailRow("Merkle Root", proof.root.take(16) + "...")
            DetailRow("叶子索引", proof.leafIndex.toString())
            DetailRow("叶子哈希", proof.leafHash.take(16) + "...")
            DetailRow("证明节点数", proof.proofNodes.size.toString())
            if (proof.proofNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "证明路径:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                proof.proofNodes.forEachIndexed { index, node ->
                    Text(
                        text = "  [$index] ${node.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationResultCard(
    verificationResult: OracleVerificationResult?,
    serverVerified: Boolean
) {
    val localColor = when {
        verificationResult == null -> MaterialTheme.colorScheme.onSurfaceVariant
        verificationResult.locallyVerified -> StatusGreen
        else -> MaterialTheme.colorScheme.error
    }
    val localIcon = when {
        verificationResult == null -> Icons.Default.Error
        verificationResult.locallyVerified -> Icons.Default.CheckCircle
        else -> Icons.Default.Error
    }

    val serverColor = if (serverVerified) StatusGreen else StatusAmber
    val serverIcon = if (serverVerified) Icons.Default.CheckCircle else Icons.Default.Error

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "验证状态",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Local verification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(localIcon, contentDescription = null, tint = localColor, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        text = "本地验证: ${if (verificationResult?.locallyVerified == true) "通过" else if (verificationResult == null) "未验证" else "失败"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = localColor
                    )
                    verificationResult?.computedRoot?.takeIf { it.isNotEmpty() }?.let { root ->
                        Text(
                            text = "计算根: ${root.take(16)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server verification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(serverIcon, contentDescription = null, tint = serverColor, modifier = Modifier.size(20.dp))
                Text(
                    text = "服务端验证: ${if (serverVerified) "已确认" else "未确认"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = serverColor
                )
            }

            // Messages
            verificationResult?.messages?.takeIf { it.isNotEmpty() }?.let { messages ->
                Spacer(modifier = Modifier.height(8.dp))
                messages.forEach { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber
                    )
                }
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
