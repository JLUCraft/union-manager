package com.jlucraft.manager.nativecore

import org.json.JSONArray
import org.json.JSONObject

/** The approver of one workflow stage, decoded from the template snapshot the flow was started with. */
internal sealed interface OfficeApprover {
    data object Ai : OfficeApprover
    data class Member(val member: String) : OfficeApprover
    data class Role(val club: String, val role: String) : OfficeApprover
    data class ClubQuorum(val clubs: Set<String>, val quorum: Int) : OfficeApprover
    data class ClubPresidents(val clubs: Set<String>) : OfficeApprover
}

/** The exact template version a flow runs, preferring the snapshot stored on the flow. */
internal fun officeFlowTemplate(state: JSONObject?, flow: JSONObject): JSONObject? {
    flow.optJSONObject("workflow")?.let { return it }
    val versions = state?.optJSONObject("flow_templates")?.optJSONObject(flow.optString("template"))
        ?.optJSONArray("versions") ?: return null
    val epoch = flow.optLong("template_epoch")
    if (epoch <= 0 || epoch > versions.length()) return null
    return versions.optJSONObject((epoch - 1).toInt())
}

internal fun officeFlowStage(state: JSONObject?, flow: JSONObject): JSONObject? {
    val stages = officeFlowTemplate(state, flow)?.optJSONArray("stages") ?: return null
    val current = flow.optInt("current")
    if (current < 0 || current >= stages.length()) return null
    return stages.optJSONObject(current)
}

internal fun officeApprover(stage: JSONObject?): OfficeApprover? {
    val approver = stage?.optJSONObject("approver") ?: return null
    return when (approver.optString("type")) {
        "ai" -> OfficeApprover.Ai
        "member" -> officeApproverMember(approver)
        "role" -> OfficeApprover.Role(approver.optString("club"), approver.optString("role"))
        "club_quorum" -> OfficeApprover.ClubQuorum(
            clubs = approver.optJSONArray("clubs").stringSet(),
            quorum = approver.optInt("quorum"),
        )
        "club_presidents" -> OfficeApprover.ClubPresidents(approver.optJSONArray("clubs").stringSet())
        else -> null
    }
}

private fun officeApproverMember(approver: JSONObject): OfficeApprover.Member? =
    approver.optString("member").takeIf { it.isNotBlank() }?.let(OfficeApprover::Member)

private fun JSONArray?.stringSet(): Set<String> {
    if (this == null) return emptySet()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }.toSet()
}

/** The slot the server would assign to [member], or null when the member does not hold the seat. */
private fun officeSeat(approver: OfficeApprover, member: OfficeMemberProfile): String? = when (approver) {
    OfficeApprover.Ai -> null
    is OfficeApprover.Member -> "member".takeIf { approver.member == member.id }
    is OfficeApprover.Role -> "role".takeIf { member.club == approver.club && approver.role in member.roles }
    is OfficeApprover.ClubQuorum -> member.club.takeIf { it in approver.clubs }
    is OfficeApprover.ClubPresidents -> member.club.takeIf { it in approver.clubs && member.president }
}

private data class OfficeAuthorizedSeat(val slot: String, val delegatedBy: String?)

/**
 * Mirrors the server's seat authorization against the current snapshot: a same-club delegation wins,
 * otherwise the device holds its own seat unless that slot was delegated away.
 */
private fun officeAuthorizedSeat(
    state: JSONObject?,
    approver: OfficeApprover,
    record: JSONObject?,
    device: String,
): OfficeAuthorizedSeat? {
    val member = officeMemberProfile(state, device) ?: return null
    val delegations = record?.optJSONObject("delegations")
    if (approver !is OfficeApprover.ClubPresidents && delegations != null) {
        for (key in delegations.keys().asSequence()) {
            val delegation = delegations.optJSONObject(key) ?: continue
            if (delegation.optString("to_member") != member.id) continue
            val from = officeMemberProfile(state, delegation.optString("from_device")) ?: continue
            if (from.club != member.club || officeSeat(approver, from) != key) continue
            return OfficeAuthorizedSeat(key, delegation.optString("from_device").takeIf { it.isNotBlank() })
        }
    }
    val slot = officeSeat(approver, member) ?: return null
    if (approver !is OfficeApprover.ClubPresidents && delegations?.has(slot) == true) return null
    return OfficeAuthorizedSeat(slot, null)
}

private fun officeReceiptIsValid(
    state: JSONObject?,
    approver: OfficeApprover,
    record: JSONObject?,
    slot: String,
): Boolean {
    val receipt = record?.optJSONObject("approvals")?.optJSONObject(slot) ?: return false
    val device = receipt.optString("device")
    return device.isNotBlank() && officeAuthorizedSeat(state, approver, record, device)?.slot == slot
}

/** Signatures needed to complete the stage; AI stages have no human count. */
internal fun officeApprovalThreshold(approver: OfficeApprover?): Int? = when (approver) {
    null, OfficeApprover.Ai -> null
    is OfficeApprover.ClubQuorum -> approver.quorum
    is OfficeApprover.ClubPresidents -> approver.clubs.size
    is OfficeApprover.Member, is OfficeApprover.Role -> 1
}

