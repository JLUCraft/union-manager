package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.sortedKeys() = keys().asSequence().toList().sorted()
private fun Iterable<String>.json() = JSONArray(toList())

@Composable
private fun workflowField(label: Int, enabled: Boolean, initial: String = "", hint: Int? = null): String {
    var value by remember { mutableStateOf(initial) }
    OutlinedTextField(value, { value = it }, label = { Text(stringResource(label)) }, enabled = enabled,
        supportingText = hint?.let { { Text(stringResource(it)) } },
        modifier = Modifier.fillMaxWidth())
    return value.trim()
}

@Composable
private fun workflowDateField(label: Int, enabled: Boolean): String {
    var value by remember { mutableStateOf("") }
    DateTimeField(value, stringResource(label), enabled) { value = it }
    return value.trim()
}

@Composable
private fun WorkflowChoice(label: String, selected: Boolean, enabled: Boolean, change: () -> Unit) {
    FilterChip(selected = selected, onClick = change, enabled = enabled,
        label = { Text(label) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET))
}

@Composable
private fun WorkflowAction(label: Int, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
        Text(stringResource(label))
    }
}

/** Stateless multi-select chips; the caller hoists the selection so it survives step switches. */
@Composable
private fun WorkflowChips(items: JSONObject, selection: Set<String>, enabled: Boolean, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        items.sortedKeys().forEach { id ->
            key(id) {
                WorkflowChoice(items.optJSONObject(id)?.optString("title")?.takeIf { it.isNotBlank() } ?: id,
                    id in selection, enabled) { onToggle(id) }
            }
        }
    }
}

