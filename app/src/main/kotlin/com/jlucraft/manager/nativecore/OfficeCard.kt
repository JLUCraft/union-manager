package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONObject

/**
 * The workflow start command holds a client-side point ceiling. Points are an internal
 * settlement mechanism, so the manager neither quotes nor caps a reservation; the server prices
 * the run and refuses any debit the account cannot cover.
 */
internal const val OFFICE_FLOW_MAX_POINTS = Long.MAX_VALUE

/** List order: soonest signup window first; events without a window go last, ties broken by id so the order is stable. */
internal fun sortedEventIds(entries: List<Pair<String, Long?>>): List<String> =
    entries.sortedWith(compareBy({ it.second ?: Long.MAX_VALUE }, { it.first })).map { it.first }

/** The requests page is the drafting desk; formal activities stay reachable for member signup. */
internal enum class OfficeEventFilter(val label: Int) {
    Todo(R.string.office_filter_event_todo),
    Public(R.string.office_filter_event_public),
    All(R.string.office_filter_event_all),
}

internal fun officeEventMatchesFilter(event: JSONObject, actor: OfficeActor, filter: OfficeEventFilter): Boolean = when (filter) {
    OfficeEventFilter.Todo -> event.optString("phase") in setOf("draft", "review") && actor.canManageEvent(event)
    OfficeEventFilter.Public -> event.optString("phase") in setOf("approved", "open")
    OfficeEventFilter.All -> true
}

/** The approvals page defaults to actual work items and keeps history behind an explicit filter. */
internal enum class OfficeFlowFilter(val label: Int) {
    Todo(R.string.office_filter_flow_todo),
    Related(R.string.office_filter_flow_related),
}

/**
 * Summary list only. Titles open the dedicated detail pages; no create form and no inline editor
 * is ever rendered here.
 */
