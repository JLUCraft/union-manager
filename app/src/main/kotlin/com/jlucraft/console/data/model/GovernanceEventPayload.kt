package com.jlucraft.console.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Typed payload for [GovernanceEvent] payloads delivered via libp2p event stream.
 *
 * [GovernanceEventPayloadSerializer] handles transparent conversion during deserialization.
 */
@Serializable(with = GovernanceEventPayloadSerializer::class)
sealed interface GovernanceEventPayload

@Serializable
data class GovernanceEventData(
    val raw: String
) : GovernanceEventPayload

@PublishedApi
internal object GovernanceEventPayloadSerializer : KSerializer<GovernanceEventPayload> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GovernanceEventPayload")

    override fun serialize(encoder: Encoder, value: GovernanceEventPayload) {
        when (value) {
            is GovernanceEventData -> encoder.encodeSerializableValue(
                JsonObject.serializer(),
                buildJsonObject { /* minimal */ }
            )
        }
    }

    override fun deserialize(decoder: Decoder): GovernanceEventPayload {
        val jsonObj = decoder.decodeSerializableValue(JsonObject.serializer())
        return GovernanceEventData(raw = jsonObj.toString())
    }
}
