package com.jlucraft.console.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Typed payload for [Proposal] payloads, both sent and received from the server.
 *
 * When sent via the libp2p control protocol, [ProposalPayloadSerializer.toJsonElement] produces the
 * same flat JSON that the server expects. The companion [serializer] delegates to
 * the custom [ProposalPayloadSerializer].
 */
@Serializable(with = ProposalPayloadSerializer::class)
sealed interface ProposalPayload

// -- Known proposal payloads --

@Serializable
data class AddNodeProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class RemoveNodeProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class GrantAdminProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class RevokeAdminProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class UpdateConfigProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class PayoutRewardProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class EmergencyRevokeProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class UpdateGovernanceParamsProposalPayload(
    val description: String,
    val target: String
) : ProposalPayload

@Serializable
data class CreateTournamentProposalPayload(
    val name: String,
    @SerialName("game_type") val gameType: String,
    val mode: String,
    @SerialName("max_participants") val maxParticipants: Int,
    @SerialName("min_member_score") val minMemberScore: Int,
    @SerialName("registration_open") val registrationOpen: String,
    @SerialName("registration_close") val registrationClose: String,
    val scoring: ScoringRules? = null
) : ProposalPayload

@Serializable
data class DisputeResolveProposalPayload(
    @SerialName("dispute_id") val disputeId: String,
    val resolution: String,
    val status: String,
    @SerialName("tournament_id") val tournamentId: String? = null
) : ProposalPayload

/**
 * Unknown/custom proposal type — carries no dynamic data.
 */
@Serializable
data class UnknownProposalPayload(
    val type: String
) : ProposalPayload

// ── Convenience accessors ──

/** Extract a human-readable description for display in UI. */
val ProposalPayload.description: String
    get() = when (this) {
        is AddNodeProposalPayload -> description
        is RemoveNodeProposalPayload -> description
        is GrantAdminProposalPayload -> description
        is RevokeAdminProposalPayload -> description
        is UpdateConfigProposalPayload -> description
        is PayoutRewardProposalPayload -> description
        is EmergencyRevokeProposalPayload -> description
        is UpdateGovernanceParamsProposalPayload -> description
        is CreateTournamentProposalPayload -> name
        is DisputeResolveProposalPayload -> resolution
        is UnknownProposalPayload -> type
    }

/** Serialize to JSON string for use in signing (buildSignablePayload). */
fun ProposalPayload.toJsonString(): String =
    ProposalPayloadSerializer.toJsonString(this)

// ── Custom serializer: flat JSON for all proposal payloads ──

@PublishedApi
internal object ProposalPayloadSerializer : KSerializer<ProposalPayload> {

    private val knownLabels = setOf(
        "add-node", "remove-node", "grant-admin", "revoke-admin",
        "update-config", "payout-reward", "emergency-revoke",
        "update-governance-params", "create-tournament", "dispute-resolve"
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ProposalPayload")

    override fun serialize(encoder: Encoder, value: ProposalPayload) {
        encoder.encodeSerializableValue(JsonObject.serializer(), toJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): ProposalPayload {
        val jsonObj = decoder.decodeSerializableValue(JsonObject.serializer())
        return fromJsonElement(jsonObj)
    }

    /** Convert typed payload to [JsonElement] for embedding in auth payloads. */
    fun toJsonElement(value: ProposalPayload): JsonObject = when (value) {
        is AddNodeProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is RemoveNodeProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is GrantAdminProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is RevokeAdminProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is UpdateConfigProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is PayoutRewardProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is EmergencyRevokeProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is UpdateGovernanceParamsProposalPayload -> buildJsonObject {
            put("description", value.description)
            put("target", value.target)
        }
        is CreateTournamentProposalPayload -> buildJsonObject {
            put("name", value.name)
            put("game_type", value.gameType)
            put("mode", value.mode)
            put("max_participants", value.maxParticipants)
            put("min_member_score", value.minMemberScore)
            put("registration_open", value.registrationOpen)
            put("registration_close", value.registrationClose)
            if (value.scoring != null) {
                put("scoring", buildJsonObject {
                    put("win", value.scoring.win)
                    put("kill", value.scoring.kill)
                    put("survive_minute", value.scoring.survive_minute)
                    put("placement_1", value.scoring.placement_1)
                    put("placement_2", value.scoring.placement_2)
                    put("placement_3", value.scoring.placement_3)
                })
            }
        }
        is DisputeResolveProposalPayload -> buildJsonObject {
            put("dispute_id", value.disputeId)
            put("resolution", value.resolution)
            put("status", value.status)
            if (value.tournamentId != null) put("tournament_id", value.tournamentId)
        }
        is UnknownProposalPayload -> buildJsonObject { put("type", value.type) }
    }

    private fun fromJsonElement(jsonObj: JsonObject): ProposalPayload {
        // Try to match known fields; fall back to UnknownProposalPayload.
        val hasDescription = jsonObj.containsKey("description")
        val hasTarget = jsonObj.containsKey("target")
        val desc = jsonObj["description"]?.jsonPrimitive?.content ?: ""
        val target = jsonObj["target"]?.jsonPrimitive?.content ?: ""

        return when {
            jsonObj.containsKey("dispute_id") -> DisputeResolveProposalPayload(
                disputeId = jsonObj["dispute_id"]?.jsonPrimitive?.content ?: "",
                resolution = jsonObj["resolution"]?.jsonPrimitive?.content ?: "",
                status = jsonObj["status"]?.jsonPrimitive?.content ?: "",
                tournamentId = jsonObj["tournament_id"]?.jsonPrimitive?.content
            )
            jsonObj.containsKey("game_type") -> CreateTournamentProposalPayload(
                name = jsonObj["name"]?.jsonPrimitive?.content ?: "",
                gameType = jsonObj["game_type"]?.jsonPrimitive?.content ?: "",
                mode = jsonObj["mode"]?.jsonPrimitive?.content ?: "",
                maxParticipants = jsonObj["max_participants"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                minMemberScore = jsonObj["min_member_score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                registrationOpen = jsonObj["registration_open"]?.jsonPrimitive?.content ?: "",
                registrationClose = jsonObj["registration_close"]?.jsonPrimitive?.content ?: "",
                scoring = null
            )
            hasDescription || hasTarget -> AddNodeProposalPayload(description = desc, target = target)
            else -> UnknownProposalPayload(type = "unknown")
        }
    }

    /** Convert [ProposalPayload] to JSON string for signing (buildSignablePayload). */
    fun toJsonString(value: ProposalPayload): String =
        kotlinx.serialization.json.Json.encodeToString(JsonObject.serializer(), toJsonElement(value))
}

/** Create the appropriate [ProposalPayload] for simple description/target proposal types. */
fun proposalPayloadFor(type: String, description: String, target: String): ProposalPayload = when (type) {
    "add-node" -> AddNodeProposalPayload(description, target)
    "remove-node" -> RemoveNodeProposalPayload(description, target)
    "grant-admin" -> GrantAdminProposalPayload(description, target)
    "revoke-admin" -> RevokeAdminProposalPayload(description, target)
    "update-config" -> UpdateConfigProposalPayload(description, target)
    "payout-reward" -> PayoutRewardProposalPayload(description, target)
    "emergency-revoke" -> EmergencyRevokeProposalPayload(description, target)
    "update-governance-params" -> UpdateGovernanceParamsProposalPayload(description, target)
    else -> UnknownProposalPayload(type)
}
