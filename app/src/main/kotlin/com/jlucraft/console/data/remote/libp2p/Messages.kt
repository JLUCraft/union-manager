package com.jlucraft.console.data.remote.libp2p

import com.jlucraft.console.data.model.*
import com.jlucraft.console.data.remote.AuthChallenge
import com.jlucraft.console.data.remote.AuthRequest
import com.jlucraft.console.data.remote.AuthResult
import com.jlucraft.console.data.remote.CommandResult
import com.jlucraft.console.data.remote.PushConfigResponse
import com.jlucraft.console.data.remote.PushPreferencesResponse
import com.jlucraft.console.data.remote.SignResponse


 *
sealed class ControlRequest {
    abstract val requestId: String


    data class CreateInstance(val request: CreateInstanceRequest, override val requestId: String) : ControlRequest()
    data class ListInstances(override val requestId: String) : ControlRequest()
    data class GetInstance(val instanceId: String, override val requestId: String) : ControlRequest()
    data class StartInstance(val instanceId: String, override val requestId: String) : ControlRequest()
    data class StopInstance(val instanceId: String, val reason: String?, override val requestId: String) : ControlRequest()
    data class DeleteInstance(val instanceId: String, override val requestId: String) : ControlRequest()
    data class MigrateInstance(val instanceId: String, val targetHost: String?, override val requestId: String) : ControlRequest()
    data class UpdateInstanceConfig(val instanceId: String, val config: Map<String, String>, override val requestId: String) : ControlRequest()
    data class UpdateAdmission(val instanceId: String, val policy: AdmissionPolicy, override val requestId: String) : ControlRequest()
    data class CheckAdmission(val instanceId: String, val playerPeerId: String?, val playerClub: String?, val hasVc: Boolean, override val requestId: String) : ControlRequest()
    data class GetInstanceLogs(val instanceId: String, val tail: Int?, val keyword: String?, override val requestId: String) : ControlRequest()
    data class GetSchedulingConstraints(val instanceId: String, override val requestId: String) : ControlRequest()
    data class ApplySchedulingConstraints(val request: ApplySchedulingConstraintsRequest, override val requestId: String) : ControlRequest()
    data class SimulateScheduling(val instanceId: String, val constraints: SchedulingConstraints, override val requestId: String) : ControlRequest()


    data class GetClusterHealth(override val requestId: String) : ControlRequest()
    data class GetNetwork(override val requestId: String) : ControlRequest()


    data class RequestChallenge(val authRequest: AuthRequest, override val requestId: String) : ControlRequest()
    data class VerifySignature(val signResponse: SignResponse, override val requestId: String) : ControlRequest()


    data class CreateCommand(val authRequest: AuthRequest, override val requestId: String) : ControlRequest()
    data class RespondCommand(val challengeId: String, val signResponse: SignResponse, override val requestId: String) : ControlRequest()


    data class ListProposals(override val requestId: String) : ControlRequest()
    data class GetProposal(val proposalId: String, override val requestId: String) : ControlRequest()
    data class CreateProposal(val proposalType: String, val payload: ProposalPayload, val proposer: String, override val requestId: String) : ControlRequest()
    data class SignProposal(val proposalId: String, val pubkey: String, val signature: String, override val requestId: String) : ControlRequest()
    data class ExecuteProposal(val proposalId: String, override val requestId: String) : ControlRequest()
    data class RejectProposal(val proposalId: String, override val requestId: String) : ControlRequest()
    data class CreateProposalDraft(val proposalType: String, val payload: ProposalPayload, val proposer: String, override val requestId: String) : ControlRequest()
    data class SubmitProposalDraft(val draftId: String, override val requestId: String) : ControlRequest()


    data class ListAuditEntries(val cmdType: String?, val limit: Int, override val requestId: String) : ControlRequest()
    data class VerifyAuditChain(override val requestId: String) : ControlRequest()
    data class ListAuditAnomalies(override val requestId: String) : ControlRequest()


