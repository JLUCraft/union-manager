package com.jlucraft.console.data.remote.libp2p

import com.jlucraft.common.v1.AuthContext as ProtoAuth
import com.jlucraft.common.v1.PeerIdentity as ProtoPeerIdentity
import com.jlucraft.common.v1.SignedRequest as ProtoSignedRequest
import com.jlucraft.control.v1.AcknowledgeAlertRequest
import com.jlucraft.control.v1.AdmissionPolicy as ProtoAdmissionPolicy
import com.jlucraft.control.v1.ApproveJoinRequest
import com.jlucraft.control.v1.ArchiveSeasonRequest
import com.jlucraft.control.v1.CheckAdmissionRequest
import com.jlucraft.control.v1.ControlRequest as ProtoControlRequest
import com.jlucraft.control.v1.CreateAuthChallengeRequest
import com.jlucraft.control.v1.CreateInstanceRequest as ProtoCreateInstanceRequest
import com.jlucraft.control.v1.CreateMatchDisputeRequest
import com.jlucraft.control.v1.CreateSeasonRequest
import com.jlucraft.control.v1.CreateTournamentRequest as ProtoCreateTournamentRequest
import com.jlucraft.control.v1.DeleteInstanceRequest
import com.jlucraft.control.v1.EmergencyRevokeDeviceRequest
import com.jlucraft.control.v1.ExecuteProposalRequest
import com.jlucraft.control.v1.GetClusterHealthRequest
import com.jlucraft.control.v1.GetCurrentSeasonRequest
import com.jlucraft.control.v1.GetDisputeRequest
import com.jlucraft.control.v1.GetInstanceLogsRequest
import com.jlucraft.control.v1.GetInstanceRequest
import com.jlucraft.control.v1.GetInstanceSchedulingRequest
import com.jlucraft.control.v1.GetNetworkRequest
import com.jlucraft.control.v1.GetNodeScoreRequest
import com.jlucraft.control.v1.GetProposalRequest
import com.jlucraft.control.v1.QueryPlayerScoreProofRequest
import com.jlucraft.control.v1.GetPushConfigRequest
import com.jlucraft.control.v1.GetPushPreferencesRequest
import com.jlucraft.control.v1.GetSeasonLeaderboardRequest
import com.jlucraft.control.v1.GetSeasonRequest
import com.jlucraft.control.v1.GetTournamentRequest
import com.jlucraft.control.v1.GrantRoleRequest as ProtoGrantRoleRequest
import com.jlucraft.control.v1.InitPushConfigRequest
import com.jlucraft.control.v1.IssueMemberCredentialRequest
import com.jlucraft.control.v1.ListAlertsRequest
import com.jlucraft.control.v1.ListAuditAnomaliesRequest
import com.jlucraft.control.v1.ListAuditEntriesRequest
import com.jlucraft.control.v1.ListDevicesRequest
import com.jlucraft.control.v1.ListDisputesRequest
import com.jlucraft.control.v1.ListInstancesRequest
import com.jlucraft.control.v1.ListMembersRequest
import com.jlucraft.control.v1.ListNodeScoresRequest
import com.jlucraft.control.v1.ListProposalsRequest
import com.jlucraft.control.v1.ListSeasonsRequest
import com.jlucraft.control.v1.ListTeamsRequest
import com.jlucraft.control.v1.ListTournamentsRequest
import com.jlucraft.control.v1.ManageTournamentRequest
import com.jlucraft.control.v1.MigrateInstanceRequest
import com.jlucraft.control.v1.PutPushPreferencesRequest
import com.jlucraft.control.v1.RegisterPushEndpointRequest
import com.jlucraft.control.v1.RejectProposalRequest
import com.jlucraft.control.v1.ResolveAlertRequest
import com.jlucraft.control.v1.ResolveDidRequest
import com.jlucraft.control.v1.RespondCommandRequest
import com.jlucraft.control.v1.RevokeCredentialRequest as ProtoRevokeCredentialRequest
import com.jlucraft.control.v1.RevokeDeviceRequest
import com.jlucraft.control.v1.RuntimeSpec
import com.jlucraft.control.v1.SchedulingConstraints as ProtoSchedulingConstraints
import com.jlucraft.control.v1.SetInstanceSchedulingRequest
import com.jlucraft.control.v1.SignProposalRequest
import com.jlucraft.control.v1.SimulateInstanceSchedulingRequest
import com.jlucraft.control.v1.StartInstanceRequest
import com.jlucraft.control.v1.StopInstanceRequest
import com.jlucraft.control.v1.SubmitProposalDraftRequest
import com.jlucraft.control.v1.UpdateAdmissionRequest
import com.jlucraft.control.v1.UpdateInstanceConfigRequest
import com.jlucraft.control.v1.UpdateTournamentStatusRequest
import com.jlucraft.control.v1.VerifyAuditChainRequest
import com.jlucraft.control.v1.VerifyAuthResponseRequest
import com.jlucraft.control.v1.VerifyCredentialRequest
import com.jlucraft.console.data.model.AdmissionPolicy
import com.jlucraft.console.data.model.SchedulingConstraints
import com.google.protobuf.ByteString
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.SignResponse

