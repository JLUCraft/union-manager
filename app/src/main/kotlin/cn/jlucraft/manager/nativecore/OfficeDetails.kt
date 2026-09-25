package cn.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import cn.jlucraft.manager.R
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
    Text(stringResource(R.string.office_coorganizers, organizations(event.optJSONArray("co_organizers"))))
    event.optJSONObject("window")?.let { window ->
        Text(stringResource(R.string.office_opens_at, officeTime(window.getLong("opens_ms"))))
        Text(stringResource(R.string.office_closes_at, officeTime(window.getLong("closes_ms"))))
        Text(stringResource(R.string.office_starts_at, officeTime(window.getLong("starts_ms"))))
    }
    Text(stringResource(R.string.office_signup_count, signups?.length() ?: 0))
}

@Composable
internal fun FlowReview(flow: JSONObject, payload: JSONObject?, target: JSONObject?, template: JSONObject?, reviewable: Boolean) {
    Text(stringResource(R.string.office_flow_proposer, flow.getString("proposer")))
    Text(stringResource(R.string.office_flow_template_version, flow.getString("template"), flow.getLong("template_epoch")))
    if (payload?.optString("type") == "events.approve") {
        Text(stringResource(R.string.office_flow_target, payload.getString("id")))
        Text(stringResource(R.string.office_flow_requested_epoch, payload.getLong("epoch")))
        Text(stringResource(R.string.office_flow_requested_partners, organizations(payload.optJSONArray("co_organizers"))))
        // The current brief must never be presented as the previously approved one.
        if (target != null && target.optLong("epoch") == payload.getLong("epoch")) {
            Text(target.getString("title"), style = MaterialTheme.typography.titleSmall)
            Text(target.getString("brief_hash"), style = MaterialTheme.typography.bodySmall)
        }
    } else Text(stringResource(R.string.office_flow_other_action))
    val status = flow.getString("status")
    if (status in setOf("active", "approved") && !reviewable) {
        Text(stringResource(R.string.office_flow_unreviewable), color = MaterialTheme.colorScheme.error)
    }
    template?.let { FlowTemplatePreview(it) }
    if (status == "active") {
        Text(if (flow.isNull("deadline_ms")) stringResource(R.string.office_flow_no_deadline)
            else stringResource(R.string.office_flow_deadline, officeTime(flow.getLong("deadline_ms"))))
    }
    Text(stringResource(R.string.office_flow_receipts), style = MaterialTheme.typography.titleSmall)
    val records = flow.optJSONArray("records")
    var hasReceipts = false
    (0 until (records?.length() ?: 0)).forEach { stage ->
        val record = records?.optJSONObject(stage) ?: return@forEach
        val aiRuns = record.optJSONArray("ai_runs")
        (0 until (aiRuns?.length() ?: 0)).forEach { attempt ->
            val run = aiRuns!!.getJSONObject(attempt)
            hasReceipts = true
            Text(stringResource(R.string.office_ai_attempt, stage + 1, attempt + 1))
            Text(run.optString("job"), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(when {
                !run.isNull("output") -> R.string.office_ai_complete
                !run.isNull("error") -> R.string.office_ai_failed
                else -> R.string.office_ai_running
            }))
            if (!run.isNull("output")) Text(run.getString("output"), style = MaterialTheme.typography.bodySmall)
        }
        val approvals = record.optJSONObject("approvals")
        approvals?.keys()?.asSequence()?.toList()?.sorted()?.forEach { seat ->
            key(stage, seat, "approval") {
                hasReceipts = true
                val receipt = approvals.getJSONObject(seat)
                Text(stringResource(R.string.office_flow_receipt, stage + 1, receipt.getString("device")))
                Text(stringResource(R.string.office_flow_comment_hash, receipt.getString("comment_hash")))
            }
        }
        record.optJSONObject("rejected")?.let { receipt ->
            hasReceipts = true
            Text(stringResource(R.string.office_flow_rejection, stage + 1, receipt.getString("device")))
            Text(stringResource(R.string.office_flow_comment_hash, receipt.getString("comment_hash")))
        }
        val delegations = record.optJSONObject("delegations")
        delegations?.keys()?.asSequence()?.toList()?.sorted()?.forEach { seat ->
            key(stage, seat, "delegation") {
                hasReceipts = true
                val delegation = delegations.getJSONObject(seat)
                Text(stringResource(R.string.office_flow_delegation, stage + 1, delegation.getString("from_device"), delegation.getString("to_member")))
            }
        }
        if (record.optBoolean("escalated")) {
            hasReceipts = true
            Text(stringResource(R.string.office_flow_escalated, stage + 1))
        }
        if (record.optBoolean("auto_approved")) {
            hasReceipts = true
            Text(stringResource(R.string.office_flow_autoapproved, stage + 1))
        }
    }
    if (!hasReceipts) Text(stringResource(R.string.office_flow_no_receipts))
}
