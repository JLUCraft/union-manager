package com.jlucraft.console.data.remote.libp2p

import com.jlucraft.console.data.model.AdmissionPolicy
import com.jlucraft.console.data.model.Alert
import com.jlucraft.console.data.model.AuditAnomaly
import com.jlucraft.console.data.model.AuditChainVerification
import com.jlucraft.console.data.model.AuditEntry
import com.jlucraft.console.data.model.Device
import com.jlucraft.console.data.model.DisputeMatch
import com.jlucraft.console.data.model.Instance
import com.jlucraft.console.data.model.InstanceRuntimeSpec
import com.jlucraft.console.data.model.Leaderboard
import com.jlucraft.console.data.model.LeaderboardEntry
import com.jlucraft.console.data.model.Match
import com.jlucraft.console.data.model.MatchResult
import com.jlucraft.console.data.model.MemberSummary
import com.jlucraft.console.data.model.NodeScore
import com.jlucraft.console.data.model.OracleProof
import com.jlucraft.console.data.model.OracleScore
import com.jlucraft.console.data.model.PlayerResult
import com.jlucraft.console.data.model.Proposal
import com.jlucraft.console.data.model.ProposalSignature
import com.jlucraft.console.data.model.ResourceRequest
import com.jlucraft.console.data.model.SchedulingConstraints
import com.jlucraft.console.data.model.SchedulingConstraintsResponse
import com.jlucraft.console.data.model.SchedulingSimulation
import com.jlucraft.console.data.model.Season
import com.jlucraft.console.data.model.Team
import com.jlucraft.console.data.model.Tournament
import com.jlucraft.console.data.model.TournamentStatus
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.AuthResult
import com.jlucraft.console.data.remote.ClusterHealthResponse
import com.jlucraft.console.data.remote.CommandResult
import com.jlucraft.console.data.model.DidServerResponse
import com.jlucraft.console.data.model.IssueCredentialResponse
import com.jlucraft.console.data.model.RevokeCredentialResponse
import com.jlucraft.console.data.model.GrantRoleResponse
import com.jlucraft.console.data.model.VcVerificationResult
import com.jlucraft.console.data.remote.NetworkSnapshot
import com.jlucraft.console.data.remote.PushConfigResponse
import com.jlucraft.console.data.remote.PushPreferencesResponse
import com.jlucraft.control.v1.ControlResponse as ProtoControlResponse

/**
 * Decodes serialised `com.jlucraft.control.v1.ControlResponse` protobuf bytes
 * into Kotlin [ControlResponse] sealed-class instances.
 *
 * Field mapping notes:
 * - Proto uses snake_case field names; Kotlin domain models use camelCase.
 * - Proto fields with no Kotlin equivalent default to null / empty / 0.
 * - Unknown body cases return [ControlResponse.Unknown] with the raw bytes.
 */
private fun String.ifEmptyNull(): String? = this.ifEmptyNull()

internal object ControlResponseDecoder {