    data class ListAlerts(val severity: String?, val includeResolved: Boolean, override val requestId: String) : ControlRequest()
    data class AcknowledgeAlert(val alertId: String, override val requestId: String) : ControlRequest()
    data class ResolveAlert(val alertId: String, override val requestId: String) : ControlRequest()


    data class ListTournaments(override val requestId: String) : ControlRequest()
    data class GetTournament(val tournamentId: String, override val requestId: String) : ControlRequest()
    data class CreateTournament(val request: CreateTournamentRequest, override val requestId: String) : ControlRequest()
    data class UpdateTournamentStatus(val tournamentId: String, val status: TournamentStatus, override val requestId: String) : ControlRequest()
    data class ListTournamentMatches(val tournamentId: String, override val requestId: String) : ControlRequest()
    data class ListTeams(override val requestId: String) : ControlRequest()


    data class ListDisputes(val tournamentId: String?, override val requestId: String) : ControlRequest()
    data class GetDispute(val disputeId: String, override val requestId: String) : ControlRequest()
    data class CreateDispute(val tournamentId: String, val request: CreateDisputeRequest, override val requestId: String) : ControlRequest()


    data class ListSeasons(override val requestId: String) : ControlRequest()
    data class GetSeason(val seasonId: String, override val requestId: String) : ControlRequest()
    data class GetCurrentSeason(override val requestId: String) : ControlRequest()
    data class GetSeasonLeaderboard(val seasonId: String, override val requestId: String) : ControlRequest()
    data class ArchiveSeason(val seasonId: String, override val requestId: String) : ControlRequest()
    data class CreateSeason(val name: String, val startDate: String, val endDate: String, override val requestId: String) : ControlRequest()


    data class PauseMatch(val matchId: String, override val requestId: String) : ControlRequest()
    data class ResumeMatch(val matchId: String, override val requestId: String) : ControlRequest()
    data class ResetMatch(val matchId: String, override val requestId: String) : ControlRequest()
    data class JudgeMatch(val matchId: String, val winnerId: String, val reason: String, override val requestId: String) : ControlRequest()


    data class ListDevices(override val requestId: String) : ControlRequest()
    data class RevokeDevice(val pubkey: String, val reason: String, val revokedBy: String, override val requestId: String) : ControlRequest()
    data class EmergencyRevokeDevice(val pubkey: String, val reason: String, val revokedBy: String, override val requestId: String) : ControlRequest()


    data class ListMembers(override val requestId: String) : ControlRequest()
    data class IssueCredential(val request: CredentialActionRequest, override val requestId: String) : ControlRequest()
    data class RevokeCredential(val request: CredentialActionRequest, override val requestId: String) : ControlRequest()
    data class GrantRole(val request: GrantRoleRequest, override val requestId: String) : ControlRequest()
    data class VerifyVc(val request: VcVerifyRequest, override val requestId: String) : ControlRequest()
    data class ResolveDid(val did: String, override val requestId: String) : ControlRequest()


    data class ListNodeScores(override val requestId: String) : ControlRequest()
    data class GetNodeScore(val peerId: String, override val requestId: String) : ControlRequest()


    data class GetOracleScore(val playerId: String, override val requestId: String) : ControlRequest()


    data class RegisterPushEndpoint(val endpoint: String, val devicePubkey: String, override val requestId: String) : ControlRequest()
    data class GetPushConfig(override val requestId: String) : ControlRequest()
    data class InitPushConfig(override val requestId: String) : ControlRequest()
    data class GetPushPreferences(override val requestId: String) : ControlRequest()
    data class UpdatePushPreferences(val enabledEventTypes: Set<String>, val dndEnabled: Boolean, val dndStartHour: Int, val dndEndHour: Int, override val requestId: String) : ControlRequest()
}


 *
