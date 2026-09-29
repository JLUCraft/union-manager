package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R
import org.json.JSONObject

@Composable
fun OfficeCard(state: JSONObject?, busy: Boolean, device: String, approvals: Boolean = false, send: (JSONObject) -> Unit) {
    var id by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    var opens by rememberSaveable { mutableStateOf("") }
    var closes by rememberSaveable { mutableStateOf("") }
    var starts by rememberSaveable { mutableStateOf("") }
    var expandedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedFlow by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val enabled = state != null && !busy
    val member = officeMemberId(state, device)
    fun act(type: String, key: String, value: String) = send(JSONObject().put("type", type).put(key, value))
    ManagerCard(title = stringResource(if (approvals) R.string.office_flows else R.string.office_title)) {
        if (state == null) Text(stringResource(R.string.office_connect))
        val events = state?.optJSONObject("events")
        LaunchedEffect(state) {
            if (creating && events?.has(id) == true) {
                expandedEvent = id
                creating = false
                id = java.util.UUID.randomUUID().toString()
                title = ""; description = ""; hash = ""; opens = ""; closes = ""; starts = ""
            }
        }
        if (!approvals) {
        if (state != null && (events == null || events.length() == 0)) Text(stringResource(R.string.office_empty))
        events?.keys()?.asSequence()?.toList()?.sorted()?.forEachIndexed { index, eventId ->
            key(eventId) {
                val event = events.getJSONObject(eventId)
                val phase = event.optString("phase")
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { expandedEvent = if (expandedEvent == eventId) null else eventId }, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)) {
                            Text(event.optString("title"), style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(text = stringResource(when (phase) {
                            "draft" -> R.string.office_phase_draft
                            "review" -> R.string.office_phase_review
                            "approved" -> R.string.office_phase_approved
                            "open" -> R.string.office_phase_open
                            "done" -> R.string.office_phase_done
                            "cancelled" -> R.string.office_phase_cancelled
                            else -> R.string.office_unknown
                        }), active = phase in setOf("approved", "open"))
                    }
                    if (expandedEvent == eventId) {
                    val own = state.optJSONObject("members")?.optJSONObject(member.orEmpty())
                    val admin = state.optJSONArray("admins")?.let { values -> (0 until values.length()).any { values.optString(it) == member } } == true
                    val manage = admin || event.optString("creator") == member ||
                        (own?.optString("club") == event.optString("organizer") && own.optJSONArray("roles")?.let { values -> (0 until values.length()).any { values.optString(it) == "president" } } == true)
                    OfficeRequestDetails(state, eventId, event, busy, manage && phase !in setOf("done", "cancelled"), send)
                    EventDetails(event, state.optJSONObject("signups")?.optJSONArray(eventId))
                    if (manage && phase == "draft") OfficeButton(R.string.office_submit, enabled) { act("events.submit", "id", eventId) }
                    if (manage && phase == "review") {
                        val flows = state.optJSONObject("flows")
                        val awaiting = flows?.keys()?.asSequence()?.any { flowId ->
                            val flow = flows.getJSONObject(flowId)
                            val payload = flow.optJSONObject("payload")
                            payload?.optString("type") == "events.approve" && payload.optString("id") == eventId &&
                                payload.optLong("epoch") == event.optLong("epoch") && flow.optString("status") in setOf("active", "approved", "changes_requested")
                        } == true
                        if (!awaiting) {
                            val templates = state.optJSONObject("flow_templates") ?: JSONObject()
                            val available = templates.keys().asSequence().filter { templateId ->
                                val template = templates.getJSONObject(templateId)
                                val versions = template.optJSONArray("versions")
                                val kinds = versions?.optJSONObject(versions.length() - 1)?.optJSONArray("payload_kinds")
                                template.optBoolean("active") && kinds?.let { values -> (0 until values.length()).any { values.optString(it) == "events.approve" } } == true
                            }.toList()
                            var menu by remember { mutableStateOf(false) }
                            OutlinedButton(onClick = { menu = true }, enabled = enabled && available.isNotEmpty()) { Text(stringResource(R.string.office_request_approval)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                available.forEach { templateId ->
                                    val versions = templates.getJSONObject(templateId).getJSONArray("versions")
                                    DropdownMenuItem(text = { Text(versions.getJSONObject(versions.length()-1).optString("name")) }, onClick = {
                                        menu = false
                                        send(JSONObject().put("type", "flows.start").put("id", java.util.UUID.randomUUID().toString())
                                            .put("template", templateId).put("input", event.optString("brief_hash"))
                                            .put("payload", JSONObject().put("type", "events.approve").put("id", eventId).put("epoch", event.getLong("epoch"))
                                                .put("co_organizers", event.optJSONArray("co_organizers") ?: org.json.JSONArray())))
                                    })
                                }
                            }
                            if (available.isEmpty()) Text(stringResource(R.string.office_no_approval_template))
                        }
                    }
                    if (manage && phase == "approved") OfficeButton(R.string.office_publish, enabled) { act("events.publish", "id", eventId) }
                    if (phase == "open") {
                        val signedUp = state.optJSONObject("signups")?.optJSONArray(eventId)?.let { entries ->
                            (0 until entries.length()).any { entries.optString(it) == member }
                        } == true
                        OfficeButton(if (signedUp) R.string.office_cancel_signup else R.string.office_signup, enabled && member != null, if (signedUp) OfficeAction.Secondary else OfficeAction.Primary) {
                            act(if (signedUp) "events.cancel" else "events.signup", "id", eventId)
                        }
                        if (manage) OfficeButton(R.string.office_finish, enabled, OfficeAction.Secondary) { act("events.finish", "id", eventId) }
                    }
                    if (manage && phase !in setOf("done", "cancelled")) OfficeButton(R.string.office_cancel, enabled, OfficeAction.Destructive) { act("events.cancel_event", "id", eventId) }
                    }
                }
            }
        }
        TextButton(onClick = { creating = !creating }, enabled = enabled) { Text(stringResource(R.string.office_create)) }
        if (creating) {
        OfficeField(title, R.string.office_name, enabled) { title = it }
        OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.office_description)) }, enabled = enabled, minLines = 4, modifier = Modifier.fillMaxWidth())
        OfficeAttachment(state, busy, id, hash, { hash = it }, send)
        DateTimeField(opens, stringResource(R.string.office_opens), enabled) { opens = it }
        DateTimeField(closes, stringResource(R.string.office_closes), enabled) { closes = it }
        DateTimeField(starts, stringResource(R.string.office_starts), enabled) { starts = it }
        val openTime = parseLocalDateTime(opens)
        val closeTime = parseLocalDateTime(closes)
        val startTime = parseLocalDateTime(starts)
        OfficeButton(R.string.office_save_request, enabled && id.isNotBlank() && title.isNotBlank() && description.isNotBlank() &&
            openTime != null && closeTime != null && startTime != null && openTime >= 0 && openTime < closeTime && closeTime <= startTime) {
            send(JSONObject().put("type", "local.events.create").put("id", id).put("title", title).put("description", description).put("attachment", hash.ifBlank { null } ?: JSONObject.NULL)
                .put("window", JSONObject().put("opens_ms", openTime).put("closes_ms", closeTime).put("starts_ms", startTime)))
        }
        }
        } else {
        val flows = state?.optJSONObject("flows")
        if (state != null && (flows == null || flows.length() == 0)) Text(stringResource(R.string.office_no_flows))
        flows?.keys()?.asSequence()?.toList()?.sorted()?.forEachIndexed { index, flowId ->
            key(flowId) {
                Column(Modifier.testTag("flow:$flowId"), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val flow = flows.getJSONObject(flowId)
                val status = flow.optString("status")
                val payload = flow.optJSONObject("payload")
                val target = payload?.optString("id")?.let { events?.optJSONObject(it) }
                TextButton(onClick = { expandedFlow = if (expandedFlow == flowId) null else flowId }, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
                    Text(target?.optString("title")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.office_flow_other_action), style = MaterialTheme.typography.titleMedium)
                }
                StatusPill(text = stringResource(when (status) {
                    "active" -> R.string.office_flow_active
                    "approved" -> R.string.office_flow_approved
                    "executed" -> R.string.office_flow_executed
                    "rejected" -> R.string.office_flow_rejected
                    "changes_requested" -> R.string.office_flow_changes_requested
                    "cancelled" -> R.string.office_flow_cancelled
                    else -> R.string.office_unknown
                }), active = status in setOf("active", "approved"))
                if (expandedFlow == flowId) {
                val versions = state.optJSONObject("flow_templates")?.optJSONObject(flow.optString("template"))?.optJSONArray("versions")
                val epoch = flow.optLong("template_epoch")
                val fixedTemplate = flow.optJSONObject("workflow") ?: if (epoch > 0 && epoch <= (versions?.length() ?: 0).toLong()) versions?.optJSONObject((epoch - 1).toInt()) else null
                val reviewable = payload?.optString("type") == "events.approve" && target != null &&
                    target.optLong("epoch") == payload.optLong("epoch") && target.optString("phase") == "review" && fixedTemplate != null
                FlowReview(flow, payload, target, reviewable, state)
                if (status == "active") Text(stringResource(R.string.office_stage, flow.optInt("current") + 1))
                val ai = fixedTemplate?.optJSONArray("stages")?.optJSONObject(flow.optInt("current"))?.optJSONObject("approver")?.optString("type") == "ai"
                if (status == "active" && ai) {
                    Text(stringResource(R.string.office_ai_automatic))
                    val runs = flow.optJSONArray("records")?.optJSONObject(flow.optInt("current"))?.optJSONArray("ai_runs")
                    val last = runs?.optJSONObject(runs.length() - 1)
                    val retryable = last != null && !last.isNull("error") && last.isNull("output") && !last.optBoolean("retry_requested") && runs.length() < 8
                    OfficeButton(R.string.office_ai_retry, enabled && retryable, OfficeAction.Secondary) { act("flows.retry_ai", "instance", flowId) }
                    OfficeButton(R.string.office_poke, enabled, OfficeAction.Secondary) { act("flows.poke", "instance", flowId) }
                }
                if (status == "active" && !ai) {
                    var comment by rememberSaveable { mutableStateOf("") }
                    var delegate by rememberSaveable { mutableStateOf("") }
                    OfficeField(comment, R.string.office_comment, enabled) { comment = it.trim() }
                    OfficeButton(R.string.office_approve, enabled && reviewable && comment.isNotBlank()) {
                        send(JSONObject().put("type", "local.flows.approve").put("instance", flowId).put("comment", comment))
                    }
                    OfficeButton(R.string.office_reject, enabled && comment.isNotBlank(), OfficeAction.Destructive) {
                        send(JSONObject().put("type", "local.flows.reject").put("instance", flowId).put("comment", comment))
                    }
                    var delegating by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { delegating = !delegating }) { Text(stringResource(R.string.office_delegate)) }
                    if (delegating) {
                    OfficeField(delegate, R.string.office_delegate_member, enabled) { delegate = it }
                    OfficeButton(R.string.office_delegate, enabled && delegate.isNotBlank()) {
                        send(JSONObject().put("type", "flows.delegate").put("instance", flowId).put("to", delegate))
                    }
                    }
                }
                if (status == "approved") OfficeButton(R.string.office_execute, enabled && reviewable) { act("flows.execute", "instance", flowId) }
                if (status in setOf("active", "approved")) OfficeButton(R.string.office_cancel_flow, enabled, OfficeAction.Destructive) { act("flows.cancel", "instance", flowId) }
                }
                }
            }
        }
        }
    }
}
@Composable
private fun OfficeField(value: String, label: Int, enabled: Boolean, hint: Int? = null, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(stringResource(label)) },
        enabled = enabled, singleLine = true, supportingText = hint?.let { { Text(stringResource(it)) } },
        modifier = Modifier.fillMaxWidth())
}
internal enum class OfficeAction { Primary, Secondary, Destructive }

@Composable
private fun OfficeButton(label: Int, enabled: Boolean, kind: OfficeAction = OfficeAction.Primary, click: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
    val onClick = if (kind == OfficeAction.Destructive) ({ confirming = true }) else click
    when (kind) {
        OfficeAction.Primary -> Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(stringResource(label)) }
        OfficeAction.Secondary -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(stringResource(label)) }
        OfficeAction.Destructive -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else DISABLED_BORDER_ALPHA)),
        ) { Text(stringResource(label)) }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(label)) },
        text = { Text(stringResource(R.string.confirm_irreversible)) },
        confirmButton = {
            TextButton(onClick = { confirming = false; click() }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(label)) }
        },
        dismissButton = {
            TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.confirm_dismiss)) }
        },
    )
}