    fun decode(bytes: ByteArray): ControlResponse {
        val proto = runCatching { ProtoControlResponse.parseFrom(bytes) }.getOrElse { e ->
            return ControlResponse.Error("DECODE_FAILED", e.message ?: "Failed to parse ControlResponse")
        }

        // Surface proto-level errors first.
        if (proto.hasError() && proto.error.code.isNotEmpty()) {
            return ControlResponse.Error(proto.error.code, proto.error.message)
        }

        return when (proto.bodyCase) {
            ProtoControlResponse.BodyCase.CLUSTER_HEALTH ->
                ControlResponse.ClusterHealth(decodeClusterHealth(proto))

            ProtoControlResponse.BodyCase.NETWORK ->
                ControlResponse.NetworkSnapshot(decodeNetworkSnapshot(proto))

            ProtoControlResponse.BodyCase.INSTANCES ->
                ControlResponse.InstanceList(proto.instances.instancesList.map { decodeInstance(it) })

            ProtoControlResponse.BodyCase.INSTANCE ->
                ControlResponse.InstanceDetail(decodeInstance(proto.instance.instance))

            ProtoControlResponse.BodyCase.INSTANCE_LOGS ->
                ControlResponse.InstanceLogs(proto.instanceLogs.content.lines())

            ProtoControlResponse.BodyCase.ADMISSION_CHECK ->
                ControlResponse.AdmissionCheck(proto.admissionCheck.allowed)

            ProtoControlResponse.BodyCase.AUTH_CHALLENGE ->
                ControlResponse.AuthChallengeResponse(decodeAuthChallenge(proto))

            ProtoControlResponse.BodyCase.AUTH_RESULT ->
                ControlResponse.AuthVerificationResult(decodeAuthResult(proto))

            ProtoControlResponse.BodyCase.COMMAND_CHALLENGE ->
                ControlResponse.AuthChallengeResponse(
                    decodeAuthChallenge(proto, fromCommandChallenge = true)
                )

            ProtoControlResponse.BodyCase.COMMAND_RESULT ->
                ControlResponse.CommandResultResponse(
                    CommandResult(result = proto.commandResult.result)
                )

            ProtoControlResponse.BodyCase.PROPOSALS ->
                ControlResponse.ProposalList(proto.proposals.proposalsList.map { decodeProposal(it) })

            ProtoControlResponse.BodyCase.PROPOSAL ->
                ControlResponse.ProposalDetail(decodeProposal(proto.proposal.proposal))

            ProtoControlResponse.BodyCase.AUDIT_ENTRIES ->
                ControlResponse.AuditEntryList(proto.auditEntries.entriesList.map { decodeAuditEntry(it) })

            ProtoControlResponse.BodyCase.AUDIT_VERIFICATION ->
                ControlResponse.AuditChainVerification(
                    AuditChainVerification(
                        valid = proto.auditVerification.valid,
                        brokenCount = proto.auditVerification.errorsCount,
                    )
                )

            ProtoControlResponse.BodyCase.AUDIT_ANOMALIES ->
                ControlResponse.AuditAnomalyList(
                    proto.auditAnomalies.anomaliesList.map { decodeAuditAnomaly(it) }
                )

            ProtoControlResponse.BodyCase.ALERTS ->
                ControlResponse.AlertList(proto.alerts.alertsList.map { decodeAlert(it) })

            ProtoControlResponse.BodyCase.ALERT ->
                ControlResponse.AlertUpdated(decodeAlert(proto.alert.alert))

            ProtoControlResponse.BodyCase.TOURNAMENTS ->
                ControlResponse.TournamentList(
                    proto.tournaments.tournamentsList.map { decodeTournament(it) }
                )

            ProtoControlResponse.BodyCase.TOURNAMENT ->
                ControlResponse.TournamentDetail(decodeTournament(proto.tournament.tournament))

            ProtoControlResponse.BodyCase.MATCHES ->
                ControlResponse.MatchList(proto.matches.matchesList.map { decodeMatch(it) })

            ProtoControlResponse.BodyCase.MATCH ->
                ControlResponse.MatchList(listOf(decodeMatch(proto.match.match)))

            ProtoControlResponse.BodyCase.TEAMS ->
                ControlResponse.TeamList(proto.teams.teamsList.map { decodeTeam(it) })

            ProtoControlResponse.BodyCase.SEASONS ->
                ControlResponse.SeasonList(proto.seasons.seasonsList.map { decodeSeason(it) })

            ProtoControlResponse.BodyCase.SEASON ->
                ControlResponse.SeasonDetail(decodeSeason(proto.season.season))

            ProtoControlResponse.BodyCase.LEADERBOARD ->
                ControlResponse.LeaderboardData(
                    Leaderboard(
                        solo = proto.leaderboard.entriesList.map { e ->
                            LeaderboardEntry(
                                player_id = e.playerId,
                                total_score = e.score.toDouble(),
                                tournaments_played = 0,
                            )
                        }
                    )
                )

            ProtoControlResponse.BodyCase.DEVICES ->
                ControlResponse.DeviceList(proto.devices.devicesList.map { decodeDevice(it) })

            ProtoControlResponse.BodyCase.DEVICE ->
                ControlResponse.DeviceUpdated(decodeDevice(proto.device.device))

            ProtoControlResponse.BodyCase.DISPUTES ->
                ControlResponse.DisputeList(proto.disputes.disputesList.map { decodeDispute(it) })

            ProtoControlResponse.BodyCase.DISPUTE ->
                ControlResponse.DisputeDetail(decodeDispute(proto.dispute.dispute))

            ProtoControlResponse.BodyCase.MEMBERS ->
                ControlResponse.MemberList(
                    proto.members.membersList.map { m ->
                        MemberSummary(
                            subjectDid = m.subjectDid,
                            role = m.role,
                        )
                    }
                )

            ProtoControlResponse.BodyCase.MEMBER_MUTATION ->
                ControlResponse.UnitSuccess("member_mutation:${proto.memberMutation.status}")

            ProtoControlResponse.BodyCase.NODE_SCORES ->
                ControlResponse.NodeScoreList(proto.nodeScores.scoresList.map { decodeNodeScore(it) })

            ProtoControlResponse.BodyCase.NODE_SCORE ->
                ControlResponse.NodeScoreDetail(decodeNodeScore(proto.nodeScore.score))

            ProtoControlResponse.BodyCase.PUSH_CONFIG ->
                ControlResponse.PushConfig(
                    PushConfigResponse(
                        configured = proto.pushConfig.configured,
                        vapidPublicKey = proto.pushConfig.vapidPublicKey.ifEmptyNull(),
                    )
                )

            ProtoControlResponse.BodyCase.PUSH_PREFERENCES ->
                ControlResponse.PushPreferences(decodePushPreferences(proto))

            ProtoControlResponse.BodyCase.SCHEDULING_CONSTRAINTS ->
                ControlResponse.SchedulingConstraints(
                    SchedulingConstraints(
                        preferredHosts = proto.schedulingConstraints.constraints.preferredPeerId
                        .takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList(),
                        blacklistedHosts = proto.schedulingConstraints.constraints.avoidPeerIdsList.toList(),
                        requiredLabels = proto.schedulingConstraints.constraints.labelsMap,
                    )
                )

            ProtoControlResponse.BodyCase.SCHEDULING_SIMULATION ->
                ControlResponse.SchedulingSimulation(
                    SchedulingSimulation(
                        instanceId = "",
                        currentHost = proto.schedulingSimulation.selectedPeerId,
                        wouldMigrate = proto.schedulingSimulation.candidatesCount > 0,
                        targetHost = proto.schedulingSimulation.selectedPeerId.ifEmptyNull(),
                        eligibleHosts = proto.schedulingSimulation.candidatesList.map { it.peerId },
                    )
                )

            ProtoControlResponse.BodyCase.DID_RESOLUTION ->
                ControlResponse.DidResolution(
                    DidServerResponse(
                        didResolutionMetadata = if (proto.didResolution.error.isNotEmpty())
                            com.jlucraft.console.data.model.DidResolutionMetadata(
                                error = proto.didResolution.error
                            ) else null,
                    )
                )

            ProtoControlResponse.BodyCase.CREDENTIAL_VERIFICATION ->
                ControlResponse.VcVerification(
                    VcVerificationResult(
                        valid = proto.credentialVerification.valid,
                        subject = proto.credentialVerification.subjectDid.ifEmptyNull(),
                    )
                )

            ProtoControlResponse.BodyCase.PLAYER_SCORE_PROOF ->
                ControlResponse.OracleScoreResult(
                    OracleScore(
                        playerId = proto.playerScoreProof.playerId,
                        aggregateScore = proto.playerScoreProof.score,
                        proof = OracleProof(
                            root = proto.playerScoreProof.merkleRoot,
                            proofNodes = proto.playerScoreProof.proofList.toList(),
                            leafHash = proto.playerScoreProof.proofList.firstOrNull() ?: "",
                        ),
                        verifiedAt = "",
                        serverVerified = true,
                    )
                )

            ProtoControlResponse.BodyCase.BODY_NOT_SET, null ->
                ControlResponse.UnitSuccess()

            else -> ControlResponse.Unknown(bytes)
        }
    }

