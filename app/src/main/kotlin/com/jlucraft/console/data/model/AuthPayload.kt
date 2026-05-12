package com.jlucraft.console.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put


 *
@Serializable(with = AuthPayloadSerializer::class)
sealed interface AuthPayload {
    val cmdType: String

    val summary: String get() = cmdType
}

@Serializable
data class StartInstancePayload(
    @SerialName("instance_id") val instanceId: String
) : AuthPayload {
    override val cmdType: String get() = "start-instance"
    override val summary: String get() = "启动实例 $instanceId"
}

@Serializable
data class StopInstancePayload(
    @SerialName("instance_id") val instanceId: String
) : AuthPayload {
    override val cmdType: String get() = "stop-instance"
    override val summary: String get() = "停止实例 $instanceId"
}

@Serializable
data class StopInstanceWithReasonPayload(
    @SerialName("instance_id") val instanceId: String,
    val reason: String
) : AuthPayload {
    override val cmdType: String get() = "stop-instance"
    override val summary: String get() = "停止实例 $instanceId ($reason)"
}

@Serializable
data class CreateInstancePayload(
    val name: String,
    val kind: String,
    val owner: String,
    val club: String
) : AuthPayload {
    override val cmdType: String get() = "create-instance"
    override val summary: String get() = "创建实例 $name"
}

@Serializable
data class MigrateInstancePayload(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("target_host") val targetHost: String? = null
) : AuthPayload {
    override val cmdType: String get() = "migrate-instance"
    override val summary: String get() = "迁移实例 $instanceId"
}

@Serializable
data class UpdateInstanceConfigPayload(
    @SerialName("instance_id") val instanceId: String,
    val config: Map<String, String>
) : AuthPayload {
    override val cmdType: String get() = "update-instance-config"
    override val summary: String get() = "更新实例配置 $instanceId"
}

@Serializable
data class BatchOperationPayload(
    @SerialName("instance_ids") val instanceIds: String,
    val count: Int,
    val operation: String
) : AuthPayload {
    override val cmdType: String get() = "batch"
    override val summary: String get() = "批量操作 ($count 个实例)"
}

@Serializable
data class CreateTournamentPayload(
    val name: String,
    @SerialName("game_type") val gameType: String,
    val mode: String,
    @SerialName("max_participants") val maxParticipants: Int,
    @SerialName("min_member_score") val minMemberScore: Int,
    @SerialName("registration_open") val registrationOpen: String,
    @SerialName("registration_close") val registrationClose: String,
    @SerialName("created_by") val createdBy: String
) : AuthPayload {
    override val cmdType: String get() = "create-tournament"
    override val summary: String get() = "创建赛事 $name"
}

@Serializable
data class UpdateTournamentStatusPayload(
    @SerialName("tournament_id") val tournamentId: String,
    val status: String
) : AuthPayload {
    override val cmdType: String get() = "update-tournament-status"
    override val summary: String get() = "更新赛事状态 $tournamentId → $status"
}

@Serializable
data class ArchiveSeasonPayload(
    @SerialName("season_id") val seasonId: String
) : AuthPayload {
    override val cmdType: String get() = "archive-season"
    override val summary: String get() = "归档赛季 $seasonId"
}

@Serializable
data class CreateSeasonPayload(
    val name: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String
) : AuthPayload {
    override val cmdType: String get() = "create-season"
    override val summary: String get() = "创建赛季 $name"
}

@Serializable
data class CreateDisputePayload(
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("match_id") val matchId: String,
    val reason: String
) : AuthPayload {
    override val cmdType: String get() = "create-dispute"
    override val summary: String get() = "创建争议 $matchId"
}

@Serializable
data class PauseMatchPayload(
    @SerialName("match_id") val matchId: String
) : AuthPayload {
    override val cmdType: String get() = "pause-match"
    override val summary: String get() = "暂停比赛 $matchId"
}

@Serializable
data class ResumeMatchPayload(
    @SerialName("match_id") val matchId: String
) : AuthPayload {
    override val cmdType: String get() = "resume-match"
    override val summary: String get() = "恢复比赛 $matchId"
}

@Serializable
data class ResetMatchPayload(
    @SerialName("match_id") val matchId: String
) : AuthPayload {
    override val cmdType: String get() = "reset-match"
    override val summary: String get() = "重置比赛 $matchId"
}

@Serializable
data class JudgeMatchPayload(
    @SerialName("match_id") val matchId: String,
    @SerialName("winner_id") val winnerId: String,
    val reason: String
) : AuthPayload {
    override val cmdType: String get() = "judge-match"
    override val summary: String get() = "判定比赛 $matchId 胜负"
}

@Serializable
data class AcknowledgeAlertPayload(
    @SerialName("alert_id") val alertId: String
) : AuthPayload {
    override val cmdType: String get() = "acknowledge-alert"
    override val summary: String get() = "确认警报 $alertId"
}