sealed class ControlResponse {
    data class ClusterHealth(val data: com.jlucraft.console.data.remote.ClusterHealthResponse) : ControlResponse()
    data class NetworkSnapshot(val data: com.jlucraft.console.data.remote.NetworkSnapshot) : ControlResponse()

    data class InstanceList(val instances: List<Instance>) : ControlResponse()
    data class InstanceDetail(val instance: Instance) : ControlResponse()
    data class UnitSuccess(val message: String = "ok") : ControlResponse()
    data class AdmissionCheck(val allowed: Boolean) : ControlResponse()
    data class InstanceLogs(val logs: List<String>) : ControlResponse()

    data class AuthChallengeResponse(val challenge: AuthChallenge) : ControlResponse()
    data class AuthVerificationResult(val result: AuthResult) : ControlResponse()
    data class CommandResultResponse(val result: CommandResult) : ControlResponse()

    data class ProposalList(val proposals: List<Proposal>) : ControlResponse()
    data class ProposalDetail(val proposal: Proposal) : ControlResponse()

    data class AuditEntryList(val entries: List<AuditEntry>) : ControlResponse()
    data class AuditChainVerification(val data: com.jlucraft.console.data.model.AuditChainVerification) : ControlResponse()
    data class AuditAnomalyList(val anomalies: List<AuditAnomaly>) : ControlResponse()

    data class AlertList(val alerts: List<Alert>) : ControlResponse()
    data class AlertUpdated(val alert: Alert) : ControlResponse()

    data class TournamentList(val tournaments: List<Tournament>) : ControlResponse()
    data class TournamentDetail(val tournament: Tournament) : ControlResponse()
    data class MatchList(val matches: List<Match>) : ControlResponse()
    data class MatchUpdated(val match: Match) : ControlResponse()
    data class TeamList(val teams: List<Team>) : ControlResponse()

    data class DisputeList(val disputes: List<DisputeMatch>) : ControlResponse()
    data class DisputeDetail(val dispute: DisputeMatch) : ControlResponse()

    data class SeasonList(val seasons: List<Season>) : ControlResponse()
    data class SeasonDetail(val season: Season) : ControlResponse()
    data class LeaderboardData(val leaderboard: Leaderboard) : ControlResponse()

    data class DeviceList(val devices: List<Device>) : ControlResponse()
    data class DeviceUpdated(val device: Device) : ControlResponse()

    data class MemberList(val members: List<MemberSummary>) : ControlResponse()
    data class CredentialIssued(val response: IssueCredentialResponse) : ControlResponse()
    data class CredentialRevoked(val response: RevokeCredentialResponse) : ControlResponse()
    data class RoleGranted(val response: GrantRoleResponse) : ControlResponse()
    data class VcVerification(val result: VcVerificationResult) : ControlResponse()
    data class DidResolution(val response: DidServerResponse) : ControlResponse()

    data class NodeScoreList(val scores: List<NodeScore>) : ControlResponse()
    data class NodeScoreDetail(val score: NodeScore) : ControlResponse()

    data class OracleScoreResult(val score: OracleScore) : ControlResponse()

    data class PushConfig(val config: PushConfigResponse) : ControlResponse()
    data class PushPreferences(val preferences: PushPreferencesResponse) : ControlResponse()

    data class SchedulingConstraints(val data: com.jlucraft.console.data.model.SchedulingConstraints) : ControlResponse()
    data class SchedulingResponse(val data: SchedulingConstraintsResponse) : ControlResponse()
    data class SchedulingSimulation(val data: com.jlucraft.console.data.model.SchedulingSimulation) : ControlResponse()

    data class Error(val code: String, val message: String) : ControlResponse()
    data class Unknown(val rawBytes: ByteArray) : ControlResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Unknown) return false
            return rawBytes.contentEquals(other.rawBytes)
        }
        override fun hashCode(): Int = rawBytes.contentHashCode()
    }
}


data class AuthContext(
    val nonce: String,
    val signature: String,
    val challengeId: String,
    val subjectDid: String,
)
