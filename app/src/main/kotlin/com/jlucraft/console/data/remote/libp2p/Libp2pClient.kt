package com.jlucraft.console.data.remote.libp2p

import com.jlucraft.console.data.model.*
import com.jlucraft.console.data.model.GrantRoleResponse
import com.jlucraft.console.data.model.IssueCredentialResponse
import com.jlucraft.console.data.model.RevokeCredentialResponse
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.AuthResult
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.CommandResult
import com.jlucraft.console.data.remote.NetworkSnapshot
import com.jlucraft.console.data.remote.PushConfigResponse
import com.jlucraft.console.data.remote.PushPreferencesResponse
import com.jlucraft.console.data.remote.SignResponse
import kotlinx.coroutines.flow.Flow
import java.util.UUID


 *
 *
 *
class Libp2pClient(
    private val transport: Libp2pTransport,
    private val controlProtocolId: String,
) {


    @Volatile
    private var authContext: AuthContext? = null


    val events: EventStreamManager = EventStreamManager(transport, controlProtocolId)



    fun setAuthContext(ctx: AuthContext) { authContext = ctx }
    fun getAuthContext(): AuthContext? = authContext
    fun clearAuthContext() { authContext = null }


    fun localPeerId(): String? = transport.localPeerId()


    suspend fun start(config: Libp2pConfig): Result<Unit> = transport.start(config)


    suspend fun stop() {
        events.shutdown()
        transport.stop()
    }



    private suspend fun send(request: ControlRequest): Result<ControlResponse> {
        val requestBytes = encodeControlRequest(request)
        return transport.unaryCall(controlProtocolId, requestBytes)
            .map { decodeControlResponse(it) }
    }




    suspend fun getClusterHealth(): Result<ClusterHealthResponse> =
        send(ControlRequest.GetClusterHealth(newRequestId()))
            .mapResponse { (it as? ControlResponse.ClusterHealth)?.data ?: unexpected(it) }

    suspend fun getNetworkSnapshot(): Result<NetworkSnapshot> =
        send(ControlRequest.GetNetwork(newRequestId()))
            .mapResponse { (it as? ControlResponse.NetworkSnapshot)?.data ?: unexpected(it) }


    suspend fun getInstances(): Result<List<Instance>> =
        send(ControlRequest.ListInstances(newRequestId()))
            .mapResponse { (it as? ControlResponse.InstanceList)?.instances ?: unexpected(it) }

    suspend fun startInstance(instanceId: String): Result<Unit> =
        send(ControlRequest.StartInstance(instanceId, newRequestId()))
            .mapUnit()

    suspend fun stopInstance(instanceId: String): Result<Unit> =
        send(ControlRequest.StopInstance(instanceId, null, newRequestId()))
            .mapUnit()

    suspend fun stopInstanceWithReason(instanceId: String, reason: String): Result<Unit> =
        send(ControlRequest.StopInstance(instanceId, reason, newRequestId()))
            .mapUnit()

    suspend fun deleteInstance(instanceId: String): Result<Unit> =
        send(ControlRequest.DeleteInstance(instanceId, newRequestId()))
            .mapUnit()

    suspend fun migrateInstance(instanceId: String, targetHost: String?): Result<Unit> =
        send(ControlRequest.MigrateInstance(instanceId, targetHost, newRequestId()))
            .mapUnit()

    suspend fun createInstance(request: CreateInstanceRequest): Result<Instance> =
        send(ControlRequest.CreateInstance(request, newRequestId()))
            .mapResponse { (it as? ControlResponse.InstanceDetail)?.instance ?: unexpected(it) }

    suspend fun updateAdmission(instanceId: String, policy: AdmissionPolicy): Result<Instance> =
        send(ControlRequest.UpdateAdmission(instanceId, policy, newRequestId()))
            .mapResponse { (it as? ControlResponse.InstanceDetail)?.instance ?: unexpected(it) }

    suspend fun checkAdmission(instanceId: String, playerPeerId: String?, playerClub: String?, hasVc: Boolean): Result<Boolean> =
        send(ControlRequest.CheckAdmission(instanceId, playerPeerId, playerClub, hasVc, newRequestId()))
            .mapResponse { (it as? ControlResponse.AdmissionCheck)?.allowed ?: unexpected(it) }

    suspend fun getInstanceLogs(instanceId: String, tail: Int? = null, keyword: String? = null): Result<List<String>> =
        send(ControlRequest.GetInstanceLogs(instanceId, tail, keyword, newRequestId()))
            .mapResponse { (it as? ControlResponse.InstanceLogs)?.logs ?: unexpected(it) }

    suspend fun updateInstanceConfig(instanceId: String, config: Map<String, String>): Result<Instance> =
        send(ControlRequest.UpdateInstanceConfig(instanceId, config, newRequestId()))
            .mapResponse { (it as? ControlResponse.InstanceDetail)?.instance ?: unexpected(it) }


    suspend fun getSchedulingConstraints(instanceId: String): Result<SchedulingConstraints> =
        send(ControlRequest.GetSchedulingConstraints(instanceId, newRequestId()))
            .mapResponse { (it as? ControlResponse.SchedulingConstraints)?.data ?: unexpected(it) }

    suspend fun applySchedulingConstraints(request: ApplySchedulingConstraintsRequest): Result<SchedulingConstraintsResponse> =
        send(ControlRequest.ApplySchedulingConstraints(request, newRequestId()))
            .mapResponse { (it as? ControlResponse.SchedulingResponse)?.data ?: unexpected(it) }

    suspend fun simulateScheduling(instanceId: String, constraints: SchedulingConstraints): Result<SchedulingSimulation> =
        send(ControlRequest.SimulateScheduling(instanceId, constraints, newRequestId()))
            .mapResponse { (it as? ControlResponse.SchedulingSimulation)?.data ?: unexpected(it) }


    suspend fun requestChallenge(request: AuthRequest): Result<AuthChallenge> =
        send(ControlRequest.RequestChallenge(request, newRequestId()))
            .mapResponse { (it as? ControlResponse.AuthChallengeResponse)?.challenge ?: unexpected(it) }

    suspend fun verifySignature(response: SignResponse): Result<AuthResult> =
        send(ControlRequest.VerifySignature(response, newRequestId()))
            .mapResponse { (it as? ControlResponse.AuthVerificationResult)?.result ?: unexpected(it) }


    suspend fun createCommand(request: AuthRequest): Result<AuthChallenge> =
        send(ControlRequest.CreateCommand(request, newRequestId()))
            .mapResponse { (it as? ControlResponse.AuthChallengeResponse)?.challenge ?: unexpected(it) }

    suspend fun respondCommand(challengeId: String, response: SignResponse): Result<CommandResult> =
        send(ControlRequest.RespondCommand(challengeId, response, newRequestId()))
            .mapResponse { (it as? ControlResponse.CommandResultResponse)?.result ?: unexpected(it) }


    suspend fun listProposals(): Result<List<Proposal>> =
        send(ControlRequest.ListProposals(newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalList)?.proposals ?: unexpected(it) }

    suspend fun getProposal(id: String): Result<Proposal> =
        send(ControlRequest.GetProposal(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }

    suspend fun createProposal(proposalType: String, payload: ProposalPayload, proposer: String): Result<Proposal> =
        send(ControlRequest.CreateProposal(proposalType, payload, proposer, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }

    suspend fun signProposal(id: String, pubkey: String, signature: String): Result<Proposal> =
        send(ControlRequest.SignProposal(id, pubkey, signature, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }

    suspend fun executeProposal(id: String): Result<Proposal> =
        send(ControlRequest.ExecuteProposal(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }

    suspend fun rejectProposal(id: String): Result<Proposal> =
        send(ControlRequest.RejectProposal(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }

    suspend fun createProposalDraft(proposalType: String, payload: ProposalPayload, proposer: String): Result<Proposal> =
        send(ControlRequest.CreateProposalDraft(proposalType, payload, proposer, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }

    suspend fun submitProposalDraft(id: String): Result<Proposal> =
        send(ControlRequest.SubmitProposalDraft(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.ProposalDetail)?.proposal ?: unexpected(it) }


    suspend fun listAuditEntries(cmdType: String? = null, limit: Int = 100): Result<List<AuditEntry>> =
        send(ControlRequest.ListAuditEntries(cmdType, limit, newRequestId()))
            .mapResponse { (it as? ControlResponse.AuditEntryList)?.entries ?: unexpected(it) }

    suspend fun verifyAuditChain(): Result<AuditChainVerification> =
        send(ControlRequest.VerifyAuditChain(newRequestId()))
            .mapResponse { (it as? ControlResponse.AuditChainVerification)?.data ?: unexpected(it) }

    suspend fun getAuditAnomalies(): Result<List<AuditAnomaly>> =
        send(ControlRequest.ListAuditAnomalies(newRequestId()))
            .mapResponse { (it as? ControlResponse.AuditAnomalyList)?.anomalies ?: unexpected(it) }


    suspend fun listAlerts(severity: String? = null, includeResolved: Boolean = false): Result<List<Alert>> =
        send(ControlRequest.ListAlerts(severity, includeResolved, newRequestId()))
            .mapResponse { (it as? ControlResponse.AlertList)?.alerts ?: unexpected(it) }

    suspend fun acknowledgeAlert(alertId: String): Result<Alert> =
        send(ControlRequest.AcknowledgeAlert(alertId, newRequestId()))
            .mapResponse { (it as? ControlResponse.AlertUpdated)?.alert ?: unexpected(it) }

    suspend fun resolveAlert(alertId: String): Result<Alert> =
        send(ControlRequest.ResolveAlert(alertId, newRequestId()))
            .mapResponse { (it as? ControlResponse.AlertUpdated)?.alert ?: unexpected(it) }


    suspend fun listTournaments(): Result<List<Tournament>> =
        send(ControlRequest.ListTournaments(newRequestId()))
            .mapResponse { (it as? ControlResponse.TournamentList)?.tournaments ?: unexpected(it) }

    suspend fun getTournament(id: String): Result<Tournament> =
        send(ControlRequest.GetTournament(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.TournamentDetail)?.tournament ?: unexpected(it) }

    suspend fun createTournament(request: CreateTournamentRequest): Result<Tournament> =
        send(ControlRequest.CreateTournament(request, newRequestId()))
            .mapResponse { (it as? ControlResponse.TournamentDetail)?.tournament ?: unexpected(it) }

    suspend fun updateTournamentStatus(id: String, status: TournamentStatus): Result<Tournament> =
        send(ControlRequest.UpdateTournamentStatus(id, status, newRequestId()))
            .mapResponse { (it as? ControlResponse.TournamentDetail)?.tournament ?: unexpected(it) }

    suspend fun listMatches(tournamentId: String): Result<List<Match>> =
        send(ControlRequest.ListTournamentMatches(tournamentId, newRequestId()))
            .mapResponse { (it as? ControlResponse.MatchList)?.matches ?: unexpected(it) }

    suspend fun listTeams(): Result<List<Team>> =
        send(ControlRequest.ListTeams(newRequestId()))
            .mapResponse { (it as? ControlResponse.TeamList)?.teams ?: unexpected(it) }


    suspend fun listDisputes(tournamentId: String? = null): Result<List<DisputeMatch>> =
        send(ControlRequest.ListDisputes(tournamentId, newRequestId()))
            .mapResponse { (it as? ControlResponse.DisputeList)?.disputes ?: unexpected(it) }

    suspend fun getDispute(disputeId: String): Result<DisputeMatch> =
        send(ControlRequest.GetDispute(disputeId, newRequestId()))
            .mapResponse { (it as? ControlResponse.DisputeDetail)?.dispute ?: unexpected(it) }

    suspend fun createDispute(tournamentId: String, request: CreateDisputeRequest): Result<DisputeMatch> =
        send(ControlRequest.CreateDispute(tournamentId, request, newRequestId()))
            .mapResponse { (it as? ControlResponse.DisputeDetail)?.dispute ?: unexpected(it) }


    suspend fun pauseMatch(matchId: String): Result<Match> =
        send(ControlRequest.PauseMatch(matchId, newRequestId()))
            .mapResponse { (it as? ControlResponse.MatchUpdated)?.match ?: unexpected(it) }

    suspend fun resumeMatch(matchId: String): Result<Match> =
        send(ControlRequest.ResumeMatch(matchId, newRequestId()))
            .mapResponse { (it as? ControlResponse.MatchUpdated)?.match ?: unexpected(it) }

    suspend fun resetMatch(matchId: String): Result<Match> =
        send(ControlRequest.ResetMatch(matchId, newRequestId()))
            .mapResponse { (it as? ControlResponse.MatchUpdated)?.match ?: unexpected(it) }

    suspend fun judgeMatch(matchId: String, winnerId: String, reason: String): Result<Match> =
        send(ControlRequest.JudgeMatch(matchId, winnerId, reason, newRequestId()))
            .mapResponse { (it as? ControlResponse.MatchUpdated)?.match ?: unexpected(it) }


    suspend fun listSeasons(): Result<List<Season>> =
        send(ControlRequest.ListSeasons(newRequestId()))
            .mapResponse { (it as? ControlResponse.SeasonList)?.seasons ?: unexpected(it) }

    suspend fun getSeason(id: String): Result<Season> =
        send(ControlRequest.GetSeason(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.SeasonDetail)?.season ?: unexpected(it) }

    suspend fun getCurrentSeason(): Result<Season> =
        send(ControlRequest.GetCurrentSeason(newRequestId()))
            .mapResponse { (it as? ControlResponse.SeasonDetail)?.season ?: unexpected(it) }

    suspend fun getSeasonLeaderboard(seasonId: String): Result<Leaderboard> =
        send(ControlRequest.GetSeasonLeaderboard(seasonId, newRequestId()))
            .mapResponse { (it as? ControlResponse.LeaderboardData)?.leaderboard ?: unexpected(it) }

    suspend fun archiveSeason(id: String): Result<Season> =
        send(ControlRequest.ArchiveSeason(id, newRequestId()))
            .mapResponse { (it as? ControlResponse.SeasonDetail)?.season ?: unexpected(it) }

    suspend fun createSeason(name: String, startDate: String, endDate: String): Result<Season> =
        send(ControlRequest.CreateSeason(name, startDate, endDate, newRequestId()))
            .mapResponse { (it as? ControlResponse.SeasonDetail)?.season ?: unexpected(it) }


    suspend fun listDevices(): Result<List<Device>> =
        send(ControlRequest.ListDevices(newRequestId()))
            .mapResponse { (it as? ControlResponse.DeviceList)?.devices ?: unexpected(it) }

    suspend fun revokeDevice(pubkey: String, reason: String, revokedBy: String): Result<Device> =
        send(ControlRequest.RevokeDevice(pubkey, reason, revokedBy, newRequestId()))
            .mapResponse { (it as? ControlResponse.DeviceUpdated)?.device ?: unexpected(it) }

    suspend fun emergencyRevokeDevice(pubkey: String, reason: String, revokedBy: String): Result<Device> =
        send(ControlRequest.EmergencyRevokeDevice(pubkey, reason, revokedBy, newRequestId()))
            .mapResponse { (it as? ControlResponse.DeviceUpdated)?.device ?: unexpected(it) }


    suspend fun listMembers(): Result<List<MemberSummary>> =
        send(ControlRequest.ListMembers(newRequestId()))
            .mapResponse { (it as? ControlResponse.MemberList)?.members ?: unexpected(it) }

    suspend fun issueCredential(request: CredentialActionRequest): Result<IssueCredentialResponse> =
        send(ControlRequest.IssueCredential(request, newRequestId()))
            .mapResponse {
                when (it) {
                    is ControlResponse.CredentialIssued -> it.response


                    is ControlResponse.UnitSuccess -> IssueCredentialResponse(
                        subjectDid = request.subjectDid,
                        credentialType = "",
                        proposalId = "",
                        status = it.message.removePrefix("member_mutation:").ifEmpty { "ok" },
                    )
                    else -> unexpected(it)
                }
            }

    suspend fun revokeCredential(request: CredentialActionRequest): Result<RevokeCredentialResponse> =
        send(ControlRequest.RevokeCredential(request, newRequestId()))
            .mapResponse {
                when (it) {
                    is ControlResponse.CredentialRevoked -> it.response
                    is ControlResponse.UnitSuccess -> RevokeCredentialResponse(
                        revoked = true,
                        subjectDid = request.subjectDid,
                    )
                    else -> unexpected(it)
                }
            }

    suspend fun grantRole(request: GrantRoleRequest): Result<GrantRoleResponse> =
        send(ControlRequest.GrantRole(request, newRequestId()))
            .mapResponse {
                when (it) {
                    is ControlResponse.RoleGranted -> it.response
                    is ControlResponse.UnitSuccess -> GrantRoleResponse(
                        subjectDid = request.subjectDid,
                        grantedRole = request.role,
                        status = it.message.removePrefix("member_mutation:").ifEmpty { "ok" },
                    )
                    else -> unexpected(it)
                }
            }

    suspend fun verifyVc(request: VcVerifyRequest): Result<VcVerificationResult> =
        send(ControlRequest.VerifyVc(request, newRequestId()))
            .mapResponse { (it as? ControlResponse.VcVerification)?.result ?: unexpected(it) }

    suspend fun resolveDid(did: String): Result<DidServerResponse> =
        send(ControlRequest.ResolveDid(did, newRequestId()))
            .mapResponse { (it as? ControlResponse.DidResolution)?.response ?: unexpected(it) }


    suspend fun listNodeScores(): Result<List<NodeScore>> =
        send(ControlRequest.ListNodeScores(newRequestId()))
            .mapResponse { (it as? ControlResponse.NodeScoreList)?.scores ?: unexpected(it) }

    suspend fun getNodeScore(peerId: String): Result<NodeScore> =
        send(ControlRequest.GetNodeScore(peerId, newRequestId()))
            .mapResponse { (it as? ControlResponse.NodeScoreDetail)?.score ?: unexpected(it) }


    suspend fun getOracleScore(playerId: String): Result<OracleScore> =
        send(ControlRequest.GetOracleScore(playerId, newRequestId()))
            .mapResponse { (it as? ControlResponse.OracleScoreResult)?.score ?: unexpected(it) }


    suspend fun registerPushEndpoint(endpoint: String, devicePubkey: String): Result<Unit> =
        send(ControlRequest.RegisterPushEndpoint(endpoint, devicePubkey, newRequestId()))
            .mapUnit()

    suspend fun getPushConfig(): Result<PushConfigResponse> =
        send(ControlRequest.GetPushConfig(newRequestId()))
            .mapResponse { (it as? ControlResponse.PushConfig)?.config ?: unexpected(it) }

    suspend fun initPushConfig(): Result<PushConfigResponse> =
        send(ControlRequest.InitPushConfig(newRequestId()))
            .mapResponse { (it as? ControlResponse.PushConfig)?.config ?: unexpected(it) }

    suspend fun getPushPreferences(): Result<PushPreferencesResponse> =
        send(ControlRequest.GetPushPreferences(newRequestId()))
            .mapResponse { (it as? ControlResponse.PushPreferences)?.preferences ?: unexpected(it) }

    suspend fun updatePushPreferences(
        enabledEventTypes: Set<String>,
        dndEnabled: Boolean,
        dndStartHour: Int,
        dndEndHour: Int
    ): Result<PushPreferencesResponse> =
        send(ControlRequest.UpdatePushPreferences(enabledEventTypes, dndEnabled, dndStartHour, dndEndHour, newRequestId()))
            .mapResponse { (it as? ControlResponse.PushPreferences)?.preferences ?: unexpected(it) }




    fun subscribeGovernanceEvents(): Flow<EventEnvelope> =
        events.subscribe(topics = listOf("governance"), groupKey = "governance")


    fun subscribeClusterEvents(): Flow<EventEnvelope> =
        events.subscribe(topics = listOf("cluster"), groupKey = "cluster")


    fun subscribeTournamentEvents(tournamentId: String): Flow<EventEnvelope> =
        events.subscribe(topics = listOf("tournament.$tournamentId"), groupKey = "tournament.$tournamentId")


    fun subscribeInstanceEvents(instanceId: String): Flow<EventEnvelope> =
        events.subscribe(topics = listOf("instance.$instanceId"), groupKey = "instance.$instanceId")



    private fun newRequestId(): String = UUID.randomUUID().toString()

    private inline fun <reified T> Result<ControlResponse>.mapResponse(
        extract: (ControlResponse) -> T?
    ): Result<T> = map { response ->
        when (response) {
            is ControlResponse.Error -> throw Libp2pProtocolException(response.code, response.message)
            else -> extract(response) ?: throw Libp2pProtocolException(
                "UNEXPECTED_RESPONSE",
                "Unexpected response type: ${response::class.simpleName}"
            )
        }
    }

    private fun Result<ControlResponse>.mapUnit(): Result<Unit> =
        map { response ->
            when (response) {
                is ControlResponse.Error -> throw Libp2pProtocolException(response.code, response.message)
                is ControlResponse.UnitSuccess -> Unit
                else -> Unit
            }
        }



    private fun encodeControlRequest(request: ControlRequest): ByteArray =
        ControlRequestEncoder.encode(request, authContext)



    private fun decodeControlResponse(bytes: ByteArray): ControlResponse =
        ControlResponseDecoder.decode(bytes)


    private fun unexpected(response: ControlResponse): Nothing =
        throw Libp2pProtocolException(
            "UNEXPECTED_RESPONSE",
            "Unexpected response type: ${response::class.simpleName}"
        )
}

class Libp2pProtocolException(
    val code: String,
    override val message: String
) : Exception("[$code] $message")