@Serializable
data class ResolveAlertPayload(
    @SerialName("alert_id") val alertId: String
) : AuthPayload {
    override val cmdType: String get() = "resolve-alert"
    override val summary: String get() = "解决警报 $alertId"
}

@Serializable
data class ApplySchedulingConstraintsPayload(
    @SerialName("instance_id") val instanceId: String,
    val reason: String
) : AuthPayload {
    override val cmdType: String get() = "apply-scheduling-constraints"
    override val summary: String get() = "应用调度约束 $instanceId"
}

@Serializable
data class RevokeDevicePayload(
    @SerialName("target_pubkey") val targetPubkey: String,
    val reason: String,
    @SerialName("revoked_by") val revokedBy: String
) : AuthPayload {
    override val cmdType: String get() = "revoke-device"
    override val summary: String get() = "吊销设备 $targetPubkey"
}

@Serializable
data class EmergencyRevokeDevicePayload(
    @SerialName("target_pubkey") val targetPubkey: String,
    val reason: String,
    @SerialName("revoked_by") val revokedBy: String
) : AuthPayload {
    override val cmdType: String get() = "emergency-revoke-device"
    override val summary: String get() = "紧急吊销设备 $targetPubkey"
}

@Serializable
data class CreateProposalAuthPayload(
    @SerialName("proposal_type") val proposalType: String,
    val payload: ProposalPayload,
    val proposer: String
) : AuthPayload {
    override val cmdType: String get() = "create-proposal"
    override val summary: String get() = "创建提案 $proposalType"
}

@Serializable
data class SubmitProposalDraftAuthPayload(
    @SerialName("proposal_id") val proposalId: String
) : AuthPayload {
    override val cmdType: String get() = "submit-proposal-draft"
    override val summary: String get() = "提交草案 $proposalId"
}

@Serializable
data class RejectProposalAuthPayload(
    @SerialName("proposal_id") val proposalId: String
) : AuthPayload {
    override val cmdType: String get() = "reject-proposal"
    override val summary: String get() = "否决提案 $proposalId"
}

@Serializable
data class ExecuteProposalAuthPayload(
    @SerialName("proposal_id") val proposalId: String
) : AuthPayload {
    override val cmdType: String get() = "execute-proposal"
    override val summary: String get() = "执行提案 $proposalId"
}

@Serializable
data class GrantRoleAuthPayload(
    @SerialName("subject_did") val subjectDid: String,
    val role: String
) : AuthPayload {
    override val cmdType: String get() = "grant-role"
    override val summary: String get() = "授予角色 $role"
}

@Serializable
data class IssueCredentialAuthPayload(
    @SerialName("subject_did") val subjectDid: String,
    val role: String = "member",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("club_code") val clubCode: String? = null,
    val permissions: List<String> = emptyList(),
    @SerialName("member_since") val memberSince: String? = null
) : AuthPayload {
    override val cmdType: String get() = "issue-credential"
    override val summary: String get() = "签发凭证"
}

@Serializable
data class RevokeCredentialAuthPayload(
    @SerialName("subject_did") val subjectDid: String,
    val reason: String
) : AuthPayload {
    override val cmdType: String get() = "revoke-credential"
    override val summary: String get() = "吊销凭证"
}

@Serializable
object PushPreferencesGetAuthPayload : AuthPayload {
    override val cmdType: String get() = "push-preferences-get"
    override val summary: String get() = "同步推送偏好"
}

@Serializable
data class PushPreferencesPutAuthPayload(
    @SerialName("enabled_event_types") val enabledEventTypes: List<String>,
    @SerialName("dnd_enabled") val dndEnabled: Boolean,
    @SerialName("dnd_start_hour") val dndStartHour: Int,
    @SerialName("dnd_end_hour") val dndEndHour: Int
) : AuthPayload {
    override val cmdType: String get() = "push-preferences-put"
    override val summary: String get() = "保存推送偏好"
}

@Serializable
data class TestAuthChallengePayload(
    val action: String,
    val description: String
) : AuthPayload {
    override val cmdType: String get() = "test-auth-challenge"
    override val summary: String get() = "测试挑战 $action"
}