/** Numbered step heading with a plain-language explanation of what to do in this step. */
@Composable
private fun StepHeader(step: Int, title: Int, help: Int? = null) {
    Text(stringResource(R.string.workflow_step_label, step, stringResource(title)), style = MaterialTheme.typography.labelLarge)
    if (help != null) Text(
        stringResource(help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepReason(reason: Int?) {
    if (reason != null) Text(
        stringResource(reason),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One event's preparation task. Only the current plan step or the current review step is rendered;
 * reports and workflow configuration live behind their own entries on the workflow page.
 */
@Composable
internal fun OfficeWorkflowEventPage(
    state: JSONObject?,
    kind: String,
    eventId: String,
    busy: Boolean,
    identity: String,
    onOpenCompetition: (String) -> Unit,
    send: FormSender,
    onBack: () -> Unit,
) {
    val event = state?.optJSONObject("events")?.optJSONObject(eventId)
    if (state == null || event == null) {
        TaskPage(stringResource(R.string.office_workflow_event_title), busy, onBack) {
            Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val office = state.optJSONObject("organization") ?: JSONObject()
    val members = state.optJSONObject("members") ?: JSONObject()
    val actor = officeActor(state, identity)
    val automation = office.optJSONObject("automation")
    val worker = automation?.optString("worker").orEmpty()
    val profiles = office.optJSONObject("schools") ?: JSONObject()
    val plans = office.optJSONObject("plans") ?: JSONObject()
    val resources = office.optJSONObject("resources") ?: JSONObject()
    val leagues = state.optJSONObject("leagues") ?: JSONObject()
    val plan = plans.optJSONObject(eventId)
    val enabled = !busy
    val bottomAction = rememberBottomActionSlot()
    // The wizard's position lives on the page so an expanded window can show it in the step outline.
    // The draft form and the review form keep separate values, both keyed like the forms they drive.
    val mayEditPlan = actor.canManageEvent(event)
    var editingPlan by rememberSaveable(eventId) { mutableStateOf(false) }
    var planRevision by rememberSaveable(eventId) { mutableStateOf(0) }
    var draftStep by rememberSaveable(eventId, planFormKey(plan)) { mutableStateOf(0) }
    var reviewStep by rememberSaveable(eventId, planFormKey(plan, planRevision)) { mutableStateOf(0) }
    // Re-check the seat on every recomposition: keeping the editor open is not a permission.
    LaunchedEffect(mayEditPlan) { if (!mayEditPlan) editingPlan = false }
    val wizardStep: Int? = when {
        event.optString("phase") != "draft" -> null
        plan == null -> if (mayEditPlan) 2 + draftStep else null
        !plan.isNull("final_epoch") -> null
        editingPlan && mayEditPlan -> 2 + draftStep
        else -> 4 + reviewStep
    }
    TaskPage(
        title = event.optString("title").ifBlank { stringResource(R.string.office_workflow_event_title) },
        busy = busy,
        onBack = onBack,
        steps = if (wizardStep != null) WORKFLOW_STEPS else null,
        currentStep = wizardStep ?: 0,
        bottomBar = { bottomAction.action?.let { action -> BottomActionBar { action() } } },
    ) {
        if (plan != null) {
            Text(
                stringResource(if (plan.optString("kind") == "meeting") R.string.workflow_meeting else R.string.workflow_online),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (plan == null && event.optString("phase") == "draft") {
            if (actor.canManageEvent(event)) PlanDraftSteps(
                kind, event, profiles, members, resources, worker, enabled, eventId, null, send,
                bottomAction = bottomAction, step = draftStep, onStepChange = { draftStep = it },
            )
            else Text(stringResource(R.string.workflow_need_manager))
        } else if (plan != null) {
            if (event.optString("phase") == "draft" && plan.isNull("final_epoch")) {
                if (editingPlan && mayEditPlan) {
                    PlanDraftSteps(
                        plan.optString("kind").takeIf { it.isNotBlank() } ?: kind,
                        event, profiles, members, resources, worker, enabled, eventId, plan,
                        send, onSaved = { planRevision += 1; editingPlan = false }, bottomAction = bottomAction,
                        step = draftStep, onStepChange = { draftStep = it },
                    )
                    TextButton(onClick = { editingPlan = false }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                } else {
                    PlanReviewSteps(kind = plan.optString("kind").takeIf { it.isNotBlank() } ?: kind, state = state, event = event, plan = plan,
                        resources = resources, busy = busy, enabled = enabled, eventId = eventId, actor = actor,
                        planRevision = planRevision, send = { action -> send.send(action) }, onEditPlan = { editingPlan = true },
                        bottomAction = bottomAction, step = reviewStep, onStepChange = { reviewStep = it })
                }
            }
            if (!plan.isNull("final_epoch") && event.optString("phase") != "done") Text(stringResource(R.string.workflow_automatic))
            listOf("polished" to R.string.workflow_polished, "summary" to R.string.workflow_summary).forEach { (field, label) ->
                if (!plan.isNull(field)) {
                    Text(stringResource(label))
                    IdentifierDetails(plan.getString(field))
                }
            }
            plan.optJSONObject("games")?.let { games ->
                games.sortedKeys().forEach { id ->
                    val game = games.getJSONObject(id)
                    key(id) {
                        Text(resources.optJSONObject(id)?.optString("title") ?: id)
                        Text(stringResource(if (!game.isNull("results")) R.string.workflow_game_finished else if (game.optBoolean("started")) R.string.workflow_game_started else R.string.workflow_game_waiting))
                        val leagueId = game.optString("league")
                        val leagueReady = leagueId.isNotBlank() && leagues.has(leagueId)
                        WorkflowAction(R.string.workflow_open_league, enabled && leagueReady) {
                            if (leagueReady) onOpenCompetition(leagueId)
                        }
                        if (!game.isNull("error")) Text(stringResource(R.string.workflow_game_failed), color = MaterialTheme.colorScheme.error)
                        game.optJSONObject("results")?.let { scores ->
                            scores.sortedKeys().forEach { member ->
                                Text(stringResource(R.string.workflow_score, member, scores.getLong(member)))
                            }
                        }
                    }
                }
            }
            plan.optJSONObject("awards")?.let { awards ->
                awards.sortedKeys().forEach { member ->
                    key(member) {
                        val award = awards.getJSONObject(member)
                        Text(stringResource(R.string.workflow_award, member, award.optLong("points"), if (award.isNull("material")) "" else award.optString("material")))
                        if (!award.isNull("material")) {
                            WorkflowAction(R.string.workflow_deliver, enabled && award.isNull("delivered_by")) {
                                send.send(JSONObject().put("type", "organization.deliver").put("event", eventId).put("member", member))
                            }
                            WorkflowAction(R.string.workflow_receive, enabled && member == actor.member && !award.isNull("delivered_by") && award.isNull("received_ms")) {
                                send.send(JSONObject().put("type", "organization.receive").put("event", eventId))
                            }
                        }
                    }
                }
            }
        }
        if (event.optString("phase") == "open") {
            WorkflowAction(R.string.workflow_checkin, enabled) { send.send(JSONObject().put("type", "events.checkin").put("id", eventId)) }
        }
        val tasks = state.optJSONObject("tasks") ?: JSONObject()
        tasks.sortedKeys().filter { tasks.getJSONObject(it).optString("event") == eventId }.forEach { task ->
            key(task) {
                Text(tasks.getJSONObject(task).optString("title"))
                val notice = office.optJSONObject("notices")?.optJSONObject(task)
                val due = parseLocalDateTime(workflowDateField(R.string.workflow_task_due, enabled))
                WorkflowAction(R.string.workflow_notify, enabled && due != null) {
                    send.send(JSONObject().put("type", "organization.notify").put("task", task).put("due_ms", due))
                }
                if (notice != null) {
                    WorkflowAction(R.string.workflow_acknowledge, enabled && notice.optString("member") == actor.member && notice.isNull("acknowledged_ms")) {
                        send.send(JSONObject().put("type", "organization.acknowledge").put("notice", task))
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

/** Steps 2-3 of the workflow wizard: who participates, then when and with which games; ends by saving the plan. */
@Composable
private fun PlanDraftSteps(
    kind: String,
    event: JSONObject,
    profiles: JSONObject,
    members: JSONObject,
    resources: JSONObject,
    worker: String,
    enabled: Boolean,
    eventId: String,
    existing: JSONObject?,
    send: FormSender,
    onSaved: (() -> Unit)? = null,
    bottomAction: BottomActionSlot? = null,
    step: Int,
    onStepChange: (Int) -> Unit,
) {
    val submission = rememberFormSubmission()
    // Only the editable plan fields reset the form; later opinions and rewards must not overwrite in-progress input.
    val planKey = planFormKey(existing)
    fun initialParticipants(): Set<String> = buildSet {
        val schools = existing?.optJSONObject("schools") ?: return@buildSet
        schools.keys().asSequence().forEach { school ->
            schools.optJSONObject(school)?.optJSONArray("participants")?.strings()?.forEach(::add)
        }
    }
    var schools by remember(eventId, planKey) {
        mutableStateOf(existing?.optJSONObject("schools")?.keys()?.asSequence()?.toSet().orEmpty())
    }
    var participants by remember(eventId, planKey) { mutableStateOf(initialParticipants()) }
    var candidates by remember(eventId, planKey) {
        mutableStateOf(existing?.optJSONArray("candidates")?.strings()?.toSet().orEmpty())
    }
    var opensText by rememberSaveable(eventId, planKey) {
        mutableStateOf(existing?.takeIf { it.has("checkin_opens_ms") }?.optLong("checkin_opens_ms")?.let(::formatLocalDateTime).orEmpty())
    }
    var closesText by rememberSaveable(eventId, planKey) {
        mutableStateOf(existing?.takeIf { it.has("checkin_closes_ms") }?.optLong("checkin_closes_ms")?.let(::formatLocalDateTime).orEmpty())
    }
    var endsText by rememberSaveable(eventId, planKey) {
        mutableStateOf(existing?.takeIf { it.has("ends_ms") }?.optLong("ends_ms")?.let(::formatLocalDateTime).orEmpty())
    }
    val participating = JSONObject().also { map ->
        members.sortedKeys().filter { members.getJSONObject(it).optString("club") in schools }
            .forEach { map.put(it, members.getJSONObject(it)) }
    }
    val effectiveParticipants = participants.intersect(participating.sortedKeys().toSet())
    if (existing != null) Text(
        stringResource(R.string.workflow_edit_plan_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    if (step == 0) {
        StepHeader(2, R.string.workflow_step_schools, R.string.workflow_step_schools_help)
        Text(stringResource(R.string.workflow_schools))
        WorkflowChips(profiles, schools, enabled) { toggled -> schools = if (toggled in schools) schools - toggled else schools + toggled }
        if (schools.isNotEmpty()) {
            Text(stringResource(R.string.workflow_participants))
            WorkflowChips(participating, effectiveParticipants, enabled) { toggled ->
                participants = if (toggled in participants) participants - toggled else participants + toggled
            }
        }
        val schoolsReady = schools.isNotEmpty() && event.optString("organizer") in schools
        // The forward action and its reason are pinned: a long school/participant list must never
        // push the advance action below the fold.
        bottomAction?.set {
            StepReason(when {
                schools.isEmpty() -> R.string.workflow_next_need_schools
                !schoolsReady -> R.string.workflow_next_need_organizer
                else -> null
            })
            WorkflowAction(R.string.workflow_next, enabled && schoolsReady) { onStepChange(1) }
        }
    } else {
        StepHeader(3, R.string.workflow_step_schedule, R.string.workflow_step_schedule_help)
        DateTimeField(opensText, stringResource(R.string.workflow_checkin_opens), enabled) { opensText = it }
        DateTimeField(closesText, stringResource(R.string.workflow_checkin_closes), enabled) { closesText = it }
        DateTimeField(endsText, stringResource(R.string.workflow_ends), enabled) { endsText = it }
        if (kind == "online") {
            Text(stringResource(R.string.workflow_candidates))
            WorkflowChips(resources, candidates, enabled) { toggled -> candidates = if (toggled in candidates) candidates - toggled else candidates + toggled }
        }
        val opens = parseLocalDateTime(opensText)
        val closes = parseLocalDateTime(closesText)
        val ends = parseLocalDateTime(endsText)
        val eventStart = event.optJSONObject("window")?.optLong("starts_ms")
        val timesValid = opens != null && closes != null && ends != null && opens < closes &&
            (eventStart == null || (closes <= eventStart && eventStart < ends))
        Text(
            stringResource(R.string.workflow_plan_summary, schools.size, effectiveParticipants.size, if (kind == "online") candidates.size else 0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val planReady = worker.isNotBlank() && timesValid && (kind != "online" || candidates.isNotEmpty())
        // This step has three date-time fields, so its save action is pinned like the others.
        bottomAction?.set {
            OutlinedButton(
                onClick = { onStepChange(0) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(R.string.workflow_back)) }
            StepReason(when {
                worker.isBlank() -> R.string.workflow_need_worker
                !timesValid -> R.string.workflow_next_need_schedule
                kind == "online" && candidates.isEmpty() -> R.string.workflow_next_need_candidates
                else -> null
            })
            WorkflowAction(R.string.workflow_plan, enabled && !submission.pending && planReady) {
                val selectedSchools = JSONObject()
                schools.forEach { school ->
                    selectedSchools.put(school, JSONObject().put("kind", profiles.getJSONObject(school).getString("kind"))
                        .put("participants", effectiveParticipants.filter { members.getJSONObject(it).optString("club") == school }.json()))
                }
                val request = JSONObject().put("type", "organization.plan").put("event", eventId).put("kind", kind).put("schools", selectedSchools)
                    .put("candidates", candidates.json()).put("checkin_opens_ms", opens).put("checkin_closes_ms", closes).put("ends_ms", ends).put("worker", worker)
                // The page closes only when the accepted snapshot holds the plan for this event.
                submission.submit(send, request) { snapshot ->
                    if (snapshot.optJSONObject("organization")?.optJSONObject("plans")?.has(eventId) == true) {
                        onSaved?.invoke()
                        true
                    } else false
                }
            }
        }
        submission.failure?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Steps 4-5 of the workflow wizard: upload the material once (the number is picked up automatically), then opinion, consent, rewards and finalize. */
@Composable
private fun PlanReviewSteps(
    kind: String,
    state: JSONObject,
    event: JSONObject,
    plan: JSONObject,
    resources: JSONObject,
    busy: Boolean,
    enabled: Boolean,
    eventId: String,
    actor: OfficeActor,
    planRevision: Int,
    send: (JSONObject) -> Unit,
    onEditPlan: () -> Unit,
    bottomAction: BottomActionSlot? = null,
    step: Int,
    onStepChange: (Int) -> Unit,
) {
    // A rewritten plan invalidates uploaded opinions and consents; never keep stale material in the form.
    val planKey = planFormKey(plan, planRevision)
    var document by rememberSaveable(eventId, planKey) { mutableStateOf("") }
    var selected by remember(eventId, planKey) { mutableStateOf(emptySet<String>()) }
    var pointsText by rememberSaveable(eventId, planKey) { mutableStateOf("") }
    var material by rememberSaveable(eventId, planKey) { mutableStateOf("") }
    var places by rememberSaveable(eventId, planKey) { mutableStateOf("") }
    val canContribute = actor.club.isNotBlank() && actor.managesClub(actor.club) &&
        plan.optJSONObject("schools")?.has(actor.club) == true
    val canFinalize = actor.canManageEvent(event)
    // Rewriting the plan clears uploaded opinions, consents and rewards, so it is a backward,
    // destructive-path action: outlined, and the warning stays next to it. The step's own forward
    // action keeps the single solid weight.
    if (canFinalize) {
        Text(
            stringResource(R.string.workflow_edit_plan_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onEditPlan,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        ) { Text(stringResource(R.string.workflow_edit_plan)) }
    }
    if (step == 0) {
        StepHeader(4, R.string.workflow_step_material, R.string.workflow_step_material_help)
        if (canContribute) {
            OfficeAttachment(state, busy, "opinion-$eventId", document, { document = it }, send)
            if (document.isBlank()) {
                StepReason(R.string.workflow_material_missing)
            } else Text(
                stringResource(R.string.workflow_material_ready, shortIdentifier(document)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else StepReason(R.string.workflow_need_own_club)
        bottomAction?.set {
            WorkflowAction(R.string.workflow_next, enabled) { onStepChange(1) }
        }
    } else {
        StepHeader(5, R.string.workflow_step_review, R.string.workflow_step_review_help)
        if (canContribute) {
            if (kind == "online") {
                val available = JSONObject().also { map ->
                    plan.optJSONArray("candidates")?.let { ids ->
                        (0 until ids.length()).forEach { i ->
                            val id = ids.getString(i)
                            map.put(id, resources.optJSONObject(id) ?: JSONObject())
                        }
                    }
                }
                Text(stringResource(R.string.workflow_candidates))
                WorkflowChips(available, selected, enabled) { toggled -> selected = if (toggled in selected) selected - toggled else selected + toggled }
            }
            if (document.isBlank()) StepReason(R.string.workflow_material_missing)
            WorkflowAction(R.string.workflow_opinion, enabled && document.isNotBlank()) {
                send(JSONObject().put("type", "organization.opinion").put("event", eventId).put("document", document).put("games", selected.json()))
            }
            if (kind == "meeting") {
                WorkflowAction(R.string.workflow_consent, enabled && document.isNotBlank()) {
                    send(JSONObject().put("type", "organization.consent").put("event", eventId).put("document", document)
                        .put("transcript", JSONObject.NULL))
                }
            }
            OutlinedTextField(pointsText, { pointsText = it }, label = { Text(stringResource(R.string.workflow_points)) }, supportingText = { Text(stringResource(R.string.workflow_points_hint)) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(material, { material = it }, label = { Text(stringResource(R.string.workflow_material)) }, supportingText = { Text(stringResource(R.string.workflow_material_hint)) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(places, { places = it }, label = { Text(stringResource(R.string.workflow_top_places)) }, supportingText = { Text(stringResource(R.string.workflow_top_places_hint)) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
            val points = pointsText.trim().toLongOrNull()
            WorkflowAction(R.string.workflow_rewards, enabled && points != null && points >= 0 && (places.isBlank() || (places.toIntOrNull() ?: 0) > 0)) {
                send(JSONObject().put("type", "organization.rewards").put("event", eventId).put("rule", JSONObject().put("points", points)
                    .put("material", material.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL).put("top_places", places.trim().toIntOrNull() ?: JSONObject.NULL)))
            }
        } else StepReason(R.string.workflow_need_own_club)
        Text(stringResource(R.string.workflow_received, plan.optJSONObject("opinions")?.length() ?: 0, plan.optJSONObject("consents")?.length() ?: 0, plan.optJSONObject("rewards")?.length() ?: 0))
        // The rewards form ends in an input, so its last control used to be cut in half by the
        // system gesture bar. Both the step-back and the finalize action are pinned instead.
        bottomAction?.set {
            OutlinedButton(
                onClick = { onStepChange(0) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(R.string.workflow_back)) }
            if (canFinalize) {
                Text(
                    stringResource(R.string.workflow_finalize_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WorkflowAction(R.string.workflow_finalize, enabled && (kind != "online" || selected.isNotEmpty())) {
                    send(JSONObject().put("type", "organization.finalize").put("event", eventId).put("selected", selected.json()))
                }
            } else StepReason(R.string.workflow_finalize_restricted)
        }
    }
}

/** Reports are a list of summaries; creating and revising each have their own page. */
@Composable
internal fun OfficeReportsPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onCreate: () -> Unit,
    onOpenReport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val office = state?.optJSONObject("organization") ?: JSONObject()
    val actor = officeActor(state, identity)
    TaskPage(stringResource(R.string.workflow_reports), busy, onBack) {
        if (!actor.canManage) {
            Text(stringResource(R.string.workflow_reports_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        MenuEntry(
            label = stringResource(R.string.workflow_report_create),
            enabled = !busy,
            onClick = onCreate,
        )
        val reports = office.optJSONObject("reports") ?: JSONObject()
        if (reports.length() == 0) Text(stringResource(R.string.workflow_reports_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        reports.sortedKeys().forEach { reportId ->
            key(reportId) {
                val report = reports.getJSONObject(reportId)
                MenuEntry(
                    label = reportId,
                    help = stringResource(
                        R.string.office_report_summary,
                        report.optString("recipient"),
                        stringResource(
                            when (report.optString("status")) {
                                "generating" -> R.string.workflow_generating
                                "draft" -> R.string.workflow_draft
                                "published" -> R.string.workflow_published
                                else -> R.string.office_unknown
                            },
                        ),
                    ),
                    enabled = !busy,
                    onClick = { onOpenReport(reportId) },
                )
            }
        }
    }
}

/**
 * The society a report page is configuring. Report pages used to repeat the requirement copy in
 * several places while never naming the society, so the operator could not tell which club the
 * material belonged to.
 */
@Composable
private fun ReportSocietyHeader(club: String) {
    ManagerCard(emphasized = false) {
        Text(
            stringResource(R.string.workflow_report_society),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            club.ifBlank { stringResource(R.string.identity_none) },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** New report: period and requirements, then create. */
@Composable
internal fun OfficeReportCreatePage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    send: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    val office = state?.optJSONObject("organization") ?: JSONObject()
    val actor = officeActor(state, identity)
    val worker = office.optJSONObject("automation")?.optString("worker").orEmpty()
    val profile = office.optJSONObject("schools")?.optJSONObject(actor.club)
    TaskPage(stringResource(R.string.workflow_report_create), busy, onBack) {
        // Which society this report belongs to is the first thing the operator has to be sure of,
        // so it is stated once here instead of being implied by the repeated requirement copy.
        ReportSocietyHeader(actor.club)
        if (!actor.canManage) {
            Text(stringResource(R.string.workflow_reports_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        val id = workflowField(R.string.workflow_report_id, !busy)
        var annual by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
            WorkflowChoice(stringResource(R.string.workflow_mua), !annual, !busy) { annual = false }
            if (profile?.optString("kind") == "official") WorkflowChoice(stringResource(R.string.workflow_annual), annual, !busy) { annual = true }
        }
        val start = parseLocalDateTime(workflowDateField(R.string.workflow_period_start, !busy))
        val end = parseLocalDateTime(workflowDateField(R.string.workflow_period_end, !busy))
        val uploadedRequirements = workflowField(R.string.workflow_report_requirements, !busy, hint = R.string.workflow_report_requirements_hint)
        val requirements = uploadedRequirements.takeIf { it.isNotBlank() } ?: profile?.optString(if (annual) "annual_requirements" else "mua_requirements")
        val recipient = profile?.optString(if (annual) "annual_recipient" else "mua_recipient")
        WorkflowAction(R.string.workflow_report_create, !busy && worker.isNotBlank() && id.isNotBlank() && requirements != null && recipient != null && start != null && end != null && start < end) {
            send(JSONObject().put("type", "organization.report").put("id", id).put("kind", if (annual) "club_annual_review" else "mua_annual")
                .put("period_start_ms", start).put("period_end_ms", end).put("requirements", requirements).put("recipient", recipient).put("worker", worker))
        }
    }
}

/** One report: evidence, revision and publication, each as an explicit action. */
@Composable
internal fun OfficeReportDetailPage(
    state: JSONObject?,
    identity: String,
    reportId: String,
    busy: Boolean,
    send: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    val office = state?.optJSONObject("organization") ?: JSONObject()
    val report = office.optJSONObject("reports")?.optJSONObject(reportId)
    val actor = officeActor(state, identity)
    TaskPage(reportId, busy, onBack) {
        ReportSocietyHeader(actor.club)
        if (report == null) {
            Text(stringResource(R.string.office_detail_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        if (!actor.canManage) {
            Text(stringResource(R.string.workflow_reports_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        Text(report.optString("recipient"), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(
                when (report.optString("status")) {
                    "generating" -> R.string.workflow_generating
                    "draft" -> R.string.workflow_draft
                    "published" -> R.string.workflow_published
                    else -> R.string.office_unknown
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.workflow_evidence))
        IdentifierDetails(report.optString("evidence"))
        if (!report.isNull("output")) {
            Text(stringResource(R.string.workflow_output))
            IdentifierDetails(report.getString("output"))
        }
        val document = workflowField(R.string.workflow_document, !busy)
        WorkflowAction(R.string.workflow_report_revise, !busy && document.isNotBlank() && report.optString("status") in setOf("draft", "published")) {
            send(JSONObject().put("type", "organization.report_revise").put("id", reportId).put("document", document))
        }
        Text(stringResource(R.string.workflow_publication_help))
        WorkflowAction(R.string.workflow_report_publish, !busy && report.optString("status") == "draft") {
            send(JSONObject().put("type", "organization.report_publish").put("id", reportId))
        }
    }
}

/** Workflow configuration menu: automation, club report profiles and league resources. */
@Composable
internal fun OfficeWorkflowSetupHome(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onOpenAutomation: () -> Unit,
    onOpenSchool: () -> Unit,
    onOpenResource: () -> Unit,
    onBack: () -> Unit,
) {
    val actor = officeActor(state, identity)
    TaskPage(stringResource(R.string.workflow_setup), busy, onBack) {
        Text(stringResource(R.string.workflow_admin_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!actor.canManage) {
            Text(stringResource(R.string.workflow_setup_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        MenuEntry(stringResource(R.string.workflow_automation), enabled = !busy, onClick = onOpenAutomation)
        MenuEntry(stringResource(R.string.workflow_school_title), stringResource(R.string.workflow_school_help), enabled = !busy, onClick = onOpenSchool)
        MenuEntry(stringResource(R.string.workflow_resource_title), stringResource(R.string.workflow_resource_help), enabled = !busy, onClick = onOpenResource)
    }
}

@Composable
internal fun WorkflowAutomationPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    send: (JSONObject) -> Unit,
    onBack: () -> Unit,
) {
    val office = state?.optJSONObject("organization") ?: JSONObject()
    val actor = officeActor(state, identity)
    TaskPage(stringResource(R.string.workflow_automation), busy, onBack) {
        if (!actor.canManage) {
            Text(stringResource(R.string.workflow_setup_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        val worker = workflowField(R.string.workflow_worker, !busy, office.optJSONObject("automation")?.optString("worker").orEmpty())
        val model = workflowField(R.string.workflow_stage_model, !busy)
        val limit = workflowField(R.string.workflow_stage_tokens, !busy, hint = R.string.workflow_stage_tokens_hint).toIntOrNull()
        val timeout = workflowField(R.string.workflow_stage_timeout, !busy, hint = R.string.workflow_stage_timeout_hint).toLongOrNull()
        WorkflowAction(R.string.workflow_automation_save, !busy && worker.isNotBlank() && model.isNotBlank() && limit != null && limit in 1..8192 && timeout != null && timeout in 1..43200) {
            send(JSONObject().put("type", "organization.automation").put("config", JSONObject().put("worker", worker).put("model", model).put("max_tokens", limit).put("timeout_ms", requireNotNull(timeout) * 60000)))
        }
    }
}

@Serializable internal data object SchoolSettingsStep : NavKey
@Serializable internal data object SchoolMuaStep : NavKey
@Serializable internal data object SchoolAnnualStep : NavKey

/**
 * Club report profile in two or three steps. Only the requirements of the chosen club type are
 * shown: an independent club never sees the annual fields, an official club sets them on their own page.
 */
@Composable
internal fun WorkflowSchoolPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    send: FormSender,
    onBack: () -> Unit,
) {
    val members = state?.optJSONObject("members") ?: JSONObject()
    val actor = officeActor(state, identity)
    if (!actor.canManage) {
        TaskPage(stringResource(R.string.workflow_school_title), busy, onBack) {
            Text(stringResource(R.string.workflow_setup_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val submission = rememberFormSubmission()
    var club by rememberSaveable { mutableStateOf("") }
    var official by rememberSaveable { mutableStateOf(false) }
    var muaRequirements by rememberSaveable { mutableStateOf("") }
    var muaRecipient by rememberSaveable { mutableStateOf("") }
    var annualRequirements by rememberSaveable { mutableStateOf("") }
    var annualRecipient by rememberSaveable { mutableStateOf("") }
    val backStack = rememberNavBackStack(SchoolSettingsStep)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    fun save() {
        val profile = JSONObject()
            .put("kind", if (official) "official" else "independent")
            .put("mua_requirements", muaRequirements.trim())
            .put("mua_recipient", muaRecipient.trim())
            .put("annual_requirements", annualRequirements.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("annual_recipient", annualRecipient.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        val request = JSONObject().put("type", "organization.school_profile").put("club", club).put("profile", profile)
        submission.submit(send, request) { snapshot ->
            val saved = snapshot.optJSONObject("organization")?.optJSONObject("schools")?.optJSONObject(club)
            if (saved?.optString("kind") == profile.optString("kind")) {
                onBack()
                true
            } else false
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<SchoolSettingsStep> {
                TaskPage(stringResource(R.string.workflow_school_title), busy, pop) {
                    StepProgress(step = 1, total = if (official) 3 else 2)
                    StepHeader(1, R.string.workflow_school_settings)
                    Text(stringResource(R.string.identity_field_club), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                        members.sortedKeys().map { members.getJSONObject(it).optString("club") }.distinct().sorted().forEach {
                            WorkflowChoice(it, club == it, !busy) { club = it }
                        }
                    }
                    WorkflowChoice(stringResource(R.string.workflow_official), official, !busy) { official = !official }
                    Text(
                        stringResource(if (official) R.string.workflow_school_official_help else R.string.workflow_school_independent_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StepReason(if (club.isBlank()) R.string.workflow_school_need_club else null)
                    WorkflowAction(R.string.workflow_next, !busy && club.isNotBlank()) { backStack.add(SchoolMuaStep) }
                }
            }
            entry<SchoolMuaStep> {
                TaskPage(stringResource(R.string.workflow_school_mua_title), busy, pop) {
                    StepProgress(step = 2, total = if (official) 3 else 2)
                    StepHeader(2, R.string.workflow_school_mua)
                    OutlinedTextField(muaRequirements, { muaRequirements = it }, label = { Text(stringResource(R.string.workflow_mua_requirements)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(muaRecipient, { muaRecipient = it }, label = { Text(stringResource(R.string.workflow_mua_recipient)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    val muaReady = muaRequirements.isNotBlank() && muaRecipient.isNotBlank()
                    StepReason(if (!muaReady) R.string.workflow_school_need_mua else null)
                    if (official) {
                        WorkflowAction(R.string.workflow_next, !busy && muaReady) { backStack.add(SchoolAnnualStep) }
                    } else {
                        WorkflowAction(R.string.workflow_school_save, !busy && muaReady && !submission.pending) { save() }
                    }
                    submission.failure?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = pop, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.workflow_back))
                    }
                }
            }
            entry<SchoolAnnualStep> {
                TaskPage(stringResource(R.string.workflow_school_annual_title), busy, pop) {
                    StepProgress(step = 3, total = 3)
                    StepHeader(3, R.string.workflow_school_annual)
                    OutlinedTextField(annualRequirements, { annualRequirements = it }, label = { Text(stringResource(R.string.workflow_annual_requirements)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(annualRecipient, { annualRecipient = it }, label = { Text(stringResource(R.string.workflow_annual_recipient)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    val annualReady = annualRequirements.isNotBlank() && annualRecipient.isNotBlank()
                    StepReason(if (!annualReady) R.string.workflow_school_need_annual else null)
                    WorkflowAction(R.string.workflow_school_save, !busy && annualReady && !submission.pending) { save() }
                    submission.failure?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = pop, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.workflow_back))
                    }
                }
            }
        },
    )
}

/** The workflow wizard's five steps, shared by the wide outline and the per-step headers. */
private val WORKFLOW_STEPS = listOf(
    R.string.workflow_step_event,
    R.string.workflow_step_schools,
    R.string.workflow_step_schedule,
    R.string.workflow_step_material,
    R.string.workflow_step_review,
)

/**
 * The plan fields that invalidate an in-progress form.
 *
 * Both the draft form and the review form reset on this key, and the page uses the same key for the
 * wizard's step position, so "the plan changed, start over" has exactly one definition.
 */
private fun planFormKey(plan: JSONObject?, revision: Int? = null): String = listOfNotNull(
    revision?.toString(),
    plan?.optJSONObject("schools")?.toString(),
    plan?.optJSONArray("candidates")?.toString(),
    plan?.optLong("checkin_opens_ms")?.toString(),
    plan?.optLong("checkin_closes_ms")?.toString(),
    plan?.optLong("ends_ms")?.toString(),
).joinToString("|")

/** The resource wizard's steps in order, shared by the wide outline and the progress bar. */
private val RESOURCE_STEPS = listOf(
    R.string.workflow_resource_basics,
    R.string.workflow_resource_connection,
    R.string.workflow_resource_rules,
    R.string.workflow_resource_review_step,
)

/** Per-team player count offered by both the league and resource forms. */
private val RESOURCE_SEATS_RANGE = 2..6

@Serializable internal data object ResourceBasicsStep : NavKey
@Serializable internal data object ResourceConnectionStep : NavKey
@Serializable internal data object ResourceRulesStep : NavKey
@Serializable internal data object ResourceReviewStep : NavKey

/** League resource in four steps: identity, server, match/ops rules, then one review and save. */
@Composable
internal fun WorkflowResourcePage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    send: FormSender,
    onBack: () -> Unit,
) {
    val actor = officeActor(state, identity)
    if (!actor.canManage) {
        TaskPage(stringResource(R.string.workflow_resource_title), busy, onBack) {
            Text(stringResource(R.string.workflow_setup_restricted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val submission = rememberFormSubmission()
    var resourceId by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var peer by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var scorer by rememberSaveable { mutableStateOf("") }
    var seats by rememberSaveable { mutableStateOf("") }
    var operations by rememberSaveable { mutableStateOf("") }
    var instance by rememberSaveable { mutableStateOf("") }
    var cross by rememberSaveable { mutableStateOf(false) }
    val seatCount = seats.trim().toIntOrNull()
    val backStack = rememberNavBackStack(ResourceBasicsStep)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<ResourceBasicsStep> {
                TaskPage(
                    title = stringResource(R.string.workflow_resource_title),
                    busy = busy,
                    onBack = pop,
                    steps = RESOURCE_STEPS,
                    currentStep = 1,
                    bottomBar = {
                        BottomActionBar {
                            WorkflowAction(R.string.workflow_next, !busy && resourceId.isNotBlank() && title.isNotBlank()) {
                                backStack.add(ResourceConnectionStep)
                            }
                        }
                    },
                ) {
                    StepProgress(step = 1, total = 4)
                    StepHeader(1, R.string.workflow_resource_basics)
                    OutlinedTextField(resourceId, { resourceId = it }, label = { Text(stringResource(R.string.workflow_resource_id)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.workflow_resource_title_field)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            entry<ResourceConnectionStep> {
                TaskPage(
                    title = stringResource(R.string.workflow_resource_connection_title),
                    busy = busy,
                    onBack = pop,
                    steps = RESOURCE_STEPS,
                    currentStep = 2,
                    bottomBar = {
                        BottomActionBar {
                            WorkflowAction(R.string.workflow_next, !busy && address.isNotBlank() && peer.isNotBlank()) {
                                backStack.add(ResourceRulesStep)
                            }
                            OutlinedButton(
                                onClick = pop,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                            ) { Text(stringResource(R.string.workflow_back)) }
                        }
                    },
                ) {
                    StepProgress(step = 2, total = 4)
                    StepHeader(2, R.string.workflow_resource_connection, R.string.workflow_resource_connection_help)
                    OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.workflow_resource_address)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(peer, { peer = it }, label = { Text(stringResource(R.string.workflow_resource_peer)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            entry<ResourceRulesStep> {
                // Moved above the scaffold so both the pinned bar and the scrolling form read one
                // definition of readiness.
                val rulesReady = host.isNotBlank() && scorer.isNotBlank() && operations.isNotBlank() &&
                    instance.isNotBlank() && seatCount in RESOURCE_SEATS_RANGE
                TaskPage(
                    title = stringResource(R.string.workflow_resource_rules_title),
                    busy = busy,
                    onBack = pop,
                    steps = RESOURCE_STEPS,
                    currentStep = 3,
                    bottomBar = {
                        BottomActionBar {
                            // The rules step is the long one: the advance action and its reason stay
                            // pinned while the six inputs scroll above them.
                            StepReason(if (!rulesReady) R.string.workflow_resource_need_rules else null)
                            WorkflowAction(R.string.workflow_next, !busy && rulesReady) { backStack.add(ResourceReviewStep) }
                            OutlinedButton(
                                onClick = pop,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                            ) { Text(stringResource(R.string.workflow_back)) }
                        }
                    },
                ) {
                    StepProgress(step = 3, total = 4)
                    StepHeader(3, R.string.workflow_resource_rules, R.string.workflow_resource_rules_help)
                    OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.workflow_resource_host)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(scorer, { scorer = it }, label = { Text(stringResource(R.string.workflow_resource_scorer)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    NumberStepperField(
                        value = seats,
                        label = R.string.workflow_resource_seats,
                        range = RESOURCE_SEATS_RANGE,
                        enabled = !busy,
                        onValueChange = { seats = it },
                    )
                    WorkflowChoice(stringResource(R.string.workflow_cross_teams), cross, !busy) { cross = !cross }
                    OutlinedTextField(operations, { operations = it }, label = { Text(stringResource(R.string.workflow_resource_operations)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(instance, { instance = it }, label = { Text(stringResource(R.string.workflow_resource_instance)) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            entry<ResourceReviewStep> {
                val request = JSONObject().put("type", "organization.resource").put("id", resourceId).put(
                    "resource",
                    JSONObject().put("title", title).put("address", address).put("peer", peer)
                        .put("host", host).put("scorer", scorer).put("seats", seatCount)
                        .put("cross_school", cross).put("automatic_teams", true)
                        .put("operations_service", operations).put("instance", instance),
                )
                TaskPage(
                    title = stringResource(R.string.workflow_resource_title),
                    busy = busy,
                    onBack = pop,
                    steps = RESOURCE_STEPS,
                    currentStep = 4,
                    bottomBar = {
                        BottomActionBar {
                            WorkflowAction(
                                label = R.string.workflow_resource_save,
                                enabled = !busy && !submission.pending && seatCount in RESOURCE_SEATS_RANGE,
                            ) {
                                submission.submit(send, request) { snapshot ->
                                    if (snapshot.optJSONObject("organization")?.optJSONObject("resources")?.has(resourceId) == true) {
                                        onBack()
                                        true
                                    } else false
                                }
                            }
                        }
                    },
                ) {
                    StepProgress(step = 4, total = 4)
                    ManagerCard(
                        title = title,
                        emphasized = false,
                    ) {
                        Text(stringResource(R.string.workflow_resource_id), style = MaterialTheme.typography.labelMedium)
                        Text(resourceId, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.workflow_resource_seats), style = MaterialTheme.typography.labelMedium)
                        Text(seatCount?.toString().orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(if (cross) R.string.workflow_cross_teams else R.string.league_same_school),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    // Every field from steps 2 and 3 is repeated here in full: a review page that
                    // omits the address or the device identifiers cannot verify the resource.
                    ManagerCard(
                        title = stringResource(R.string.workflow_resource_connection_title),
                        emphasized = false,
                    ) {
                        IdentifierField(address, stringResource(R.string.workflow_resource_address))
                        IdentifierField(peer, stringResource(R.string.workflow_resource_peer))
                    }
                    ManagerCard(
                        title = stringResource(R.string.workflow_resource_rules_title),
                        emphasized = false,
                    ) {
                        IdentifierField(host, stringResource(R.string.workflow_resource_host))
                        IdentifierField(scorer, stringResource(R.string.workflow_resource_scorer))
                        IdentifierField(operations, stringResource(R.string.workflow_resource_operations))
                        IdentifierField(instance, stringResource(R.string.workflow_resource_instance))
                    }
                    submission.failure?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}
