package com.jlucraft.console.data.repository

import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.CreateTournamentRequest
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.model.Team
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.remote.ApiService
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.CreateInstanceRequest
import com.jlucraft.console.data.remote.NetworkSnapshot

class NodeRepository(
    private val api: ApiService
) {
    suspend fun getClusterHealth(): Result<ClusterHealthResponse> = api.getClusterHealth()

    suspend fun getNetworkSnapshot(): Result<NetworkSnapshot> = api.getNetworkSnapshot()

    suspend fun getInstances(): Result<List<Instance>> = api.getInstances()

    suspend fun startInstance(instanceId: String): Result<Unit> = api.startInstance(instanceId)

    suspend fun stopInstance(instanceId: String): Result<Unit> = api.stopInstance(instanceId)

    suspend fun deleteInstance(instanceId: String): Result<Unit> = api.deleteInstance(instanceId)

    suspend fun migrateInstance(instanceId: String, targetHost: String): Result<Unit> =
        api.migrateInstance(instanceId, targetHost)

    suspend fun createInstance(request: CreateInstanceRequest): Result<Instance> = api.createInstance(request)

    suspend fun listNodeScores(): Result<List<NodeScore>> = api.listNodeScores()

    suspend fun listTournaments(): Result<List<Tournament>> = api.listTournaments()

    suspend fun getTournament(id: String): Result<Tournament> = api.getTournament(id)

    suspend fun createTournament(request: CreateTournamentRequest): Result<Tournament> =
        api.createTournament(request)

    suspend fun updateTournamentStatus(id: String, status: String): Result<Tournament> =
        api.updateTournamentStatus(id, status)

    suspend fun listMatches(tournamentId: String): Result<List<Match>> =
        api.listMatches(tournamentId)

    suspend fun listTeams(): Result<List<Team>> =
        api.listTeams()

    suspend fun listAlerts(severity: String? = null, includeResolved: Boolean = false): Result<List<Alert>> =
        api.listAlerts(severity, includeResolved)

    suspend fun getInstanceLogs(instanceId: String, tail: Int? = null, keyword: String? = null): Result<List<String>> =
        api.getInstanceLogs(instanceId, tail, keyword)

    suspend fun listSeasons(): Result<List<Season>> = api.listSeasons()

    suspend fun getSeason(id: String): Result<Season> = api.getSeason(id)

    suspend fun getCurrentSeason(): Result<Season> = api.getCurrentSeason()

    suspend fun getSeasonLeaderboard(seasonId: String): Result<Leaderboard> =
        api.getSeasonLeaderboard(seasonId)

    suspend fun archiveSeason(id: String): Result<Season> = api.archiveSeason(id)

    suspend fun createSeason(name: String, startDate: String, endDate: String): Result<Season> =
        api.createSeason(name, startDate, endDate)

    suspend fun listDevices(): Result<List<Device>> = api.listDevices()

    suspend fun revokeDevice(pubkey: String, reason: String, revokedBy: String): Result<Device> =
        api.revokeDevice(pubkey, reason, revokedBy)

    suspend fun emergencyRevokeDevice(pubkey: String, reason: String, revokedBy: String): Result<Device> =
        api.emergencyRevokeDevice(pubkey, reason, revokedBy)
}