    // ── Entity decoders ───────────────────────────────────────────────────

    private fun decodeClusterHealth(proto: ProtoControlResponse): ClusterHealthResponse =
        ClusterHealthResponse(
            status = proto.clusterHealth.status,
            peer_id = "",                                     // not in proto
            connected_peers = proto.clusterHealth.nodeCount,
            running_instances = proto.clusterHealth.runningInstances,
            consensus_role = "",                              // not in proto
            last_announcement_at = null,
        )

    private fun decodeNetworkSnapshot(proto: ProtoControlResponse): NetworkSnapshot =
        NetworkSnapshot(
            connected_peers = proto.network.nodesList.map { it.peerId },
        )

    private fun decodeInstance(inst: com.jlucraft.control.v1.Instance): Instance =
        Instance(
            id = inst.id,
            name = inst.name,
            kind = inst.kind,
            status = inst.status,
            owner = inst.owner.ifEmptyNull(),
            club = inst.club.ifEmptyNull(),
            currentHost = inst.hostPeerId.ifEmptyNull(),
            playerCount = inst.playerCount,
            createdAt = inst.createdAt.ifEmptyNull(),
            updatedAt = inst.updatedAt.ifEmptyNull(),
            migrationTarget = inst.migrationTarget.ifEmptyNull(),
            admission = if (inst.hasAdmission()) AdmissionPolicy(
                mode = inst.admission.mode,
                allowed_clubs = inst.admission.allowedClubsList.toList(),
                allowed_players = inst.admission.allowedPlayersList.toList(),
            ) else null,
            runtime = if (inst.hasRuntime()) InstanceRuntimeSpec(
                image = inst.runtime.image,
                env = inst.runtime.envMap,
                labels = inst.runtime.labelsMap,
            ) else null,
            resources = if (inst.hasResources()) ResourceRequest(
                cpuCores = inst.resources.cpuCores,
                memoryGb = inst.resources.memoryGb.toInt(),
                diskGb = inst.resources.diskGb.toInt(),
            ) else null,
        )

