package com.jlucraft.console.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClusterHealthResponse(
    val status: String,
    @SerialName("peer_id") val peer_id: String,
    @SerialName("connected_peers") val connected_peers: Int,
    @SerialName("running_instances") val running_instances: Int,
    @SerialName("consensus_role") val consensus_role: String,
    @SerialName("last_announcement_at") val last_announcement_at: String? = null
)

@Serializable
data class NetworkSnapshot(
    @SerialName("connected_peers") val connected_peers: List<String> = emptyList()
)