/**
 * Encodes Kotlin [ControlRequest] sealed-class variants into serialised
 * `com.jlucraft.control.v1.ControlRequest` protobuf bytes.
 *
 * The output bytes are raw protobuf binary (no length prefix). The varint
 * framing is applied by [FramedStreamController.writeFrame].
 */
internal object ControlRequestEncoder {

    fun encode(request: ControlRequest, authContext: AuthContext?): ByteArray {
        val proto = buildProtoRequest(request, authContext)
        return proto.toByteArray()
    }

    // ── Builder ──────────────────────────────────────────────────────────

    private fun buildProtoRequest(
        request: ControlRequest,
        ctx: AuthContext?,
    ): ProtoControlRequest {
        val builder = ProtoControlRequest.newBuilder()
            .setRequestId(request.requestId)

        ctx?.let { builder.setAuth(buildAuth(it)) }

        when (request) {
            // ── Cluster / network ──────────────────────────────────────
            is ControlRequest.GetClusterHealth ->
                builder.setGetClusterHealth(GetClusterHealthRequest.getDefaultInstance())

            is ControlRequest.GetNetwork ->
                builder.setGetNetwork(GetNetworkRequest.getDefaultInstance())

            // ── Instances ─────────────────────────────────────────────
            is ControlRequest.ListInstances ->
                builder.setListInstances(ListInstancesRequest.getDefaultInstance())

            is ControlRequest.GetInstance ->
                builder.setGetInstance(
                    GetInstanceRequest.newBuilder().setInstanceId(request.instanceId).build()
                )

            is ControlRequest.CreateInstance ->
                builder.setCreateInstance(buildCreateInstance(request))

            is ControlRequest.StartInstance ->
                builder.setStartInstance(
                    StartInstanceRequest.newBuilder().setInstanceId(request.instanceId).build()
                )

            is ControlRequest.StopInstance ->
                builder.setStopInstance(
                    StopInstanceRequest.newBuilder().setInstanceId(request.instanceId).build()
                )

            is ControlRequest.DeleteInstance ->
                builder.setDeleteInstance(
                    DeleteInstanceRequest.newBuilder().setInstanceId(request.instanceId).build()
                )

            is ControlRequest.MigrateInstance ->
                builder.setMigrateInstance(
                    MigrateInstanceRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .also { b -> request.targetHost?.let { b.setTargetPeerId(it) } }
                        .build()
                )

            is ControlRequest.GetInstanceLogs ->
                builder.setGetInstanceLogs(
                    GetInstanceLogsRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .also { b -> request.tail?.let { b.setTailLines(it) } }
                        .build()
                )

            is ControlRequest.UpdateInstanceConfig ->
                builder.setUpdateInstanceConfig(
                    UpdateInstanceConfigRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .putAllConfig(request.config)
                        .build()
                )

            is ControlRequest.UpdateAdmission ->
                builder.setUpdateAdmission(
                    UpdateAdmissionRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .setAdmission(buildAdmissionPolicy(request.policy))
                        .build()
                )

            is ControlRequest.CheckAdmission ->
                builder.setCheckAdmission(
                    CheckAdmissionRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .setPlayerPeerId(request.playerPeerId ?: "")
                        .setPlayerClub(request.playerClub ?: "")
                        .setHasVc(request.hasVc)
                        .build()
                )

            is ControlRequest.StreamInstanceLogs ->
                builder.setStreamInstanceLogs(
                    com.jlucraft.control.v1.StreamInstanceLogsRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .setFollow(request.follow)
                        .build()
                )

            // ── Scheduling ────────────────────────────────────────────
            is ControlRequest.GetSchedulingConstraints ->
                builder.setGetInstanceScheduling(
                    GetInstanceSchedulingRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .build()
                )

            is ControlRequest.ApplySchedulingConstraints ->
                builder.setSetInstanceScheduling(
                    SetInstanceSchedulingRequest.newBuilder()
                        .setInstanceId(request.request.instanceId)
                        .setConstraints(buildSchedulingConstraints(request.request.constraints))
                        .build()
                )

            is ControlRequest.SimulateScheduling ->
                builder.setSimulateInstanceScheduling(
                    SimulateInstanceSchedulingRequest.newBuilder()
                        .setInstanceId(request.instanceId)
                        .setConstraints(buildSchedulingConstraints(request.constraints))
                        .build()
                )

            // ── Auth ──────────────────────────────────────────────────
            is ControlRequest.RequestChallenge ->
                builder.setCreateAuthChallenge(
                    CreateAuthChallengeRequest.newBuilder()
                        .setCmdType(request.authRequest.cmd_type)
                        .setPublicKey(request.authRequest.public_key)
                        .build()
                )

            is ControlRequest.VerifySignature ->
                builder.setVerifyAuthResponse(
                    VerifyAuthResponseRequest.newBuilder()
                        .setChallengeId(request.signResponse.challenge_id)
                        .setNonce(request.signResponse.nonce)
                        .setSignature(request.signResponse.signature)
                        .setSignatureAlg(request.signResponse.signature_alg)
                        .build()
                )

            // ── Commands ──────────────────────────────────────────────
            is ControlRequest.CreateCommand ->
                builder.setCreateCommand(
                    com.jlucraft.control.v1.CreateCommandRequest.newBuilder()
                        .setCmdType(request.authRequest.cmd_type)
                        .setPublicKey(request.authRequest.public_key)
                        .build()
                )

            is ControlRequest.RespondCommand ->
                builder.setRespondCommand(
                    RespondCommandRequest.newBuilder()
                        .setChallengeId(request.challengeId)
                        .setResponse(
                            VerifyAuthResponseRequest.newBuilder()
                                .setChallengeId(request.challengeId)
                                .setNonce(request.signResponse.nonce)
                                .setSignature(request.signResponse.signature)
                                .setSignatureAlg(request.signResponse.signature_alg)
                                .build()
                        )
                        .build()
                )

            // ── Proposals ─────────────────────────────────────────────
            is ControlRequest.ListProposals ->
                builder.setListProposals(ListProposalsRequest.getDefaultInstance())

            is ControlRequest.GetProposal ->
                builder.setGetProposal(
                    GetProposalRequest.newBuilder().setProposalId(request.proposalId).build()
                )

            is ControlRequest.CreateProposal ->
                builder.setCreateProposal(
                    com.jlucraft.control.v1.CreateProposalRequest.newBuilder()
                        .setProposalType(request.proposalType)
                        .build()
                )

            is ControlRequest.CreateProposalDraft ->
                builder.setCreateProposalDraft(
                    com.jlucraft.control.v1.CreateProposalDraftRequest.newBuilder()
                        .setProposalType(request.proposalType)
                        .build()
                )

            is ControlRequest.SubmitProposalDraft ->
                builder.setSubmitProposalDraft(
                    SubmitProposalDraftRequest.newBuilder().setProposalId(request.draftId).build()
                )

            is ControlRequest.SignProposal ->
                builder.setSignProposal(
                    SignProposalRequest.newBuilder()
                        .setProposalId(request.proposalId)
                        .setPublicKey(request.pubkey)
                        .setSignature(request.signature)
                        .build()
                )

            is ControlRequest.RejectProposal ->
                builder.setRejectProposal(
                    RejectProposalRequest.newBuilder().setProposalId(request.proposalId).build()
                )

            is ControlRequest.ExecuteProposal ->
                builder.setExecuteProposal(
                    ExecuteProposalRequest.newBuilder().setProposalId(request.proposalId).build()
                )

            // ── Audit ─────────────────────────────────────────────────
            is ControlRequest.ListAuditEntries ->
                builder.setListAuditEntries(
                    ListAuditEntriesRequest.newBuilder()
                        .also { b -> request.cmdType?.let { b.setCmdType(it) } }
                        .build()
                )

            is ControlRequest.VerifyAuditChain ->
                builder.setVerifyAuditChain(VerifyAuditChainRequest.getDefaultInstance())

            is ControlRequest.ListAuditAnomalies ->
                builder.setListAuditAnomalies(ListAuditAnomaliesRequest.getDefaultInstance())

            // ── Alerts ────────────────────────────────────────────────
            is ControlRequest.ListAlerts ->
                builder.setListAlerts(
                    ListAlertsRequest.newBuilder()
                        .setIncludeResolved(request.includeResolved)
                        .also { b -> request.severity?.let { b.setSeverity(it) } }
                        .build()
                )

            is ControlRequest.AcknowledgeAlert ->
                builder.setAcknowledgeAlert(
                    AcknowledgeAlertRequest.newBuilder().setAlertId(request.alertId).build()
                )

            is ControlRequest.ResolveAlert ->
                builder.setResolveAlert(
                    ResolveAlertRequest.newBuilder().setAlertId(request.alertId).build()
                )

            // ── Tournaments ───────────────────────────────────────────
            is ControlRequest.ListTournaments ->
                builder.setListTournaments(ListTournamentsRequest.getDefaultInstance())

            is ControlRequest.GetTournament ->
                builder.setGetTournament(
                    GetTournamentRequest.newBuilder().setTournamentId(request.tournamentId).build()
                )

            is ControlRequest.CreateTournament ->
                builder.setCreateTournament(
                    ProtoCreateTournamentRequest.newBuilder()
                        .setName(request.request.name)
                        .setGameType(request.request.game_type)
                        .setMode(request.request.mode)
                        .setMaxParticipants(request.request.max_participants)
                        .setMinMemberScore(request.request.min_member_score)
                        .build()
                )

            is ControlRequest.UpdateTournamentStatus ->
                builder.setManageTournament(
                    ManageTournamentRequest.newBuilder()
                        .setTournamentId(request.tournamentId)
                        .setAction(request.status.name.lowercase())
                        .build()
                )

            is ControlRequest.ListTournamentMatches ->
                builder.setListTournamentMatches(
                    com.jlucraft.control.v1.ListTournamentMatchesRequest.newBuilder()
                        .setTournamentId(request.tournamentId)
                        .build()
                )

            is ControlRequest.ListTeams ->
                builder.setListTeams(ListTeamsRequest.getDefaultInstance())

            // ── Disputes ──────────────────────────────────────────────
            is ControlRequest.ListDisputes ->
                builder.setListDisputes(
                    ListDisputesRequest.newBuilder()
                        .also { b -> request.tournamentId?.let { b.setTournamentId(it) } }
                        .build()
                )

            is ControlRequest.GetDispute ->
                builder.setGetDispute(
                    GetDisputeRequest.newBuilder().setDisputeId(request.disputeId).build()
                )

            is ControlRequest.CreateDispute ->
                builder.setCreateMatchDispute(
                    CreateMatchDisputeRequest.newBuilder()
                        .setTournamentId(request.tournamentId)
                        .setReason(request.request.reason)
                        .addAllEvidenceUrls(request.request.evidenceUrls)
                        .build()
                )

            // ── Seasons ───────────────────────────────────────────────
            is ControlRequest.ListSeasons ->
                builder.setListSeasons(ListSeasonsRequest.getDefaultInstance())

            is ControlRequest.GetSeason ->
                builder.setGetSeason(
                    GetSeasonRequest.newBuilder().setSeasonId(request.seasonId).build()
                )

            is ControlRequest.GetCurrentSeason ->
                builder.setGetCurrentSeason(GetCurrentSeasonRequest.getDefaultInstance())

            is ControlRequest.GetSeasonLeaderboard ->
                builder.setGetSeasonLeaderboard(
                    GetSeasonLeaderboardRequest.newBuilder().setSeasonId(request.seasonId).build()
                )

            is ControlRequest.ArchiveSeason ->
                builder.setArchiveSeason(
                    ArchiveSeasonRequest.newBuilder().setSeasonId(request.seasonId).build()
                )

            is ControlRequest.CreateSeason ->
                builder.setCreateSeason(
                    CreateSeasonRequest.newBuilder()
                        .setName(request.name)
                        .setStartsAt(request.startDate)
                        .setEndsAt(request.endDate)
                        .build()
                )

            // ── Devices ───────────────────────────────────────────────
            is ControlRequest.ListDevices ->
                builder.setListDevices(ListDevicesRequest.getDefaultInstance())

            is ControlRequest.RevokeDevice ->
                builder.setRevokeDevice(
                    RevokeDeviceRequest.newBuilder()
                        .setPublicKey(request.pubkey)
                        .setReason(request.reason)
                        .build()
                )

            is ControlRequest.EmergencyRevokeDevice ->
                builder.setEmergencyRevokeDevice(
                    EmergencyRevokeDeviceRequest.newBuilder()
                        .setPublicKey(request.pubkey)
                        .setReason(request.reason)
                        .build()
                )

            // ── Members / VC / DID ────────────────────────────────────
            is ControlRequest.ListMembers ->
                builder.setListMembers(ListMembersRequest.getDefaultInstance())

            is ControlRequest.IssueCredential ->
                builder.setIssueMemberCredential(
                    IssueMemberCredentialRequest.newBuilder()
                        .setSubjectDid(request.request.subjectDid)
                        .setCredentialType(request.request.reason ?: "")
                        .build()
                )

            is ControlRequest.RevokeCredential ->
                builder.setRevokeCredential(
                    ProtoRevokeCredentialRequest.newBuilder()
                        .setSubjectDid(request.request.subjectDid)
                        .build()
                )

            is ControlRequest.GrantRole ->
                builder.setGrantRole(
                    ProtoGrantRoleRequest.newBuilder()
                        .setSubjectDid(request.request.subjectDid)
                        .setRole(request.request.role)
                        .build()
                )

            is ControlRequest.VerifyVc ->
                builder.setVerifyCredential(
                    VerifyCredentialRequest.newBuilder()
                        .setCredential(ByteString.copyFromUtf8(request.request.vcJwt))
                        .build()
                )

            is ControlRequest.ResolveDid ->
                builder.setResolveDid(
                    ResolveDidRequest.newBuilder().setDid(request.did).build()
                )

            // ── Node scores ───────────────────────────────────────────
            is ControlRequest.ListNodeScores ->
                builder.setListNodeScores(ListNodeScoresRequest.getDefaultInstance())

            is ControlRequest.GetNodeScore ->
                builder.setGetNodeScore(
                    GetNodeScoreRequest.newBuilder().setPeerId(request.peerId).build()
                )

            // ── Oracle (not used from Android) ────────────────────────
            is ControlRequest.GetOracleScore ->
                builder.setQueryPlayerScoreProof(
                    QueryPlayerScoreProofRequest.newBuilder().setPlayerId(request.playerId).build()
                )

            // ── Push management ───────────────────────────────────────
            is ControlRequest.RegisterPushEndpoint ->
                builder.setRegisterPushEndpoint(
                    RegisterPushEndpointRequest.newBuilder()
                        .setEndpoint(request.endpoint)
                        .setDevicePublicKey(request.devicePubkey)
                        .build()
                )

            is ControlRequest.GetPushConfig ->
                builder.setGetPushConfig(GetPushConfigRequest.getDefaultInstance())

            is ControlRequest.InitPushConfig ->
                builder.setInitPushConfig(InitPushConfigRequest.getDefaultInstance())

            is ControlRequest.GetPushPreferences ->
                builder.setGetPushPreferences(GetPushPreferencesRequest.getDefaultInstance())

            is ControlRequest.UpdatePushPreferences ->
                builder.setPutPushPreferences(
                    PutPushPreferencesRequest.newBuilder()
                        .addAllEnabledEventTypes(request.enabledEventTypes)
                        .setDndEnabled(request.dndEnabled)
                        .setDndStartHour(request.dndStartHour)
                        .setDndEndHour(request.dndEndHour)
                        .build()
                )
        }

        return builder.build()
    }

