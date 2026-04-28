package com.jlucraft.console.data.remote

import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.AuditAnomaly
import com.jlucraft.console.data.model.AuditChainVerification
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.model.CreateTournamentRequest
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.LeaderboardEntry
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.model.Team
import com.jlucraft.console.data.model.Tournament
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
private data class LogsResponse(val logs: List<String> = emptyList())

@Serializable
data class AuthRequest(
    val cmd_type: String,
    val payload: JsonObject,
    val public_key: String
)

@Serializable
data class AuthChallenge(
    val nonce: String,
    val ts: Long,
    val payload_hash: String,
    val ttl: Long
)

@Serializable
data class SignResponse(
    val nonce: String,
    val signature: String
)

@Serializable
data class AuthResult(
    val success: Boolean,
    val message: String
)

@Serializable
data class ClusterHealthResponse(
    val status: String,
    val peer_id: String,
    val connected_peers: Int,
    val running_instances: Int,
    val consensus_role: String,
    val last_oracle_report: String? = null,
    val last_announcement_at: String? = null
)

@Serializable
data class NetworkSnapshot(
    val runtime: NetworkRuntime? = null,
    val connected_peers: List<String> = emptyList()
)

@Serializable
data class NetworkRuntime(
    val identity: NetworkIdentity? = null
)

