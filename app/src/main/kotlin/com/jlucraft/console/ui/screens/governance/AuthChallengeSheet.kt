package com.jlucraft.console.ui.screens.governance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.ui.theme.StatusAmber
import com.jlucraft.console.ui.theme.StatusGreen
import com.jlucraft.console.ui.theme.StatusRed


 *
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthChallengeSheet(
    challenge: AuthChallenge,
    countdownSeconds: Long,
    isExecuting: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "安全验证",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "操作确认",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }


            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "操作摘要",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = challenge.human_summary.ifEmpty { "无摘要" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }


            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow("命令类型", challenge.cmd_type)
                    DetailRow("风险等级", challenge.risk_level.uppercase(), riskColor = riskLevelColor(challenge.risk_level))
                    DetailRow("所需角色", challenge.required_role.ifEmpty { "无要求" })
                    DetailRow("Payload Hash", challenge.payload_hash.take(12) + "...")
                }
            }


            val countdownColor = when {
                countdownSeconds > 30 -> StatusGreen
                countdownSeconds > 10 -> StatusAmber
                else -> StatusRed
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = countdownColor.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "倒计时",
                        tint = countdownColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "挑战将在 ${countdownSeconds}s 后过期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = countdownColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isExecuting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !isExecuting
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Gavel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("确认签名")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    riskColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val displayColor = riskColor ?: MaterialTheme.colorScheme.onSurface
        Surface(
            color = if (riskColor != null) riskColor.copy(alpha = 0.12f) else displayColor.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = displayColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}


@Composable
private fun riskLevelColor(riskLevel: String): androidx.compose.ui.graphics.Color {
    return when (riskLevel.lowercase()) {
        "low" -> StatusGreen
        "medium" -> StatusAmber
        "high" -> MaterialTheme.colorScheme.error
        "critical" -> StatusRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