    // ── Sub-builders ─────────────────────────────────────────────────────

    private fun buildAuth(ctx: AuthContext): ProtoAuth =
        ProtoAuth.newBuilder()
            .setActor(
                ProtoPeerIdentity.newBuilder()
                    .setDid(ctx.subjectDid)
                    .build()
            )
            .setSignature(
                ProtoSignedRequest.newBuilder()
                    .setNonce(ctx.nonce)
                    .setSignature(ctx.signature)
                    .setChallengeId(ctx.challengeId)
                    .build()
            )
            .build()

    private fun buildAdmissionPolicy(policy: AdmissionPolicy): ProtoAdmissionPolicy =
        ProtoAdmissionPolicy.newBuilder()
            .setMode(policy.mode)
            .addAllAllowedClubs(policy.allowed_clubs)
            .addAllAllowedPlayers(policy.allowed_players)
            .build()

    private fun buildSchedulingConstraints(c: SchedulingConstraints): ProtoSchedulingConstraints =
        ProtoSchedulingConstraints.newBuilder()
            .setPreferredPeerId(c.preferredHosts.firstOrNull() ?: "")
            .addAllAvoidPeerIds(c.blacklistedHosts)
            .putAllLabels(c.requiredLabels)
            .build()

    private fun buildCreateInstance(
        request: ControlRequest.CreateInstance,
    ): ProtoCreateInstanceRequest {
        val r = request.request
        return ProtoCreateInstanceRequest.newBuilder()
            .setName(r.name)
            .setKind(r.kind)
            .setOwner(r.owner)
            .setClub(r.club)
            .setRuntime(
                RuntimeSpec.newBuilder()
                    .setImage(r.runtime.image)
                    .putAllEnv(r.runtime.env)
                    .putAllLabels(r.runtime.labels)
                    .build()
            )
            .setAdmission(buildAdmissionPolicy(r.admission))
            .setAutoRestart(r.autoRestart)
            .build()
    }
}
