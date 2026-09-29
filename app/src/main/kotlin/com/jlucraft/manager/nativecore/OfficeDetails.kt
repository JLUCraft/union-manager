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
    else (0 until values.length()).joinToString(stringResource(R.string.governance_separator)) { values.getString(it) }

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
internal fun FlowReview(flow: JSONObject, payload: JSONObject?, target: JSONObject?, reviewable: Boolean, state: JSONObject? = null) {
    Text(stringResource(R.string.office_flow_proposer, flow.getString("proposer")))
    if (payload?.optString("type") == "events.approve") {
        // The current brief must never be presented as the previously approved one.
        if (target != null && target.optLong("epoch") == payload.getLong("epoch")) {
            if (state != null) OfficeRequestDetails(state, payload.getString("id"), target, false, false) {}
        }
    } else Text(stringResource(R.string.office_flow_other_action))
    val status = flow.getString("status")
    if (status in setOf("active", "approved") && !reviewable) {
        Text(stringResource(R.string.office_flow_unreviewable), color = MaterialTheme.colorScheme.error)
    }
    if (status == "changes_requested") Text(stringResource(R.string.office_revision_required))
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
                val issues = review.getJSONArray("issues")
                (0 until issues.length()).forEach { issue -> Text(issues.getString(issue)) }
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
                    val bytes = UnionNative.readOfficeFile(state.toString(), cid)
                    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                    if (actual == digest) String(bytes, Charsets.UTF_8) else null
                }
            }.getOrNull()
        }
    }
    if (text != null) Text(text.orEmpty()) else IdentifierDetails(digest)
}
