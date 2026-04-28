package com.jlucraft.console.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.jlucraft.console.ui.theme.StatusGreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun String.toShortDate(): String = take(10)

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String =
    if (length > maxLength) take(maxLength) + ellipsis else this

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

val Instance.isStopped: Boolean get() = status.equals("stopped", ignoreCase = true)

fun Instance.kindText(): String = if (kind == "service") "服务" else "房间"

@Composable
fun Tournament.statusColor(): Color = when (status.lowercase()) {
    "ongoing" -> MaterialTheme.colorScheme.primary
    "registration" -> StatusGreen
    "completed" -> MaterialTheme.colorScheme.outline
    "draft" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.outline
}

fun Tournament.statusText(): String = when (status.lowercase()) {
    "ongoing" -> "进行中"
    "registration" -> "报名中"
    "completed" -> "已结束"
    "draft" -> "草稿"
    else -> status
}

@Composable
fun Proposal.statusColor(): Color = when (status) {
    "pending" -> MaterialTheme.colorScheme.primary
    "approved" -> StatusGreen
    "executed" -> com.jlucraft.console.ui.theme.StatusBlue
    "rejected", "expired" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

fun Proposal.statusText(): String = when (status) {
    "pending" -> "待签名"
    "approved" -> "已通过"
    "executed" -> "已执行"
    "rejected" -> "已拒绝"
    "expired" -> "已过期"
    else -> status
}

fun Proposal.displayType(): String = proposalType.replace("-", " ").uppercase()

@Composable
fun AuditEntry.outcomeColor(): Color = when (outcome) {
    "success" -> StatusGreen
    "rejected" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

fun Match.statusText(): String = when (status) {
    "scheduled" -> "已安排"
    "live" -> "进行中"
    "finished" -> "已结束"
    "disputed" -> "争议中"
    else -> status
}

@Serializable
data class Node(
    val peerId: String,
    val role: String,
    val status: String,
    val cpuUsage: Float,
    val memoryUsage: Float,
    val diskUsage: Float,
    val instanceCount: Int,
    val nodeScore: Float,
    val labels: Map<String, String>,
    val lastSeenAt: String
)

@Serializable
data class Instance(
    val id: String,
    val name: String,
    val kind: String,
    val status: String,
    @SerialName("current_host") val currentHost: String,
    @SerialName("player_count") val playerCount: Int = 0,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ProposalSignature(
    val pubkey: String,
    val signature: String,
    @SerialName("signed_at") val signedAt: String
)

@Serializable
data class Proposal(
    val id: String,
    @SerialName("proposal_type") val proposalType: String,
    val payload: kotlinx.serialization.json.JsonObject,
    val proposer: String,
    @SerialName("expires_at") val expiresAt: String,
    val signatures: List<ProposalSignature>,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("executed_at") val executedAt: String? = null
)

@Serializable
data class AuditSignature(
    val pubkey: String,
    val signature: String,
    @SerialName("signed_at") val signedAt: String
)

@Serializable
data class AuditEntry(
    val id: Long,
    val ts: Long,
    @SerialName("actor_pubkey") val actorPubkey: String,
    @SerialName("actor_role") val actorRole: String,
    @SerialName("cmd_type") val cmdType: String,
    val target: String,
    @SerialName("payload_hash") val payloadHash: String,
    val signatures: List<AuditSignature>,
    val outcome: String,
    val error: String? = null,
    @SerialName("prev_hash") val prevHash: String
)

@Serializable
data class AuditChainVerification(
    @SerialName("valid") val valid: Boolean,
    @SerialName("broken_entries") val brokenEntries: List<Long>
)

@Serializable
data class AuditAnomaly(
    val rule: String,
    val actor: String,
    val detail: String,
    @SerialName("detected_at") val detectedAt: Long
)

@Serializable
data class Alert(
    val id: String,
    @SerialName("alert_type") val alertType: String,
    val severity: String,
    val message: String,
    val target: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null
)

@Serializable
data class NodeScore(
    val peer_id: String,
    val uptime_score: Double,
    val performance_score: Double,
    val governance_score: Double,
    val penalty: Double,
    val final_score: Double,
    val last_updated: String,
    /** Optional resource usage (populated when scheduler data is available). */
    val cpu_usage: Float? = null,
    val memory_usage: Float? = null,
    val disk_usage: Float? = null,
    /** Optional node labels from the cluster registry. */
    val labels: Map<String, String>? = null
)

@Serializable
data class Tournament(
    val id: String,
    val name: String,
    val game_type: String,
    val mode: String,
    val status: String,
    val max_participants: Int,
    val participant_count: Int = 0,
    val min_member_score: Int,
    val created_at: String,
    val created_by: String
)

@Serializable
data class Match(
    val id: String,
    val tournament_id: String,
    val round: Int,
    val participants: List<String>,
    val status: String,
    val scheduled_at: String
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val members: List<String>,
    val total_score: Int
)

@Serializable
data class MatchSchedule(
    val round: Int,
    val datetime: String,
    val map: String
)

@Serializable
data class TournamentSchedule(
    val registration_open: String,
    val registration_close: String,
    val matches: List<MatchSchedule> = emptyList()
)

@Serializable
data class ScoringRules(
    val win: Int = 10,
    val kill: Int = 2,
    val survive_minute: Double = 0.5,
    val placement_1: Int = 10,
    val placement_2: Int = 7,
    val placement_3: Int = 5
)

@Serializable
data class CreateTournamentRequest(
    val name: String,
    val game_type: String,
    val mode: String,
    val schedule: TournamentSchedule,
    val scoring: ScoringRules = ScoringRules(),
    val min_member_score: Int = 0,
    val max_participants: Int,
    val created_by: String
)

@Serializable
data class Season(
    val id: String,
    val name: String,
    val start_date: String,
    val end_date: String? = null,
    val tournament_ids: List<String> = emptyList(),
    val status: String,
    val created_at: String? = null
)

@Serializable
data class LeaderboardEntry(
    val player_id: String,
    val total_score: Double,
    val tournaments_played: Int
)

@Serializable
data class TeamLeaderboardEntry(
    val team_id: String,
    val team_name: String,
    val total_score: Double,
    val tournaments_played: Int
)

@Serializable
data class Leaderboard(
    val solo: List<LeaderboardEntry> = emptyList(),
    val team: List<TeamLeaderboardEntry> = emptyList()
)

@Serializable
data class Device(
    val pubkey: String,
    val status: String, // "active", "revoked"
    val platform: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("revoked_reason") val revokedReason: String? = null,
    @SerialName("revoked_by") val revokedBy: String? = null
)