    private fun decodeProposal(p: com.jlucraft.control.v1.Proposal): Proposal =
        Proposal(
            id = p.id,
            proposalType = p.proposalType,
            payload = com.jlucraft.console.data.model.UnknownProposalPayload(type = p.proposalType.ifEmpty { "unknown" }),
            proposer = p.proposer,
            expiresAt = "",
            signatures = p.signaturesList.map { s ->
                ProposalSignature(pubkey = s.publicKey, signature = s.signature, signedAt = s.signedAt)
            },
            status = p.status,
            createdAt = p.createdAt,
        )

    private fun decodeAuditEntry(e: com.jlucraft.control.v1.AuditEntry): AuditEntry =
        AuditEntry(
            id = 0L,
            ts = 0L,
            actorPubkey = e.actor,
            actorRole = "",
            cmdType = e.command,
            target = e.target,
            payloadHash = e.payloadHash,
            signatures = emptyList(),
            outcome = e.outcome,
            error = null,
            prevHash = e.prevHash,
        )

    private fun decodeAuditAnomaly(a: com.jlucraft.control.v1.AuditAnomaly): AuditAnomaly =
        AuditAnomaly(
            rule = a.rule,
            actor = "",
            detail = a.description,
            detectedAt = 0L,
        )

    private fun decodeAlert(a: com.jlucraft.control.v1.Alert): Alert =
        Alert(
            id = a.id,
            alertType = a.alertType,
            severity = a.severity,
            message = a.message,
            target = a.target,
            createdAt = a.createdAt,
            resolvedAt = a.resolvedAt.ifEmptyNull(),
            acknowledgedAt = null,
        )

    private fun decodeTournament(t: com.jlucraft.control.v1.Tournament): Tournament =
        Tournament(
            id = t.id,
            name = t.name,
            game_type = t.gameType,
            mode = t.mode,
            status = runCatching { TournamentStatus.valueOf(t.status.replaceFirstChar { it.uppercase() }) }
                .getOrDefault(TournamentStatus.Draft),
            max_participants = t.maxParticipants,
            participant_count = t.participantCount,
            min_member_score = t.minMemberScore,
            created_at = t.createdAt,
            created_by = t.createdBy,
        )