@PublishedApi
internal object AuthPayloadSerializer : KSerializer<AuthPayload> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthPayload")

    override fun serialize(encoder: Encoder, value: AuthPayload) {
        val jsonObj: JsonObject = when (value) {
            is StartInstancePayload -> buildJsonObject { put("instance_id", value.instanceId) }
            is StopInstancePayload -> buildJsonObject { put("instance_id", value.instanceId) }
            is StopInstanceWithReasonPayload -> buildJsonObject {
                put("instance_id", value.instanceId)
                put("reason", value.reason)
            }
            is CreateInstancePayload -> buildJsonObject {
                put("name", value.name)
                put("kind", value.kind)
                put("owner", value.owner)
                put("club", value.club)
            }
            is MigrateInstancePayload -> buildJsonObject {
                put("instance_id", value.instanceId)
                if (value.targetHost != null) put("target_host", value.targetHost)
            }
            is UpdateInstanceConfigPayload -> buildJsonObject {
                put("instance_id", value.instanceId)
                value.config.forEach { (k, v) -> put(k, v) }
            }
            is BatchOperationPayload -> buildJsonObject {
                put("instance_ids", value.instanceIds)
                put("count", value.count)
                put("operation", value.operation)
            }
            is CreateTournamentPayload -> buildJsonObject {
                put("name", value.name)
                put("game_type", value.gameType)
                put("mode", value.mode)
                put("max_participants", value.maxParticipants)
                put("min_member_score", value.minMemberScore)
                put("registration_open", value.registrationOpen)
                put("registration_close", value.registrationClose)
                put("created_by", value.createdBy)
            }
            is UpdateTournamentStatusPayload -> buildJsonObject {
                put("tournament_id", value.tournamentId)
                put("status", value.status)
            }
            is ArchiveSeasonPayload -> buildJsonObject {
                put("season_id", value.seasonId)
            }
            is CreateSeasonPayload -> buildJsonObject {
                put("name", value.name)
                put("start_date", value.startDate)
                put("end_date", value.endDate)
            }
            is CreateDisputePayload -> buildJsonObject {
                put("tournament_id", value.tournamentId)
                put("match_id", value.matchId)
                put("reason", value.reason)
            }
            is PauseMatchPayload -> buildJsonObject {
                put("match_id", value.matchId)
            }
            is ResumeMatchPayload -> buildJsonObject {
                put("match_id", value.matchId)
            }
            is ResetMatchPayload -> buildJsonObject {
                put("match_id", value.matchId)
            }
            is JudgeMatchPayload -> buildJsonObject {
                put("match_id", value.matchId)
                put("winner_id", value.winnerId)
                put("reason", value.reason)
            }
            is AcknowledgeAlertPayload -> buildJsonObject {
                put("alert_id", value.alertId)
            }
            is ResolveAlertPayload -> buildJsonObject {
                put("alert_id", value.alertId)
            }
            is ApplySchedulingConstraintsPayload -> buildJsonObject {
                put("instance_id", value.instanceId)
                put("reason", value.reason)
            }
            is RevokeDevicePayload -> buildJsonObject {
                put("target_pubkey", value.targetPubkey)
                put("reason", value.reason)
                put("revoked_by", value.revokedBy)
            }
            is EmergencyRevokeDevicePayload -> buildJsonObject {
                put("target_pubkey", value.targetPubkey)
                put("reason", value.reason)
                put("revoked_by", value.revokedBy)
            }
            is CreateProposalAuthPayload -> buildJsonObject {
                put("proposal_type", value.proposalType)
                put("payload", ProposalPayloadSerializer.toJsonElement(value.payload))
                put("proposer", value.proposer)
            }
            is SubmitProposalDraftAuthPayload -> buildJsonObject {
                put("proposal_id", value.proposalId)
            }
            is RejectProposalAuthPayload -> buildJsonObject {
                put("proposal_id", value.proposalId)
            }
            is ExecuteProposalAuthPayload -> buildJsonObject {
                put("proposal_id", value.proposalId)
            }
            is GrantRoleAuthPayload -> buildJsonObject {
                put("subject_did", value.subjectDid)
                put("role", value.role)
            }
            is IssueCredentialAuthPayload -> buildJsonObject {
                put("subject_did", value.subjectDid)
                put("role", value.role)
                put("display_name", value.displayName)
                value.clubCode?.let { put("club_code", it) }
                put("permissions", kotlinx.serialization.json.JsonArray(
                    value.permissions.map { JsonPrimitive(it) }
                ))
                value.memberSince?.let { put("member_since", it) }
            }
            is RevokeCredentialAuthPayload -> buildJsonObject {
                put("subject_did", value.subjectDid)
                put("reason", value.reason)
            }
            is PushPreferencesGetAuthPayload -> buildJsonObject {  }
            is PushPreferencesPutAuthPayload -> buildJsonObject {
                put("enabled_event_types", kotlinx.serialization.json.JsonArray(
                    value.enabledEventTypes.map { JsonPrimitive(it) }
                ))
                put("dnd_enabled", value.dndEnabled)
                put("dnd_start_hour", value.dndStartHour)
                put("dnd_end_hour", value.dndEndHour)
            }
            is TestAuthChallengePayload -> buildJsonObject {
                put("action", value.action)
                put("description", value.description)
            }
        }
        encoder.encodeSerializableValue(JsonObject.serializer(), jsonObj)
    }

    override fun deserialize(decoder: Decoder): AuthPayload {


        throw UnsupportedOperationException("AuthPayload deserialization is not supported")
    }
}