@Composable
fun OfficeCard(
    state: JSONObject?,
    busy: Boolean,
    device: String,
    approvals: Boolean = false,
    showTitle: Boolean = true,
    onOpenEvent: ((String) -> Unit)? = null,
    onOpenFlow: ((String) -> Unit)? = null,
) {
    val actor = officeActor(state, device)
    val enabled = state != null && !busy
    ManagerCard(title = if (showTitle) stringResource(if (approvals) R.string.office_flows else R.string.office_title) else null) {
        if (state == null) {
            Text(stringResource(R.string.office_connect))
            return@ManagerCard
        }
        val events = state.optJSONObject("events") ?: JSONObject()
        if (!approvals) {
            var eventFilter by rememberSaveable(actor.member, actor.canManage) {
                mutableStateOf(if (actor.canManage) OfficeEventFilter.Todo else OfficeEventFilter.Public)
            }
            if (events.length() == 0) Text(stringResource(R.string.office_empty)) else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                    OfficeEventFilter.entries.forEach { filter ->
                        key(filter) {
                            FilterChip(
                                selected = eventFilter == filter,
                                onClick = { eventFilter = filter },
                                enabled = enabled,
                                label = { Text(stringResource(filter.label)) },
                                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                            )
                        }
                    }
                }
                val orderedEvents = officeEventIds(state, actor, eventFilter)
                if (orderedEvents.isEmpty()) Text(stringResource(R.string.office_no_filtered_events))
                orderedEvents.forEachIndexed { index, eventId ->
                    key(eventId) {
                        val event = events.getJSONObject(eventId)
                        val phase = event.optString("phase")
                        if (index > 0) HorizontalDivider()
                        EventSummaryRow(
                            title = event.optString("title").ifBlank { eventId },
                            phase = phase,
                            enabled = enabled,
                            tag = "event:$eventId",
                            onOpen = onOpenEvent?.let { open -> { open(eventId) } },
                        )
                    }
                }
            }
        } else {
            var flowFilter by rememberSaveable { mutableStateOf(OfficeFlowFilter.Todo) }
            val flows = state.optJSONObject("flows") ?: JSONObject()
            if (flows.length() == 0) Text(stringResource(R.string.office_no_flows)) else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                    OfficeFlowFilter.entries.forEach { filter ->
                        key(filter) {
                            FilterChip(
                                selected = flowFilter == filter,
                                onClick = { flowFilter = filter },
                                enabled = enabled,
                                label = { Text(stringResource(filter.label)) },
                                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                            )
                        }
                    }
                }
                val relatedFlows = flows.keys().asSequence().filter { flowId ->
                    flows.optJSONObject(flowId)?.let { officeFlowVisible(state, device, it) } == true
                }.toList()
                val visibleFlows = relatedFlows.filter { flowId ->
                    val flow = flows.getJSONObject(flowId)
                    flowFilter == OfficeFlowFilter.Related || officeFlowNeedsAttention(state, device, flow)
                }.sortedWith(compareByDescending<String> { officeFlowNeedsAttention(state, device, flows.getJSONObject(it)) }.thenBy { it })
                if (relatedFlows.isEmpty()) Text(stringResource(R.string.office_no_related_approvals))
                else if (visibleFlows.isEmpty()) Text(stringResource(R.string.office_no_pending_approvals))
                visibleFlows.forEachIndexed { index, flowId ->
                    key(flowId) {
                        if (index > 0) HorizontalDivider()
                        val flow = flows.getJSONObject(flowId)
                        val payload = flow.optJSONObject("payload")
                        val target = payload?.optString("id")?.let { events.optJSONObject(it) }
                        FlowSummaryRow(
                            title = target?.optString("title")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.office_flow_other_action),
                            status = flow.optString("status"),
                            enabled = enabled,
                            tag = "flow:$flowId",
                            onOpen = onOpenFlow?.let { open -> { open(flowId) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSummaryRow(
    title: String,
    phase: String,
    enabled: Boolean,
    tag: String,
    onOpen: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        if (onOpen != null) TextButton(
            onClick = onOpen,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        } else Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        StatusPill(
            text = stringResource(
                when (phase) {
                    "draft" -> R.string.office_phase_draft
                    "review" -> R.string.office_phase_review
                    "approved" -> R.string.office_phase_approved
                    "open" -> R.string.office_phase_open
                    "done" -> R.string.office_phase_done
                    "cancelled" -> R.string.office_phase_cancelled
                    else -> R.string.office_unknown
                },
            ),
            active = phase in setOf("approved", "open"),
        )
    }
}

@Composable
private fun FlowSummaryRow(
    title: String,
    status: String,
    enabled: Boolean,
    tag: String,
    onOpen: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        if (onOpen != null) TextButton(
            onClick = onOpen,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        } else Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        StatusPill(
            text = stringResource(
                when (status) {
                    "active" -> R.string.office_flow_active
                    "approved" -> R.string.office_flow_approved
                    "executed" -> R.string.office_flow_executed
                    "rejected" -> R.string.office_flow_rejected
                    "changes_requested" -> R.string.office_flow_changes_requested
                    "cancelled" -> R.string.office_flow_cancelled
                    else -> R.string.office_unknown
                },
            ),
            active = status in setOf("active", "approved"),
        )
    }
}

/** One office event with its management actions; editing has its own page. */
@Composable
internal fun OfficeEventDetail(
    state: JSONObject,
    eventId: String,
    event: JSONObject,
    busy: Boolean,
    actor: OfficeActor,
    enabled: Boolean,
    send: (JSONObject) -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    fun act(type: String, key: String, value: String) = send(JSONObject().put("type", type).put(key, value))
    val phase = event.optString("phase")
    val manage = actor.canManageEvent(event)
    val member = actor.member.takeIf { actor.bound }
    var pendingTemplateId by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        OfficeRequestDetails(state, eventId, event, busy, editable = false, send = send)
        EventDetails(event, state.optJSONObject("signups")?.optJSONArray(eventId))
        Text(stringResource(R.string.office_event_version, event.optLong("epoch")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (phase == "draft") Text(
            stringResource(R.string.office_draft_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (manage && phase == "draft") {
            if (onEdit != null) ActionButton(R.string.office_event_edit, enabled, kind = ActionKind.Secondary) { onEdit() }
            ActionButton(R.string.office_submit, enabled) { act("events.submit", "id", eventId) }
        }
        if (manage && phase == "review") {
            val flows = state.optJSONObject("flows") ?: JSONObject()
            val awaiting = flows.keys().asSequence().any { flowId ->
                val flow = flows.getJSONObject(flowId)
                val payload = flow.optJSONObject("payload")
                payload != null && payload.optString("type") == "events.approve" && payload.optString("id") == eventId &&
                    payload.optLong("epoch") == event.optLong("epoch") && flow.optString("status") in setOf("active", "changes_requested")
            }
            val approved = flows.keys().asSequence().any { flowId ->
                val flow = flows.getJSONObject(flowId)
                val payload = flow.optJSONObject("payload")
                payload != null && payload.optString("type") == "events.approve" && payload.optString("id") == eventId &&
                    payload.optLong("epoch") == event.optLong("epoch") && flow.optString("status") == "approved"
            }
            val rejected = flows.keys().asSequence().any { flowId ->
                val flow = flows.getJSONObject(flowId)
                val payload = flow.optJSONObject("payload")
                payload != null && payload.optString("type") == "events.approve" && payload.optString("id") == eventId &&
                    payload.optLong("epoch") == event.optLong("epoch") && flow.optString("status") == "rejected"
            }
            if (approved) Text(stringResource(R.string.office_approval_approved_pending))
            else if (awaiting) Text(stringResource(R.string.office_approval_in_progress))
            if (rejected && !awaiting && !approved) Text(stringResource(R.string.office_rejected_help))
            if (!awaiting && !approved && !rejected) {
                val plan = state.optJSONObject("organization")?.optJSONObject("plans")?.optJSONObject(eventId)
                if (plan == null) {
                    val eligible = actor.bound && actor.club == event.optString("organizer")
                    Text(
                        stringResource(R.string.office_start_presidents_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ActionButton(R.string.office_start_presidents, enabled && eligible) {
                        send(
                            JSONObject().put("type", "flows.start_presidents").put("id", java.util.UUID.randomUUID().toString())
                                .put(
                                    "payload",
                                    JSONObject().put("type", "events.approve").put("id", eventId).put("epoch", event.getLong("epoch"))
                                        .put("co_organizers", event.optJSONArray("co_organizers") ?: org.json.JSONArray()),
                                ),
                        )
                    }
                    if (!eligible) Text(
                        stringResource(R.string.office_start_presidents_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val templates = state.optJSONObject("flow_templates") ?: JSONObject()
                    val available = templates.keys().asSequence().filter { templateId ->
                        val template = templates.getJSONObject(templateId)
                        val versions = template.optJSONArray("versions")
                        val latest = versions?.optJSONObject(versions.length() - 1)
                        template.optBoolean("active") && latest?.optJSONArray("payload_kinds").containsString("events.approve") == true &&
                            latest != null && officeTemplateReviewsAutomatically(latest)
                    }.toList()
                    val input = if (plan.isNull("review_input")) null else plan.optString("review_input").takeIf { it.isNotBlank() }
                    val coOrganizers = org.json.JSONArray()
                    plan.optJSONObject("schools")?.keys()?.asSequence()?.filter { it != event.optString("organizer") }
                        ?.forEach { coOrganizers.put(it) }
                    var menu by rememberSaveable { mutableStateOf(false) }
                    Text(
                        stringResource(R.string.office_plan_approval_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { menu = true }, enabled = enabled && available.isNotEmpty() && input != null) {
                        Text(stringResource(R.string.office_request_approval))
                    }
                    if (available.isEmpty()) Text(stringResource(R.string.office_no_approval_template))
                    else if (input == null) Text(
                        stringResource(R.string.office_no_review_input),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        available.forEach { templateId ->
                            val versions = templates.getJSONObject(templateId).getJSONArray("versions")
                            DropdownMenuItem(text = { Text(versions.getJSONObject(versions.length() - 1).optString("name")) }, onClick = {
                                menu = false
                                pendingTemplateId = templateId
                            })
                        }
                    }
                    val pendingId = pendingTemplateId.takeIf { it.isNotBlank() }
                    if (pendingId != null) {
                        AlertDialog(
                            onDismissRequest = { pendingTemplateId = "" },
                            title = { Text(stringResource(R.string.office_flow_start_confirm_title)) },
                            text = {
                                Text(
                                    stringResource(R.string.office_flow_start_confirm_plain),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = enabled && input != null,
                                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                                    onClick = {
                                        if (input == null) return@TextButton
                                        pendingTemplateId = ""
                                        send(
                                            JSONObject().put("type", "flows.start").put("id", java.util.UUID.randomUUID().toString())
                                                .put("template", pendingId)
                                                .put("max_points", OFFICE_FLOW_MAX_POINTS)
                                                .put("input", input)
                                                .put("payload", JSONObject().put("type", "events.approve").put("id", eventId).put("epoch", event.getLong("epoch"))
                                                    .put("co_organizers", coOrganizers)),
                                        )
                                    },
                                ) { Text(stringResource(R.string.office_flow_start_confirm_action)) }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { pendingTemplateId = "" },
                                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                                ) { Text(stringResource(R.string.confirm_dismiss)) }
                            },
                        )
                    }
                }
            }
        }
        if (manage && phase == "approved") ActionButton(R.string.office_publish, enabled) { act("events.publish", "id", eventId) }
        // Participant and organizer duties are separate groups: a member's own signup never shares a
        // card with the organizer's terminal actions, and every irreversible action confirms first.
        if (phase == "open") {
            val signedUp = state.optJSONObject("signups")?.optJSONArray(eventId).containsString(member)
            ManagerCard(
                title = stringResource(R.string.office_participant_actions),
                emphasized = false,
            ) {
                ActionButton(
                    label = if (signedUp) R.string.office_cancel_signup else R.string.office_signup,
                    enabled = enabled && member != null,
                    kind = if (signedUp) ActionKind.Destructive else ActionKind.Primary,
                    confirmText = R.string.office_cancel_signup_confirm,
                ) {
                    act(if (signedUp) "events.cancel" else "events.signup", "id", eventId)
                }
                if (member == null) Text(
                    stringResource(R.string.office_signup_unbound),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (manage && phase !in setOf("done", "cancelled")) ManagerCard(
            title = stringResource(R.string.office_organizer_actions),
            helper = stringResource(R.string.office_organizer_actions_help),
            emphasized = false,
        ) {
            if (phase == "open") ActionButton(
                label = R.string.office_finish,
                enabled = enabled,
                kind = ActionKind.Confirmed,
                confirmText = R.string.office_finish_confirm,
            ) { act("events.finish", "id", eventId) }
            ActionButton(
                label = R.string.office_cancel,
                enabled = enabled,
                kind = ActionKind.Destructive,
                confirmText = R.string.office_cancel_event_confirm,
            ) { act("events.cancel_event", "id", eventId) }
        }
    }
}

/** Event destination body: resolves the event from [state] and renders [OfficeEventDetail]. */
@Composable
internal fun OfficeEventDetailScreen(
    state: JSONObject?,
    busy: Boolean,
    device: String,
    eventId: String,
    onEdit: (() -> Unit)? = null,
    send: (JSONObject) -> Unit,
) {
    val event = state?.optJSONObject("events")?.optJSONObject(eventId)
    if (state == null || event == null) {
        Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val actor = officeActor(state, device)
    OfficeEventDetail(state, eventId, event, busy, actor, !busy, send, onEdit)
}

/** Dedicated edit page; the detail page never embeds this editor. */
@Composable
internal fun OfficeEventEditScreen(
    state: JSONObject?,
    busy: Boolean,
    device: String,
    eventId: String,
    onBack: () -> Unit,
    send: FormSender,
) {
    val event = state?.optJSONObject("events")?.optJSONObject(eventId)
    if (state == null || event == null) {
        TaskPage(stringResource(R.string.office_event_edit), busy, onBack) {
            Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // Losing the manager seat or leaving the draft phase removes the editor immediately.
    val editable = officeActor(state, device).canManageEvent(event) && event.optString("phase") == "draft"
    val bottomAction = rememberBottomActionSlot()
    TaskPage(
        title = stringResource(R.string.office_event_edit),
        busy = busy,
        onBack = onBack,
        bottomBar = { bottomAction.action?.let { action -> BottomActionBar { action() } } },
    ) {
        if (!editable) {
            Text(stringResource(R.string.office_edit_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OfficeRequestDetails(state, eventId, event, busy, editable = false, send = { action -> send.send(action) })
        } else {
            OfficeRequestDetails(
                state = state,
                id = eventId,
                event = event,
                busy = busy,
                editable = true,
                send = { action -> send.send(action) },
                onSaved = onBack,
                submit = send,
                bottomAction = bottomAction,
            )
        }
    }
}

/** One workflow instance with its review actions; the opinion form has its own decision page. */
@Composable
internal fun OfficeFlowDetail(
    state: JSONObject,
    flowId: String,
    flow: JSONObject,
    busy: Boolean,
    enabled: Boolean,
    device: String,
    send: (JSONObject) -> Unit,
    onDecision: (() -> Unit)? = null,
) {
    fun act(type: String, key: String, value: String) = send(JSONObject().put("type", type).put(key, value))
    val status = flow.optString("status")
    val payload = flow.optJSONObject("payload")
    val target = payload?.optString("id")?.let { state.optJSONObject("events")?.optJSONObject(it) }
    val template = officeFlowTemplate(state, flow)
    val stage = officeFlowStage(state, flow)
    val approver = officeApprover(stage)
    val ai = approver is OfficeApprover.Ai
    val actor = officeActor(state, device)
    val proposer = actor.admin || (actor.bound && actor.member == flow.optString("proposer"))
    val canExecute = officeCanExecuteFlow(state, device, flow)
    val currentTarget = officeFlowCurrentReviewTarget(state, flow) != null
    val reviewable = currentTarget && template != null
    val seat = if (status == "active" && !ai) officeApprovalSeat(state, device, flow) else null
    Column(Modifier.testTag("flow:$flowId:detail"), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        FlowReview(flow, payload, target, reviewable, state, device)
        if (status == "active" && ai) {
            Text(stringResource(R.string.office_ai_automatic))
            val runs = flow.optJSONArray("records")?.optJSONObject(flow.optInt("current"))?.optJSONArray("ai_runs")
            val last = runs?.optJSONObject(runs.length() - 1)
            val retryable = last != null && !last.isNull("error") && last.isNull("output") && !last.optBoolean("retry_requested") && runs.length() < 8
            ActionButton(R.string.office_ai_retry, enabled && retryable && proposer, kind = ActionKind.Secondary) { act("flows.retry_ai", "instance", flowId) }
            ActionButton(R.string.office_poke, enabled && actor.bound, kind = ActionKind.Secondary) { act("flows.poke", "instance", flowId) }
        }
        if (status == "active" && !ai && seat != null) {
            if (onDecision != null) {
                ActionButton(R.string.office_approval_decision, enabled && reviewable && seat.canApprove) { onDecision() }
            } else {
                OfficeFlowDecisionContent(state, flowId, flow, busy, enabled, device, send)
            }
        }
        if (status == "approved") ActionButton(R.string.office_execute, enabled && currentTarget && template != null && canExecute) {
            act("flows.execute", "instance", flowId)
        }
        if (status == "approved" && !canExecute) Text(
            stringResource(R.string.office_execute_restricted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status in setOf("active", "approved") && proposer) {
            ActionButton(R.string.office_cancel_flow, enabled, kind = ActionKind.Destructive) { act("flows.cancel", "instance", flowId) }
        }
    }
}

/** The opinion and signature task, kept off the detail page so one page has one purpose. */
@Composable
private fun OfficeFlowDecisionContent(
    state: JSONObject,
    flowId: String,
    flow: JSONObject,
    busy: Boolean,
    enabled: Boolean,
    device: String,
    send: (JSONObject) -> Unit,
) {
    fun act(type: String, key: String, value: String) = send(JSONObject().put("type", type).put(key, value))
    val stage = officeFlowStage(state, flow)
    val approver = officeApprover(stage)
    val template = officeFlowTemplate(state, flow)
    val reviewable = officeFlowCurrentReviewTarget(state, flow) != null && template != null
    val seat = officeApprovalSeat(state, device, flow) ?: return
    val actor = officeActor(state, device)
    var comment by rememberSaveable { mutableStateOf("") }
    var delegate by rememberSaveable { mutableStateOf("") }
    OfficeField(comment, R.string.office_comment, enabled) { comment = it.trim() }
    if (comment.isBlank()) Text(
        stringResource(R.string.office_comment_required),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActionButton(R.string.office_approve, enabled && reviewable && seat.canApprove && comment.isNotBlank()) {
        send(JSONObject().put("type", "local.flows.approve").put("instance", flowId).put("comment", comment))
    }
    ActionButton(
        R.string.office_return_for_changes,
        enabled && reviewable && !seat.approved && comment.isNotBlank(),
        kind = ActionKind.Destructive,
        confirmText = R.string.office_return_confirm,
    ) {
        send(JSONObject().put("type", "local.flows.reject").put("instance", flowId).put("comment", comment))
    }
    if (!seat.approved && approver !is OfficeApprover.ClubPresidents) {
        var delegating by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { delegating = !delegating }) { Text(stringResource(R.string.office_delegate)) }
        if (delegating) {
            OfficeField(delegate, R.string.office_delegate_member, enabled) { delegate = it }
            Text(
                stringResource(R.string.office_delegate_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionButton(R.string.office_delegate, enabled && delegate.isNotBlank() && delegate != actor.member) {
                send(JSONObject().put("type", "flows.delegate").put("instance", flowId).put("to", delegate))
            }
        }
    }
}

/** Flow destination body: resolves the flow from [state] and renders [OfficeFlowDetail]. */
@Composable
internal fun OfficeFlowDetailScreen(
    state: JSONObject?,
    busy: Boolean,
    device: String,
    flowId: String,
    onDecision: (() -> Unit)? = null,
    send: (JSONObject) -> Unit,
) {
    val flow = state?.optJSONObject("flows")?.optJSONObject(flowId)
    if (state == null || flow == null) {
        Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OfficeFlowDetail(state, flowId, flow, busy, !busy, device, send, onDecision)
}

/** Dedicated approval-opinion page: context, comment and the signature confirmation. */
@Composable
internal fun OfficeFlowDecisionScreen(
    state: JSONObject?,
    busy: Boolean,
    device: String,
    flowId: String,
    onBack: () -> Unit,
    send: (JSONObject) -> Unit,
) {
    val flow = state?.optJSONObject("flows")?.optJSONObject(flowId)
    if (state == null || flow == null) {
        TaskPage(stringResource(R.string.office_approval_decision), busy, onBack) {
            Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val payload = flow.optJSONObject("payload")
    val target = payload?.optString("id")?.let { state.optJSONObject("events")?.optJSONObject(it) }
    // Signing a review whose draft content is missing would approve a document nobody can read, so
    // the page records that and blocks the signature until the draft is repaired.
    var draftAvailable by remember(flowId) { mutableStateOf(true) }
    TaskPage(stringResource(R.string.office_approval_decision), busy, onBack) {
        if (target != null) {
            ManagerCard(
                title = target.optString("title").ifBlank { payload?.optString("id").orEmpty() },
                helper = stringResource(R.string.office_approval_target_help),
                emphasized = false,
            ) {
                Text(stringResource(R.string.office_organizer, target.optString("organizer")))
                Text(
                    stringResource(R.string.office_event_version, target.optLong("epoch")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OfficeRequestDetails(
                    state = state,
                    id = target.optString("id"),
                    event = target,
                    busy = busy,
                    editable = false,
                    send = send,
                    onDocumentState = { draftAvailable = it },
                )
            }
        }
        FlowReview(flow, payload, target, officeFlowCurrentReviewTarget(state, flow) != null, state, device)
        if (!draftAvailable) Text(
            stringResource(R.string.office_signing_blocked_missing_draft),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OfficeFlowDecisionContent(
            state = state,
            flowId = flowId,
            flow = flow,
            busy = busy,
            enabled = !busy && draftAvailable,
            device = device,
            send = send,
        )
    }
}

@Composable
private fun OfficeField(value: String, label: Int, enabled: Boolean, hint: Int? = null, change: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = change, label = { Text(stringResource(label)) },
        enabled = enabled, singleLine = true, supportingText = hint?.let { { Text(stringResource(it)) } },
        modifier = Modifier.fillMaxWidth())
}