@Serializable
data class NetworkIdentity(
    val peer_id: String
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

class ApiService(initialBaseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var baseUrl: String = initialBaseUrl.removeSuffix("/")
    private var authNonce: String? = null
    private var authSignature: String? = null

    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = if (com.jlucraft.console.BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    fun setBaseUrl(url: String) {
        baseUrl = url.removeSuffix("/")
    }

    fun getBaseUrl(): String = baseUrl

    fun setAuthHeaders(nonce: String?, signature: String?) {
        authNonce = nonce
        authSignature = signature
    }

    fun clearAuthHeaders() {
        authNonce = null
        authSignature = null
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders() {
        authNonce?.let { header("X-Auth-Nonce", it) }
        authSignature?.let { header("X-Auth-Signature", it) }
    }

    suspend fun getClusterHealth(): Result<ClusterHealthResponse> = runCatching {
        client.get("/v1/cluster/health").body()
    }

    suspend fun getNetworkSnapshot(): Result<NetworkSnapshot> = runCatching {
        client.get("/v1/network").body()
    }

    suspend fun getInstances(): Result<List<Instance>> = runCatching {
        client.get("/v1/instances").body()
    }

    suspend fun startInstance(instanceId: String): Result<Unit> = runCatching {
        client.post("/v1/instances/$instanceId/start") {
            applyAuthHeaders()
        }
    }

    suspend fun stopInstance(instanceId: String): Result<Unit> = runCatching {
        client.post("/v1/instances/$instanceId/stop") {
            applyAuthHeaders()
        }
    }

    suspend fun deleteInstance(instanceId: String): Result<Unit> = runCatching {
        client.delete("/v1/instances/$instanceId") {
            applyAuthHeaders()
        }
    }

    suspend fun migrateInstance(instanceId: String, targetHost: String): Result<Unit> = runCatching {
        client.post("/v1/instances/$instanceId/migrate") {
            applyAuthHeaders()
            setBody(mapOf("target_host" to targetHost))
        }
    }

    suspend fun createInstance(request: CreateInstanceRequest): Result<Instance> = runCatching {
        client.post("/v1/instances") {
            applyAuthHeaders()
            setBody(request)
        }.body()
    }

    suspend fun updateAdmission(instanceId: String, policy: AdmissionPolicy): Result<Instance> = runCatching {
        client.put("/v1/instances/$instanceId/admission") {
            applyAuthHeaders()
            setBody(policy)
        }.body()
    }

    suspend fun checkAdmission(instanceId: String, playerPeerId: String?, playerClub: String?, hasVc: Boolean): Result<Boolean> = runCatching {
        client.post("/v1/instances/$instanceId/admission/check") {
            setBody(mapOf(
                "player_peer_id" to playerPeerId,
                "player_club" to playerClub,
                "has_vc" to hasVc
            ))
        }.body<Map<String, Boolean>>()["allowed"] ?: false
    }

    suspend fun requestChallenge(request: AuthRequest): Result<AuthChallenge> = runCatching {
        client.post("/v1/auth/challenge") {
            setBody(request)
        }.body()
    }

    suspend fun verifySignature(response: SignResponse): Result<AuthResult> = runCatching {
        client.post("/v1/auth/verify") {
            setBody(response)
        }.body()
    }

    suspend fun listProposals(): Result<List<Proposal>> = runCatching {
        client.get("/v1/proposals").body()
    }

    suspend fun getProposal(id: String): Result<Proposal> = runCatching {
        client.get("/v1/proposals/$id").body()
    }

    suspend fun createProposal(
        proposalType: String,
        payload: JsonObject,
        proposer: String
    ): Result<Proposal> = runCatching {
        client.post("/v1/proposals") {
            applyAuthHeaders()
            setBody(mapOf(
                "proposal_type" to proposalType,
                "payload" to payload,
                "proposer" to proposer
            ))
        }.body()
    }

    suspend fun signProposal(id: String, pubkey: String, signature: String): Result<Proposal> = runCatching {
        client.post("/v1/proposals/$id/sign") {
            applyAuthHeaders()
            setBody(mapOf(
                "pubkey" to pubkey,
                "signature" to signature
            ))
        }.body()
    }

    suspend fun executeProposal(id: String): Result<Proposal> = runCatching {
        client.post("/v1/proposals/$id/execute") {
            applyAuthHeaders()
        }.body()
    }

    suspend fun listAuditEntries(cmdType: String? = null, limit: Int = 100): Result<List<AuditEntry>> = runCatching {
        client.get("/v1/audit") {
            url {
                parameters.append("limit", limit.toString())
                if (cmdType != null) {
                    parameters.append("cmd_type", cmdType)
                }
            }
        }.body()
    }

    suspend fun verifyAuditChain(): Result<AuditChainVerification> = runCatching {
        client.post("/v1/audit/verify").body()
    }

    suspend fun getAuditAnomalies(): Result<List<AuditAnomaly>> = runCatching {
        client.get("/v1/audit/anomalies").body()
    }

    // --- Proposal draft lifecycle ---

    suspend fun createProposalDraft(
        proposalType: String,
        payload: JsonObject,
        proposer: String
    ): Result<Proposal> = runCatching {
        client.post("/v1/proposals/draft") {
            applyAuthHeaders()
            setBody(mapOf(
                "proposal_type" to proposalType,
                "payload" to payload,
                "proposer" to proposer
            ))
        }.body()
    }

    suspend fun submitProposalDraft(id: String): Result<Proposal> = runCatching {
        client.post("/v1/proposals/$id/submit") {
            applyAuthHeaders()
        }.body()
    }

    suspend fun rejectProposal(id: String): Result<Proposal> = runCatching {
        client.post("/v1/proposals/$id/reject") {
            applyAuthHeaders()
        }.body()
    }

    // --- End proposal draft lifecycle ---

    suspend fun listNodeScores(): Result<List<NodeScore>> = runCatching {
        client.get("/v1/nodes/scores").body()
    }

    suspend fun getNodeScore(peerId: String): Result<NodeScore> = runCatching {
        client.get("/v1/nodes/$peerId/score").body()
    }

    suspend fun listTournaments(): Result<List<Tournament>> = runCatching {
        client.get("/v1/tournaments").body()
    }

    suspend fun getTournament(id: String): Result<Tournament> = runCatching {
        client.get("/v1/tournaments/$id").body()
    }

    suspend fun createTournament(request: CreateTournamentRequest): Result<Tournament> = runCatching {
        client.post("/v1/tournaments") {
            applyAuthHeaders()
            setBody(request)
        }.body()
    }

    suspend fun updateTournamentStatus(id: String, status: String): Result<Tournament> = runCatching {
        client.put("/v1/tournaments/$id/status") {
            applyAuthHeaders()
            setBody(mapOf("status" to status))
        }.body()
    }

    suspend fun listMatches(tournamentId: String): Result<List<Match>> = runCatching {
        client.get("/v1/tournaments/$tournamentId/matches").body()
    }

    suspend fun listTeams(): Result<List<Team>> = runCatching {
        client.get("/v1/teams").body()
    }

    suspend fun getInstanceLogs(instanceId: String, tail: Int? = null, keyword: String? = null): Result<List<String>> = runCatching {
        client.get("/v1/instances/$instanceId/logs") {
            url {
                if (tail != null) {
                    parameters.append("tail", tail.toString())
                }
                if (keyword != null) {
                    parameters.append("keyword", keyword)
                }
            }
        }.body<LogsResponse>().logs
    }

    suspend fun listAlerts(severity: String? = null, includeResolved: Boolean = false): Result<List<Alert>> = runCatching {
        client.get("/v1/alerts") {
            url {
                if (severity != null) {
                    parameters.append("severity", severity)
                }
                parameters.append("include_resolved", includeResolved.toString())
            }
        }.body()
    }

    suspend fun listSeasons(): Result<List<Season>> = runCatching {
        client.get("/v1/seasons").body()
    }

    suspend fun getSeason(id: String): Result<Season> = runCatching {
        client.get("/v1/seasons/$id").body()
    }

    suspend fun getCurrentSeason(): Result<Season> = runCatching {
        client.get("/v1/seasons/current").body()
    }

    suspend fun getSeasonLeaderboard(seasonId: String): Result<Leaderboard> = runCatching {
        client.get("/v1/seasons/$seasonId/leaderboard").body()
    }

    suspend fun archiveSeason(id: String): Result<Season> = runCatching {
        client.post("/v1/seasons/$id/archive") {
            applyAuthHeaders()
        }.body()
    }

    suspend fun createSeason(name: String, startDate: String, endDate: String): Result<Season> = runCatching {
        client.post("/v1/seasons") {
            applyAuthHeaders()
            setBody(mapOf("name" to name, "start_date" to startDate, "end_date" to endDate))
        }.body()
    }

    // --- Push ---

    suspend fun registerPushEndpoint(endpoint: String): Result<Unit> = runCatching {
        client.post("/v1/push/register") {
            applyAuthHeaders()
            setBody(mapOf("endpoint" to endpoint))
        }
    }

    // --- Device management ---

    suspend fun listDevices(): Result<List<Device>> = runCatching {
        client.get("/v1/devices").body()
    }

    suspend fun revokeDevice(pubkey: String, reason: String, revokedBy: String): Result<Device> = runCatching {
        client.post("/v1/devices/$pubkey/revoke") {
            applyAuthHeaders()
            setBody(mapOf("reason" to reason, "revoked_by" to revokedBy))
        }.body()
    }

    suspend fun emergencyRevokeDevice(pubkey: String, reason: String, revokedBy: String): Result<Device> = runCatching {
        client.post("/v1/devices/$pubkey/emergency-revoke") {
            applyAuthHeaders()
            setBody(mapOf("reason" to reason, "revoked_by" to revokedBy))
        }.body()
    }
}