/** Approval clubs of a management action, as the server derives them from the event or its office plan. */
internal fun officeFlowApprovalClubs(state: JSONObject?, flow: JSONObject): Set<String> {
    val payload = flow.optJSONObject("payload") ?: return emptySet()
    if (payload.optString("type") != "events.approve") return emptySet()
    val id = payload.optString("id")
    val event = state?.optJSONObject("events")?.optJSONObject(id) ?: return emptySet()
    val plan = state.optJSONObject("organization")?.optJSONObject("plans")?.optJSONObject(id)
    if (plan != null) return plan.optJSONObject("schools")?.keys()?.asSequence()?.toSet().orEmpty()
    return buildSet {
        event.optString("organizer").takeIf { it.isNotBlank() }?.let(::add)
        addAll(payload.optJSONArray("co_organizers").stringSet())
    }
}

/** The event a flow still reviews, or null when the payload targets a missing, outdated, or settled version. */
internal fun officeFlowCurrentReviewTarget(state: JSONObject?, flow: JSONObject): JSONObject? {
    val payload = flow.optJSONObject("payload") ?: return null
    if (payload.optString("type") != "events.approve") return null
    val id = payload.optString("id")
    if (id.isBlank()) return null
    val target = state?.optJSONObject("events")?.optJSONObject(id) ?: return null
    if (target.optLong("epoch") != payload.optLong("epoch") || target.optString("phase") != "review") return null
    return target
}

private fun officeFlowApprovesEvent(flow: JSONObject): Boolean =
    flow.optJSONObject("payload")?.optString("type") == "events.approve"

/** Human receipts that still satisfy the current stage under the published membership. */
internal fun officeApprovalProgress(state: JSONObject?, flow: JSONObject): Int {
    val approver = officeApprover(officeFlowStage(state, flow)) ?: return 0
    if (approver is OfficeApprover.Ai) return 0
    val record = flow.optJSONArray("records")?.optJSONObject(flow.optInt("current")) ?: return 0
    val approvals = record.optJSONObject("approvals") ?: return 0
    return approvals.keys().asSequence().count { slot -> officeReceiptIsValid(state, approver, record, slot) }
}

internal fun officeCanExecuteFlow(state: JSONObject?, device: String, flow: JSONObject): Boolean {
    if (flow.optString("status") != "approved") return false
    if (officeFlowApprovesEvent(flow) && officeFlowCurrentReviewTarget(state, flow) == null) return false
    val actor = officeActor(state, device)
    if (actor.admin || (actor.bound && actor.member == flow.optString("proposer"))) return true
    return actor.bound && actor.president && actor.club in officeFlowApprovalClubs(state, flow)
}

/** Flows the device may inspect: its own submissions, flows of events it manages, and flows with a seat for it. */
internal fun officeFlowVisible(state: JSONObject?, device: String, flow: JSONObject): Boolean {
    val actor = officeActor(state, device)
    val payload = flow.optJSONObject("payload")
    val target = payload?.optString("id")?.let { state?.optJSONObject("events")?.optJSONObject(it) }
    return actor.admin || (actor.bound && flow.optString("proposer") == actor.member) ||
        (target != null && actor.canManageEvent(target)) ||
        (flow.optString("status") == "active" && officeApprovalSeat(state, device, flow) != null)
}

/** True when the flow currently asks this device to sign, execute, or revise something. */
internal fun officeFlowNeedsAttention(state: JSONObject?, device: String, flow: JSONObject): Boolean {
    if (officeFlowApprovesEvent(flow) && officeFlowCurrentReviewTarget(state, flow) == null) return false
    val actor = officeActor(state, device)
    val status = flow.optString("status")
    if (status == "active") {
        val seat = officeApprovalSeat(state, device, flow)
        if (seat != null && !seat.approved) return true
    }
    if (officeCanExecuteFlow(state, device, flow)) return true
    val proposer = actor.bound && flow.optString("proposer") == actor.member
    if (status == "active" && (actor.admin || proposer)) return true
    if (status in setOf("changes_requested", "rejected") && (actor.admin || proposer || officeFlowManagesTarget(state, actor, flow))) {
        return true
    }
    return false
}

private fun officeFlowManagesTarget(state: JSONObject?, actor: OfficeActor, flow: JSONObject): Boolean {
    val id = flow.optJSONObject("payload")?.optString("id") ?: return false
    val target = state?.optJSONObject("events")?.optJSONObject(id) ?: return false
    return actor.canManageEvent(target)
}

/** Whether a template version contains the automatic material review stage required by an office plan. */
internal fun officeTemplateReviewsAutomatically(template: JSONObject): Boolean {
    val stages = template.optJSONArray("stages") ?: return false
    return (0 until stages.length()).any { index ->
        val approver = stages.optJSONObject(index)?.optJSONObject("approver") ?: return@any false
        approver.optString("type") == "ai" && approver.optString("mode") == "review"
    }
}

/** The current stage seat for this device, including seats received through a same-club delegation. */
internal data class OfficeApprovalSeat(
    val slot: String,
    val delegatedBy: String?,
    val approved: Boolean,
) {
    val canApprove: Boolean get() = !approved
}

internal fun officeApprovalSeat(state: JSONObject?, device: String, flow: JSONObject): OfficeApprovalSeat? {
    val approver = officeApprover(officeFlowStage(state, flow)) ?: return null
    val record = flow.optJSONArray("records")?.optJSONObject(flow.optInt("current"))
    val seat = officeAuthorizedSeat(state, approver, record, device) ?: return null
    return OfficeApprovalSeat(seat.slot, seat.delegatedBy, officeReceiptIsValid(state, approver, record, seat.slot))
}
