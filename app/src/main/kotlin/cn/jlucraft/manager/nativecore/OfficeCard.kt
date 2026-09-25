package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.jlucraft.manager.R
import org.json.JSONObject

@Composable
fun OfficeCard(state: JSONObject?, busy: Boolean, send: (JSONObject) -> Unit) {
    var id by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    var opens by remember { mutableStateOf("") }
    var closes by remember { mutableStateOf("") }
    var starts by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var taskId by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("") }
    var flowId by remember { mutableStateOf("") }
    var templateId by remember { mutableStateOf("") }
    var partners by remember { mutableStateOf("") }
    var delegate by remember { mutableStateOf("") }
    val enabled = state != null && !busy
    val validHash = Regex("[a-f0-9]{64}")
    val validFile = Regex("(?:[a-f0-9]{64}|b[a-z2-7]{58,62})")
    fun act(type: String, key: String, value: String) = send(JSONObject().put("type", type).put(key, value))
    ManagerCard(title = stringResource(R.string.office_title), helper = stringResource(R.string.office_help)) {
        if (state == null) Text(stringResource(R.string.office_connect))
        val events = state?.optJSONObject("events")
        if (state != null && (events == null || events.length() == 0)) Text(stringResource(R.string.office_empty))
        events?.keys()?.asSequence()?.toList()?.sorted()?.forEach { eventId ->
            key(eventId) {
                val event = events.getJSONObject(eventId)
                val phase = event.optString("phase")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(event.optString("title"), style = MaterialTheme.typography.titleMedium)
                    IdentifierDetails(eventId)
                    Text(stringResource(when (phase) {
                        "draft" -> R.string.office_phase_draft
                        "review" -> R.string.office_phase_review
                        "approved" -> R.string.office_phase_approved
                        "open" -> R.string.office_phase_open
                        "done" -> R.string.office_phase_done
                        "cancelled" -> R.string.office_phase_cancelled
                        else -> R.string.office_unknown
                    }))
                    Text(stringResource(R.string.office_epoch, event.optLong("epoch")))
                    IdentifierDetails(event.optString("brief_hash"))
                    EventDetails(event, state.optJSONObject("signups")?.optJSONArray(eventId))
                    if (phase == "draft") OfficeButton(R.string.office_submit, enabled) { act("events.submit", "id", eventId) }
                    if (phase == "approved") OfficeButton(R.string.office_publish, enabled) { act("events.publish", "id", eventId) }
                    if (phase == "open") {
                        OfficeButton(R.string.office_signup, enabled) { act("events.signup", "id", eventId) }
                        OfficeButton(R.string.office_cancel_signup, enabled) { act("events.cancel", "id", eventId) }
                        OfficeButton(R.string.office_finish, enabled) { act("events.finish", "id", eventId) }
                    }
                    if (phase !in setOf("done", "cancelled")) OfficeButton(R.string.office_cancel, enabled) { act("events.cancel_event", "id", eventId) }
                }
            }
        }
        OfficeField(id, R.string.office_id, enabled) { id = it }
        OfficeField(title, R.string.office_name, enabled) { title = it }
        OfficeField(hash, R.string.office_hash, enabled) { hash = it.trim() }
        OfficeField(opens, R.string.office_opens, enabled, R.string.governance_datetime_hint) { opens = it }
        OfficeField(closes, R.string.office_closes, enabled, R.string.governance_datetime_hint) { closes = it }
        OfficeField(starts, R.string.office_starts, enabled, R.string.governance_datetime_hint) { starts = it }
        val openTime = parseLocalDateTime(opens)
        val closeTime = parseLocalDateTime(closes)
        val startTime = parseLocalDateTime(starts)
        OfficeButton(R.string.office_create, enabled && id.isNotBlank() && title.isNotBlank() && validFile.matches(hash) &&
            openTime != null && closeTime != null && startTime != null && openTime >= 0 && openTime < closeTime && closeTime <= startTime) {
            send(JSONObject().put("type", "events.create").put("id", id).put("title", title).put("brief_hash", hash)
                .put("window", JSONObject().put("opens_ms", openTime).put("closes_ms", closeTime).put("starts_ms", startTime)))
        }
        OfficeButton(R.string.office_amend, enabled && events?.has(id) == true && validFile.matches(hash)) {
            send(JSONObject().put("type", "events.amend").put("id", id).put("brief_hash", hash))
        }
        Text(stringResource(R.string.office_tasks), style = MaterialTheme.typography.titleMedium)
        val tasks = state?.optJSONObject("tasks")
        if (state != null && (tasks == null || tasks.length() == 0)) Text(stringResource(R.string.office_no_tasks))
        tasks?.keys()?.asSequence()?.toList()?.sorted()?.forEach { taskId ->
            key(taskId) {
                val task = tasks.getJSONObject(taskId)
                Text(task.optString("title"))
                IdentifierDetails(taskId)
                Text(stringResource(R.string.office_task_event, task.getString("event")))
                Text(if (task.isNull("assignee")) stringResource(R.string.office_unassigned) else stringResource(R.string.office_assigned_to, task.getString("assignee")))
                if (!task.isNull("due_ms")) Text(stringResource(R.string.office_due, officeTime(task.getLong("due_ms"))))
                if (task.optBoolean("done")) Text(stringResource(R.string.office_task_done))
                else OfficeButton(R.string.office_complete, enabled) { act("events.complete", "task", taskId) }
            }
        }
        OfficeField(taskId, R.string.office_task_id, enabled) { taskId = it }
        OfficeField(taskTitle, R.string.office_task_title, enabled) { taskTitle = it }
        OfficeField(assignee, R.string.office_assignee, enabled) { assignee = it }
        OfficeButton(R.string.office_add_task, enabled && events?.has(id) == true && taskId.isNotBlank() && taskTitle.isNotBlank()) {
            send(JSONObject().put("type", "events.add_task").put("event", id).put("id", taskId).put("title", taskTitle))
        }
        OfficeButton(R.string.office_assign, enabled && tasks?.has(taskId) == true && assignee.isNotBlank()) {
            send(JSONObject().put("type", "events.assign").put("task", taskId).put("member", assignee))
        }
        Text(stringResource(R.string.office_flows), style = MaterialTheme.typography.titleMedium)
        OfficeField(comment, R.string.office_comment, enabled) { comment = it.trim() }
        OfficeField(delegate, R.string.office_delegate_member, enabled) { delegate = it }
        val flows = state?.optJSONObject("flows")
        if (state != null && (flows == null || flows.length() == 0)) Text(stringResource(R.string.office_no_flows))
        flows?.keys()?.asSequence()?.toList()?.sorted()?.forEach { flowId ->
            key(flowId) {
                val flow = flows.getJSONObject(flowId)
                val status = flow.optString("status")
                IdentifierDetails(flowId)
                Text(stringResource(when (status) {
                    "active" -> R.string.office_flow_active
                    "approved" -> R.string.office_flow_approved
                    "executed" -> R.string.office_flow_executed
                    "rejected" -> R.string.office_flow_rejected
                    "cancelled" -> R.string.office_flow_cancelled
                    else -> R.string.office_unknown
                }))
                val payload = flow.optJSONObject("payload")
                val target = payload?.optString("id")?.let { events?.optJSONObject(it) }
                val versions = state.optJSONObject("flow_templates")?.optJSONObject(flow.optString("template"))?.optJSONArray("versions")
                val epoch = flow.optLong("template_epoch")
                val fixedTemplate = if (epoch > 0 && epoch <= (versions?.length() ?: 0).toLong()) versions?.optJSONObject((epoch - 1).toInt()) else null
                val reviewable = payload?.optString("type") == "events.approve" && target != null &&
                    target.optLong("epoch") == payload.optLong("epoch") && target.optString("phase") == "review" && fixedTemplate != null
                FlowReview(flow, payload, target, fixedTemplate, reviewable)
                if (status == "active") Text(stringResource(R.string.office_stage, flow.optInt("current") + 1))
                val ai = fixedTemplate?.optJSONArray("stages")?.optJSONObject(flow.optInt("current"))?.optJSONObject("approver")?.optString("type") == "ai"
                if (status == "active" && ai) {
                    Text(stringResource(R.string.office_ai_automatic))
                    val runs = flow.optJSONArray("records")?.optJSONObject(flow.optInt("current"))?.optJSONArray("ai_runs")
                    val last = runs?.optJSONObject(runs.length() - 1)
                    val retryable = last != null && last.isNull("output") && !last.optBoolean("retry_requested") && runs.length() < 8
                    OfficeButton(R.string.office_ai_retry, enabled && retryable) { act("flows.retry_ai", "instance", flowId) }
                    OfficeButton(R.string.office_poke, enabled) { act("flows.poke", "instance", flowId) }
                }
                if (status == "active" && !ai) {
                    OfficeButton(R.string.office_approve, enabled && reviewable && validHash.matches(comment)) {
                        send(JSONObject().put("type", "flows.approve").put("instance", flowId).put("comment_hash", comment))
                    }
                    OfficeButton(R.string.office_reject, enabled && validHash.matches(comment)) {
                        send(JSONObject().put("type", "flows.reject").put("instance", flowId).put("reason", comment))
                    }
                    OfficeButton(R.string.office_delegate, enabled && delegate.isNotBlank()) {
                        send(JSONObject().put("type", "flows.delegate").put("instance", flowId).put("to", delegate))
                    }
                    OfficeButton(R.string.office_poke, enabled) { act("flows.poke", "instance", flowId) }
                }
                if (status == "approved") OfficeButton(R.string.office_execute, enabled && reviewable) { act("flows.execute", "instance", flowId) }
                if (status in setOf("active", "approved")) OfficeButton(R.string.office_cancel_flow, enabled) { act("flows.cancel", "instance", flowId) }
            }
        }
        Text(stringResource(R.string.office_templates), style = MaterialTheme.typography.titleMedium)
        val templates = state?.optJSONObject("flow_templates")
        if (state != null && (templates == null || templates.length() == 0)) Text(stringResource(R.string.office_no_templates))
        templates?.keys()?.asSequence()?.toList()?.sorted()?.forEach { template ->
            key(template) {
                val versions = templates.getJSONObject(template)
                if (versions.optBoolean("active")) {
                    val history = versions.optJSONArray("versions")
                    history?.optJSONObject(history.length() - 1)?.let { FlowTemplatePreview(it) }
                    Button(onClick = { templateId = template }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.office_use_template, template))
                    }
                }
            }
        }
        OfficeField(templateId, R.string.office_template, enabled) { templateId = it }
        OfficeField(flowId, R.string.office_flow_id, enabled) { flowId = it }
        OfficeField(partners, R.string.office_partners, enabled) { partners = it }
        val event = events?.optJSONObject(id)
        OfficeButton(R.string.office_start_flow, enabled && event?.optString("phase") == "review" && templateId.isNotBlank() && flowId.isNotBlank()) {
            send(JSONObject().put("type", "flows.start").put("id", flowId).put("template", templateId)
                .put("input", event?.optString("brief_hash")?.takeIf { it.startsWith("b") } ?: JSONObject.NULL)
                .put("payload", JSONObject().put("type", "events.approve").put("id", id).put("epoch", event?.optLong("epoch"))
                    .put("co_organizers", org.json.JSONArray(partners.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()))))
        }
    }
}
@Composable
private fun OfficeField(value: String, label: Int, enabled: Boolean, hint: Int? = null, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(stringResource(label)) },
        enabled = enabled, singleLine = true, supportingText = hint?.let { { Text(stringResource(it)) } },
        modifier = Modifier.fillMaxWidth())
}
@Composable
private fun OfficeButton(label: Int, enabled: Boolean, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(label)) }
}
