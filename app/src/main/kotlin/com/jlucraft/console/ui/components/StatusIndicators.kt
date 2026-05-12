@file:Suppress("unused")

package com.jlucraft.console.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.model.TournamentStatus
import com.jlucraft.console.ui.theme.StatusBlue
import com.jlucraft.console.ui.theme.StatusGreen



@Composable
fun Instance.statusColor(): Color = when (status.lowercase()) {
    "running" -> StatusGreen
    "stopped" -> MaterialTheme.colorScheme.outline
    "migrating" -> MaterialTheme.colorScheme.primary
    "degraded" -> MaterialTheme.colorScheme.error
    "provisioning" -> MaterialTheme.colorScheme.tertiary
    "destroyed" -> MaterialTheme.colorScheme.outline
    "failed" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun Instance.statusText(): String = when (status.lowercase()) {
    "running" -> "运行中"
    "stopped" -> "已停止"
    "migrating" -> "迁移中"
    "degraded" -> "已降级"
    "provisioning" -> "预配中"
    "destroyed" -> "已销毁"
    "failed" -> "失败"
    else -> status
}



@Composable
fun Tournament.statusColor(): Color = when (status) {
    TournamentStatus.Ongoing -> MaterialTheme.colorScheme.primary
    TournamentStatus.Registration -> StatusGreen
    TournamentStatus.Completed -> MaterialTheme.colorScheme.outline
    TournamentStatus.Draft -> MaterialTheme.colorScheme.onSurfaceVariant
    TournamentStatus.Paused -> MaterialTheme.colorScheme.error
    TournamentStatus.Cancelled -> MaterialTheme.colorScheme.outline
}



@Composable
fun Proposal.statusColor(): Color = when (status) {
    "pending" -> MaterialTheme.colorScheme.primary
    "approved" -> StatusGreen
    "executed" -> StatusBlue
    "rejected", "expired" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}



@Composable
fun AuditEntry.outcomeColor(): Color = when (outcome) {
    "success" -> StatusGreen
    "rejected" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}
