package com.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

internal fun officeTime(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
private fun organizations(values: JSONArray?): String =
    if (values == null || values.length() == 0) stringResource(R.string.office_no_partners)
    else values.strings().joinToString(stringResource(R.string.governance_separator))

@Composable
internal fun EventDetails(event: JSONObject, signups: JSONArray?) {
    Text(stringResource(R.string.office_organizer, event.getString("organizer")))
    event.optJSONArray("co_organizers")?.takeIf { it.length() > 0 }?.let { Text(stringResource(R.string.office_coorganizers, organizations(it))) }
    event.optJSONObject("window")?.let { window ->
        Text(stringResource(R.string.office_opens_at, officeTime(window.getLong("opens_ms"))))
        Text(stringResource(R.string.office_closes_at, officeTime(window.getLong("closes_ms"))))
        Text(stringResource(R.string.office_starts_at, officeTime(window.getLong("starts_ms"))))
    }
    if (event.optString("phase") in setOf("open", "done")) Text(stringResource(R.string.office_signup_count, signups?.length() ?: 0))
}

@Composable
internal fun FlowReview(
    flow: JSONObject,
    payload: JSONObject?,
    target: JSONObject?,
    reviewable: Boolean,
    state: JSONObject? = null,
    device: String = "",
) {
    Text(stringResource(R.string.office_flow_proposer, flow.getString("proposer")))
    val status = flow.getString("status")
    val template = state?.let { officeFlowTemplate(it, flow) }
    val stage = state?.let { officeFlowStage(it, flow) }
    val approver = officeApprover(stage)
    if (stage != null) {
        Text(
            stringResource(R.string.office_approval_stage, flow.optInt("current") + 1, stage.optString("name")),
            style = MaterialTheme.typography.titleSmall,
        )
        if (flow.optString("template").isNotBlank() && flow.optLong("template_epoch") > 0) Text(
            stringResource(R.string.office_approval_template_version, flow.optLong("template_epoch")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ApproverLine(approver)
        if (approver is OfficeApprover.ClubPresidents) Text(
            stringResource(R.string.office_approval_presidents_no_delegation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status == "active" && state != null && device.isNotBlank() && approver != null && approver !is OfficeApprover.Ai) {
            val seat = officeApprovalSeat(state, device, flow)
            if (seat == null) Text(stringResource(R.string.office_approval_not_you))
            else {
                val delegatedBy = seat.delegatedBy
                if (delegatedBy != null) Text(
                    stringResource(
                        R.string.office_approval_delegated,
                        officeMemberId(state, delegatedBy) ?: delegatedBy,
                    ),
                ) else Text(stringResource(R.string.office_approval_you))
                if (seat.approved) Text(stringResource(R.string.office_approval_already_approved))
            }
        }
        val threshold = officeApprovalThreshold(approver)
        if (status == "active" && threshold != null) {
            Text(stringResource(R.string.office_approval_progress, officeApprovalProgress(state, flow), threshold))
        }
    }
    if (payload != null && payload.optString("type") == "events.approve") {
        val requested = payload.optLong("epoch")
        // The current brief must never be presented as the previously approved one.
        if (target == null) {
            Text(stringResource(R.string.office_approval_target_missing), color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(R.string.office_approval_version, requested))
            if (target.optLong("epoch") != requested) {
                Text(
                    stringResource(R.string.office_approval_version_mismatch, requested, target.optLong("epoch")),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (status == "active" && target.optString("phase") != "review") {
                Text(stringResource(R.string.office_approval_phase_mismatch), color = MaterialTheme.colorScheme.error)
            }
            if (target.optLong("epoch") == requested && state != null) {
                OfficeRequestDetails(state, payload.getString("id"), target, false, false, send = { _ -> })
            }
        }
    } else Text(stringResource(R.string.office_flow_other_action))
    if (status == "active" && !reviewable) {
        when {
            payload?.optString("type") != "events.approve" -> Unit
            target == null -> Unit
            (target?.optLong("epoch") ?: -1L) != (payload?.optLong("epoch") ?: -1L) -> Unit
            target?.optString("phase") != "review" -> Unit
            template == null -> Text(stringResource(R.string.office_approval_no_template), color = MaterialTheme.colorScheme.error)
            else -> Text(stringResource(R.string.office_flow_unreviewable), color = MaterialTheme.colorScheme.error)
        }
    }
    if (status == "changes_requested") Text(stringResource(R.string.office_revision_required))
    if (status == "rejected") Text(stringResource(R.string.office_rejected_help))
    if (status == "active" && !flow.isNull("deadline_ms")) Text(stringResource(R.string.office_flow_deadline, officeTime(flow.getLong("deadline_ms"))))
    val records = flow.optJSONArray("records")
    (0 until (records?.length() ?: 0)).forEach { stage ->
        val record = records?.optJSONObject(stage) ?: return@forEach
        val aiRuns = record.optJSONArray("ai_runs")
        (0 until (aiRuns?.length() ?: 0)).forEach { attempt ->
            val run = aiRuns!!.getJSONObject(attempt)
            Text(stringResource(R.string.office_ai_attempt, stage + 1, attempt + 1))
            Text(stringResource(when {
                !run.isNull("output") -> R.string.office_ai_complete
                !run.isNull("error") -> R.string.office_ai_failed
                else -> R.string.office_ai_running
            }))
            run.optJSONObject("review")?.let { review ->
                Text(stringResource(when (review.optString("decision")) {
                    "approve" -> R.string.office_review_approved
                    "changes_requested" -> R.string.office_flow_changes_requested
                    "reject" -> R.string.office_flow_rejected
                    else -> R.string.office_unknown
                }))
                Text(review.getString("summary"))
                review.getJSONArray("issues").strings().forEach { Text(it) }
            }
            if (!run.isNull("output")) IdentifierDetails(run.getString("output"))
        }
        val approvals = record.optJSONObject("approvals")
        approvals?.keys()?.asSequence()?.toList()?.sorted()?.forEach { seat ->
            key(stage, seat, "approval") {
                    val receipt = approvals.getJSONObject(seat)
                Text(stringResource(R.string.office_flow_receipt, stage + 1, officeMemberId(state, receipt.getString("device")) ?: stringResource(R.string.office_reviewer)))
                ReviewComment(state, receipt.getString("comment_hash"))
            }
        }
        record.optJSONObject("rejected")?.let { receipt ->
            Text(stringResource(R.string.office_flow_rejection, stage + 1, officeMemberId(state, receipt.getString("device")) ?: stringResource(R.string.office_reviewer)))
            ReviewComment(state, receipt.getString("comment_hash"))
        }
        val delegations = record.optJSONObject("delegations")
        delegations?.keys()?.asSequence()?.toList()?.sorted()?.forEach { seat ->
            key(stage, seat, "delegation") {
                    val delegation = delegations.getJSONObject(seat)
                Text(stringResource(R.string.office_flow_delegation, stage + 1, delegation.getString("from_device"), delegation.getString("to_member")))
            }
        }
        if (record.optBoolean("escalated")) {
            Text(stringResource(R.string.office_flow_escalated, stage + 1))
        }
        if (record.optBoolean("auto_approved")) {
            Text(stringResource(R.string.office_flow_autoapproved, stage + 1))
        }
    }

}

@Composable
private fun ApproverLine(approver: OfficeApprover?) {
    when (approver) {
        null -> Text(stringResource(R.string.office_approval_unknown_approver))
        OfficeApprover.Ai -> Text(stringResource(R.string.office_approval_approver_ai))
        is OfficeApprover.Member -> Text(stringResource(R.string.office_approval_approver_member, approver.member))
        is OfficeApprover.Role -> Text(
            stringResource(
                R.string.office_approval_approver_role,
                approver.club,
                officeRoleText(approver.role),
            ),
        )
        is OfficeApprover.ClubQuorum -> Text(
            stringResource(
                R.string.office_approval_approver_quorum,
                approver.clubs.joinToString(stringResource(R.string.governance_separator)),
                approver.quorum,
            ),
        )
        is OfficeApprover.ClubPresidents -> Text(
            stringResource(
                R.string.office_approval_approver_presidents,
                approver.clubs.joinToString(stringResource(R.string.governance_separator)),
                approver.clubs.size,
            ),
        )
    }
}

@Composable
private fun officeRoleText(role: String): String = officeRoleLabel(role)?.let { stringResource(it) } ?: role

@Composable
private fun ReviewComment(state: JSONObject?, digest: String) {
    var text by remember(digest) { mutableStateOf<String?>(null) }
    LaunchedEffect(state, digest) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                val files = state?.optJSONObject("files") ?: return@runCatching null
                val branches = files.optJSONObject("branches") ?: return@runCatching null
                branches.keys().asSequence().mapNotNull { branch ->
                    val commit = branches.getJSONObject(branch).getString("commit")
                    val encoded = files.getJSONObject("blocks").getString(commit)
                    val directory = JSONObject(String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP), Charsets.UTF_8)).getJSONObject("files")
                    directory.optJSONObject(digest)?.optString("/")
                }.firstNotNullOfOrNull { cid ->
                    val bytes = UnionNative.readFile(state.toString(), cid)
                    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                    if (actual == digest) String(bytes, Charsets.UTF_8) else null
                }
            }.getOrNull()
        }
    }
    if (text != null) Text(text.orEmpty()) else IdentifierDetails(digest)
}