    private fun decodeMatch(m: com.jlucraft.control.v1.Match): Match =
        Match(
            id = m.id,
            tournament_id = m.tournamentId,
            round = m.round,
            participants = m.participantsList.toList(),
            status = runCatching {
                com.jlucraft.console.data.model.MatchStatus.valueOf(
                    m.status.replaceFirstChar { it.uppercase() }
                )
            }.getOrDefault(com.jlucraft.console.data.model.MatchStatus.Scheduled),
            scheduled_at = m.scheduledAt,
            instance_id = m.instanceId.ifEmptyNull(),
            result = if (m.result.rankingsCount > 0) MatchResult(
                rankings = m.result.rankingsList.map { r ->
                    PlayerResult(
                        player_id = r.playerId,
                        score = r.score,
                        kills = r.kills,
                        deaths = r.deaths,
                        survive_minutes = r.surviveMinutes,
                    )
                }
            ) else null,
        )

    private fun decodeTeam(t: com.jlucraft.control.v1.Team): Team =
        Team(
            id = t.id,
            name = t.name,
            members = t.membersList.toList(),
            total_score = t.totalScore,
        )

    private fun decodeSeason(s: com.jlucraft.control.v1.Season): Season =
        Season(
            id = s.id,
            name = s.name,
            status = s.status,
            start_date = s.startsAt,
            end_date = s.endsAt.ifEmptyNull(),
            tournament_ids = s.tournamentIdsList.toList(),
        )

    private fun decodeDevice(d: com.jlucraft.control.v1.Device): Device =
        Device(
            pubkey = d.publicKey,
            status = d.status,
            platform = "",
            ownerPeerId = d.ownerPeerId.ifEmptyNull(),
            deviceName = d.deviceName.ifEmptyNull(),
            registeredAt = d.registeredAt.ifEmptyNull(),
            revokedAt = d.revokedAt.ifEmptyNull(),
        )

    private fun decodeDispute(d: com.jlucraft.control.v1.Dispute): DisputeMatch =
        DisputeMatch(
            disputeId = d.disputeId,
            tournamentId = d.tournamentId,
            matchId = d.matchId,
            status = d.status,
            reason = d.reason,
            evidenceUrls = d.evidenceUrlsList.toList(),
            submittedBy = d.submittedBy,
            resolution = d.resolution.ifEmptyNull(),
            createdAt = d.createdAt,
            resolvedAt = d.resolvedAt.ifEmptyNull(),
        )

    private fun decodeNodeScore(n: com.jlucraft.control.v1.NodeScore): NodeScore =
        NodeScore(
            peer_id = n.peerId,
            uptime_score = n.uptimeScore.toDouble(),
            performance_score = n.performanceScore.toDouble(),
            governance_score = n.governanceScore.toDouble(),
            penalty = n.penalty.toDouble(),
            final_score = (n.uptimeScore + n.performanceScore + n.governanceScore - n.penalty).toDouble(),
            last_updated = "",
        )

    private fun decodeAuthChallenge(
        proto: ProtoControlResponse,
        fromCommandChallenge: Boolean = false,
    ): AuthChallenge {
        val ch = if (fromCommandChallenge) proto.commandChallenge.challenge else proto.authChallenge
        return AuthChallenge(
            challenge_id = ch.challengeId,
            nonce = ch.nonce,
            issued_at = ch.issuedAt,
            expires_at = ch.expiresAt,
            ttl_seconds = ch.ttlSeconds.toLong(),
            cmd_type = ch.cmdType,
            payload_hash = ch.payloadHash,
            human_summary = ch.humanSummary,
            risk_level = ch.riskLevel,
            required_role = ch.requiredRole,
        )
    }

    private fun decodeAuthResult(proto: ProtoControlResponse): AuthResult =
        AuthResult(
            success = proto.authResult.verified,
            message = proto.authResult.role.ifEmpty { proto.authResult.actorPeerId },
        )

    private fun decodePushPreferences(proto: ProtoControlResponse): PushPreferencesResponse =
        PushPreferencesResponse(
            enabledEventTypes = proto.pushPreferences.enabledEventTypesList.toList(),
            dndEnabled = proto.pushPreferences.dndEnabled,
            dndStartHour = proto.pushPreferences.dndStartHour,
            dndEndHour = proto.pushPreferences.dndEndHour,
        )
}
