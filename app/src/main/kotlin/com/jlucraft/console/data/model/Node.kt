package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun String.toShortDate(): String = take(10)

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String =
    if (length > maxLength) take(maxLength) + ellipsis else this

// ── Tournament / Match status enums (aligned with federated-server league.rs) ──

@Serializable
enum class TournamentStatus {
    @SerialName("draft") Draft,
    @SerialName("registration") Registration,
    @SerialName("ongoing") Ongoing,
    @SerialName("paused") Paused,
    @SerialName("cancelled") Cancelled,
    @SerialName("completed") Completed;
}

@Serializable
enum class MatchStatus {
    @SerialName("scheduled") Scheduled,
    @SerialName("live") Live,
    @SerialName("finished") Finished,
    @SerialName("disputed") Disputed;
}

// ── Instance helpers ──

val Instance.isStopped: Boolean get() = status.equals("stopped", ignoreCase = true)

fun Instance.kindText(): String = if (kind == "service") "服务" else "房间"

fun Tournament.statusText(): String = when (status) {
    TournamentStatus.Ongoing -> "进行中"
    TournamentStatus.Registration -> "报名中"
    TournamentStatus.Completed -> "已结束"
    TournamentStatus.Draft -> "草稿"
    TournamentStatus.Paused -> "已暂停"
    TournamentStatus.Cancelled -> "已取消"
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

fun Match.statusText(): String = when (status) {
    MatchStatus.Scheduled -> "已安排"
    MatchStatus.Live -> "进行中"
    MatchStatus.Finished -> "已结束"
    MatchStatus.Disputed -> "争议中"
}

/**
 * A federated-server cluster node as seen by the union-manager admin console.
 *
 * The [peerId] field is the **federated-server node's** libp2p identity
 * (Ed25519-derived PeerId). The union-manager is itself a libp2p peer that
 * communicates via protobuf [ControlRequest]/[ControlResponse] over libp2p
 * streams on the `/control/v1` protocol.
 *
 * [peerId] is used:
 *  - For display/identification in admin dashboards.
 *  - As a stable reference when issuing governance commands (proposal targeting,
 *    credential revocation, instance migration).
 *  - To correlate node health/score data.
 */
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
data class AdmissionPolicy(
    /** Valid values: "public", "vc-only", "mua-member", "club-only" */
    val mode: String,
    val allowed_clubs: List<String> = emptyList(),
    val allowed_players: List<String> = emptyList(),
    val requires_verified_email: Boolean = false,
    val allowed_email_domains: List<String> = emptyList()
) {
    companion object {
        const val MODE_PUBLIC = "public"
        const val MODE_VC_ONLY = "vc-only"
        const val MODE_MUA_MEMBER = "mua-member"
        const val MODE_CLUB_ONLY = "club-only"
    }
}

@Serializable
data class InstanceRuntimeSpec(
    val image: String,
    val command: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    @SerialName("working_dir") val workingDir: String = "",
    @SerialName("data_mount_path") val dataMountPath: String = "",
    @SerialName("log_path") val logPath: String = ""
)

@Serializable
data class ResourceRequest(
    @SerialName("cpu_cores") val cpuCores: Int,
    @SerialName("memory_gb") val memoryGb: Int,
    @SerialName("disk_gb") val diskGb: Int
)

@Serializable
data class MigrationProgress(
    val phase: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("pre_dump_key") val preDumpKey: String? = null,
    @SerialName("final_dump_key") val finalDumpKey: String? = null,
    val error: String? = null,
    val trigger: String? = null
)

@Serializable
data class CreateInstanceRequest(
    val name: String,
    val kind: String,
    val owner: String,
    val club: String,
    val runtime: InstanceRuntimeSpec,
    val resources: ResourceRequest,
    @SerialName("auto_restart") val autoRestart: Boolean = false,
    val admission: AdmissionPolicy = AdmissionPolicy(mode = "public")
)

@Serializable
data class Instance(
    val id: String,
    val name: String,
    val kind: String,
    val status: String,
    val owner: String? = null,
    val club: String? = null,
    @SerialName("current_host") val currentHost: String? = null,
    @SerialName("player_count") val playerCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("host_port") val hostPort: Int? = null,
    @SerialName("rcon_port") val rconPort: Int? = null,
    @SerialName("auto_restart") val autoRestart: Boolean? = null,
    @SerialName("migration_target") val migrationTarget: String? = null,
    @SerialName("migration_progress") val migrationProgress: MigrationProgress? = null,
    val admission: AdmissionPolicy? = null,
    val runtime: InstanceRuntimeSpec? = null,
    val resources: ResourceRequest? = null
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
    val payload: ProposalPayload,
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
    @SerialName("broken_count") val brokenCount: Int
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
    val status: TournamentStatus,
    val max_participants: Int,
    val participant_count: Int = 0,
    val min_member_score: Int,
    val created_at: String,
    val created_by: String,
    /** Optional server-side schedule fields populated when available. */
    val schedule: TournamentSchedule? = null,
    val scoring: ScoringRules? = null
)

@Serializable
data class Match(
    val id: String,
    val tournament_id: String,
    val round: Int,
    val participants: List<String>,
    val status: MatchStatus,
    val scheduled_at: String,
    val instance_id: String? = null,
    val result: MatchResult? = null
)

@Serializable
data class MatchResult(
    val rankings: List<PlayerResult> = emptyList()
)

@Serializable
data class PlayerResult(
    val player_id: String,
    val score: Double,
    val kills: Int,
    val deaths: Int,
    val survive_minutes: Double
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
    @SerialName("owner_peer_id") val ownerPeerId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("registered_by") val registeredBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("revoked_reason") val revokedReason: String? = null,
    @SerialName("revoked_by") val revokedBy: String? = null
)
