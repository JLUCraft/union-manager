package com.jlucraft.console.data.repository

import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.model.SchedulingConstraints
import com.jlucraft.console.data.model.SchedulingSimulation
import com.jlucraft.console.data.model.ApplySchedulingConstraintsRequest
import com.jlucraft.console.data.model.SchedulingConstraintsResponse
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.NetworkSnapshot
import com.jlucraft.console.data.remote.PushPreferencesResponse
import com.jlucraft.console.data.remote.libp2p.Libp2pClient

/**
 * Repository for cluster-level operations: health, network topology,
 * node scores, scheduling, push preferences, and event subscriptions.
 *
 */
class NodeRepository(
    private val client: Libp2pClient
) {
    // ── Cluster / network ──

    suspend fun getClusterHealth(): Result<ClusterHealthResponse> = client.getClusterHealth()

    suspend fun getNetworkSnapshot(): Result<NetworkSnapshot> = client.getNetworkSnapshot()

    // ── Node scores ──

    suspend fun listNodeScores(): Result<List<NodeScore>> = client.listNodeScores()

    // ── Scheduling ──

    suspend fun getSchedulingConstraints(instanceId: String): Result<SchedulingConstraints> =
        client.getSchedulingConstraints(instanceId)

    suspend fun applySchedulingConstraints(request: ApplySchedulingConstraintsRequest): Result<SchedulingConstraintsResponse> =
        client.applySchedulingConstraints(request)

    suspend fun simulateScheduling(instanceId: String, constraints: SchedulingConstraints): Result<SchedulingSimulation> =
        client.simulateScheduling(instanceId, constraints)

    // ── Push preferences ──

    suspend fun getPushPreferences(): Result<PushPreferencesResponse> = client.getPushPreferences()

    suspend fun updatePushPreferences(
        enabledEventTypes: Set<String>,
        dndEnabled: Boolean,
        dndStartHour: Int,
        dndEndHour: Int
    ): Result<PushPreferencesResponse> =
        client.updatePushPreferences(enabledEventTypes, dndEnabled, dndStartHour, dndEndHour)

}
