package com.jlucraft.console.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushConfigResponse(
    val configured: Boolean,
    @SerialName("vapid_public_key") val vapidPublicKey: String? = null
)

@Serializable
data class PushPreferencesResponse(
    @SerialName("enabled_event_types") val enabledEventTypes: List<String> = emptyList(),
    @SerialName("dnd_enabled") val dndEnabled: Boolean = false,
    @SerialName("dnd_start_hour") val dndStartHour: Int = 0,
    @SerialName("dnd_end_hour") val dndEndHour: Int = 0
)
