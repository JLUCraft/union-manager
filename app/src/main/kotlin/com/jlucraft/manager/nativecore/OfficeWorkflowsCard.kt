package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.sortedKeys() = keys().asSequence().toList().sorted()
private fun Iterable<String>.json() = JSONArray(toList())

@Composable
private fun workflowField(label: Int, enabled: Boolean, initial: String = ""): String {
    var value by remember { mutableStateOf(initial) }
    OutlinedTextField(value, { value = it }, label = { Text(stringResource(label)) }, enabled = enabled,
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
@Composable
private fun workflowSelection(items: JSONObject, enabled: Boolean): Set<String> {
    var selection by remember { mutableStateOf(emptySet<String>()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        items.sortedKeys().forEach { id -> key(id) {
            WorkflowChoice(items.optJSONObject(id)?.optString("title")?.takeIf { it.isNotBlank() } ?: id,
                id in selection, enabled) { selection = if (id in selection) selection - id else selection + id }
        } }
    }
    return selection.intersect(items.sortedKeys().toSet())
}

@Composable
fun OfficeWorkflowsCard(state: JSONObject?, busy: Boolean, device: String, focusedNotice: String?, openLeague: (JSONObject, String) -> Unit, send: (JSONObject) -> Unit) {
    ManagerCard(title = stringResource(R.string.workflow_title), helper = stringResource(R.string.workflow_help)) {
        if (state == null) { Text(stringResource(R.string.office_connect)); return@ManagerCard }
        val enabled = !busy
        val office = state.optJSONObject("office") ?: JSONObject()
        val members = state.optJSONObject("members") ?: JSONObject()
        val actor = officeMemberId(state, device)
        val club = actor?.let { members.getJSONObject(it).optString("club") }
        val automation = office.optJSONObject("automation")
        val worker = automation?.optString("worker").orEmpty()
        val profiles = office.optJSONObject("schools") ?: JSONObject()
        val events = state.optJSONObject("events") ?: JSONObject()
        val plans = office.optJSONObject("plans") ?: JSONObject()
        val resources = office.optJSONObject("resources") ?: JSONObject()
        val noticeEvent = focusedNotice?.let { office.optJSONObject("notices")?.optJSONObject(it)?.optString("event") }
        var section by remember { mutableStateOf(0) }
        LaunchedEffect(noticeEvent) { if (noticeEvent != null) section = if (plans.optJSONObject(noticeEvent)?.optString("kind") == "meeting") 1 else 0 }
        val sections = listOf(R.string.workflow_online, R.string.workflow_meeting, R.string.workflow_reports, R.string.workflow_setup)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            sections.forEachIndexed { index, label ->
                SegmentedButton(selected = section == index, onClick = { section = index }, enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sections.size)) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        when (section) {
            0, 1 -> {
                val kind = if (section == 0) "online" else "meeting"
                if (events.length() == 0) Text(stringResource(R.string.office_empty))
                var chosen by remember(section, noticeEvent) { mutableStateOf(noticeEvent ?: "") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                    events.sortedKeys().filter { plans.optJSONObject(it)?.optString("kind")?.let { k -> k == kind } != false }.forEach { id ->
                        WorkflowChoice(events.getJSONObject(id).optString("title"), chosen == id, enabled) { chosen = id }
                    }
                }
                val event = events.optJSONObject(chosen)
                if (event != null) key(chosen) {
                    val plan = plans.optJSONObject(chosen)
                    if (plan == null && event.optString("phase") == "draft") {
                        Text(stringResource(R.string.workflow_schools))
                        val schools = workflowSelection(profiles, enabled)
                        Text(stringResource(R.string.workflow_participants))
                        val participants = workflowSelection(JSONObject().also { map -> members.sortedKeys().filter { members.getJSONObject(it).optString("club") in schools }.forEach { map.put(it, members.getJSONObject(it)) } }, enabled)
                        val candidates = if (kind == "online") { Text(stringResource(R.string.workflow_candidates)); workflowSelection(resources, enabled) } else emptySet()
                        val opens = parseLocalDateTime(workflowDateField(R.string.workflow_checkin_opens, enabled))
                        val closes = parseLocalDateTime(workflowDateField(R.string.workflow_checkin_closes, enabled))
                        val ends = parseLocalDateTime(workflowDateField(R.string.workflow_ends, enabled))
                        WorkflowAction(R.string.workflow_plan, enabled && worker.isNotBlank() && schools.isNotEmpty() && opens != null && closes != null && ends != null && opens < closes && closes < ends && (kind != "online" || candidates.isNotEmpty())) {
                            val selectedSchools = JSONObject()
                            schools.forEach { school -> selectedSchools.put(school, JSONObject().put("kind", profiles.getJSONObject(school).getString("kind"))
                                .put("participants", participants.filter { members.getJSONObject(it).optString("club") == school }.json())) }
                            send(JSONObject().put("type", "office.plan").put("event", chosen).put("kind", kind).put("schools", selectedSchools)
                                .put("candidates", candidates.json()).put("checkin_opens_ms", opens).put("checkin_closes_ms", closes).put("ends_ms", ends).put("worker", worker))
                        }
                    } else if (plan != null) {
                        if (event.optString("phase") == "draft" && plan.isNull("final_epoch")) {
                            val document = workflowField(R.string.workflow_document, enabled)
                            val available = JSONObject().also { map -> plan.optJSONArray("candidates")?.let { ids -> (0 until ids.length()).forEach { i -> val id = ids.getString(i); map.put(id, resources.optJSONObject(id) ?: JSONObject()) } } }
                            val selected = workflowSelection(available, enabled)
                            WorkflowAction(R.string.workflow_opinion, enabled && document.isNotBlank()) {
                                send(JSONObject().put("type", "office.opinion").put("event", chosen).put("document", document).put("games", selected.json()))
                            }
                            if (kind == "meeting") {
                                WorkflowAction(R.string.workflow_consent, enabled && document.isNotBlank()) {
                                    send(JSONObject().put("type", "office.consent").put("event", chosen).put("document", document)
                                        .put("transcript", JSONObject.NULL))
                                }
                            }
                            val points = workflowField(R.string.workflow_points, enabled).toLongOrNull()
                            val material = workflowField(R.string.workflow_material, enabled)
                            val places = workflowField(R.string.workflow_top_places, enabled)
                            WorkflowAction(R.string.workflow_rewards, enabled && points != null && points >= 0 && (places.isBlank() || (places.toIntOrNull() ?: 0) > 0)) {
                                send(JSONObject().put("type", "office.rewards").put("event", chosen).put("rule", JSONObject().put("points", points)
                                    .put("material", material.takeIf { it.isNotBlank() } ?: JSONObject.NULL).put("top_places", places.toIntOrNull() ?: JSONObject.NULL)))
                            }
                            Text(stringResource(R.string.workflow_received, plan.optJSONObject("opinions")?.length() ?: 0, plan.optJSONObject("consents")?.length() ?: 0, plan.optJSONObject("rewards")?.length() ?: 0))
                            WorkflowAction(R.string.workflow_finalize, enabled && (kind != "online" || selected.isNotEmpty())) {
                                send(JSONObject().put("type", "office.finalize").put("event", chosen).put("selected", selected.json()))
                            }
                        }
                        if (!plan.isNull("final_epoch") && event.optString("phase") != "done") Text(stringResource(R.string.workflow_automatic))
                        listOf("polished" to R.string.workflow_polished, "summary" to R.string.workflow_summary).forEach { (field, label) ->
                            if (!plan.isNull(field)) { Text(stringResource(label)); IdentifierDetails(plan.getString(field)) }
                        }
                        plan.optJSONObject("games")?.let { games -> games.sortedKeys().forEach { id ->
                            val game = games.getJSONObject(id)
                            Text(resources.optJSONObject(id)?.optString("title") ?: id)
                            Text(stringResource(if (!game.isNull("results")) R.string.workflow_game_finished else if (game.optBoolean("started")) R.string.workflow_game_started else R.string.workflow_game_waiting))
                            resources.optJSONObject(id)?.let { resource ->
                                WorkflowAction(R.string.workflow_open_league, enabled) { openLeague(resource, game.optString("league")) }
                            }
                            if (!game.isNull("error")) Text(stringResource(R.string.workflow_game_failed), color = MaterialTheme.colorScheme.error)
                            game.optJSONObject("results")?.let { scores -> scores.sortedKeys().forEach { member -> Text(stringResource(R.string.workflow_score, member, scores.getLong(member))) } }
                        } }
                        plan.optJSONObject("awards")?.let { awards -> awards.sortedKeys().forEach { member -> key(member) {
                            val award = awards.getJSONObject(member)
                            Text(stringResource(R.string.workflow_award, member, award.optLong("points"), if (award.isNull("material")) "" else award.optString("material")))
                            if (!award.isNull("material")) {
                                WorkflowAction(R.string.workflow_deliver, enabled && award.isNull("delivered_by")) { send(JSONObject().put("type", "office.deliver").put("event", chosen).put("member", member)) }
                                WorkflowAction(R.string.workflow_receive, enabled && member == actor && !award.isNull("delivered_by") && award.isNull("received_ms")) { send(JSONObject().put("type", "office.receive").put("event", chosen)) }
                            }
                        } } }
                    }
                    if (event.optString("phase") == "open") {
                        WorkflowAction(R.string.workflow_checkin, enabled) { send(JSONObject().put("type", "events.checkin").put("id", chosen)) }
                    }
                    val tasks = state.optJSONObject("tasks") ?: JSONObject()
                    tasks.sortedKeys().filter { tasks.getJSONObject(it).optString("event") == chosen }.forEach { task -> key(task) {
                        Text(tasks.getJSONObject(task).optString("title"))
                        val notice = office.optJSONObject("notices")?.optJSONObject(task)
                        run {
                            val due = parseLocalDateTime(workflowDateField(R.string.workflow_task_due, enabled))
                            WorkflowAction(R.string.workflow_notify, enabled && due != null) { send(JSONObject().put("type", "office.notify").put("task", task).put("due_ms", due)) }
                        }
                        if (notice != null) {
                            WorkflowAction(R.string.workflow_acknowledge, enabled && notice.optString("member") == actor && notice.isNull("acknowledged_ms")) {
                                send(JSONObject().put("type", "office.acknowledge").put("notice", task))
                            }
                        }
                    } }
                }
            }
            2 -> Reports(office, club, worker, enabled, send)
            3 -> WorkflowSetup(office, members, enabled, send)
        }
    }
}

@Composable
private fun Reports(office: JSONObject, club: String?, worker: String, enabled: Boolean, send: (JSONObject) -> Unit) {
    val profile = office.optJSONObject("schools")?.optJSONObject(club ?: "")
    val id = workflowField(R.string.workflow_report_id, enabled)
    var annual by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        WorkflowChoice(stringResource(R.string.workflow_mua), !annual, enabled) { annual = false }
        if (profile?.optString("kind") == "official") WorkflowChoice(stringResource(R.string.workflow_annual), annual, enabled) { annual = true }
    }
    val start = parseLocalDateTime(workflowDateField(R.string.workflow_period_start, enabled))
    val end = parseLocalDateTime(workflowDateField(R.string.workflow_period_end, enabled))
    val uploadedRequirements = workflowField(R.string.workflow_report_requirements, enabled)
    val requirements = uploadedRequirements.takeIf { it.isNotBlank() } ?: profile?.optString(if (annual) "annual_requirements" else "mua_requirements")
    val recipient = profile?.optString(if (annual) "annual_recipient" else "mua_recipient")
    WorkflowAction(R.string.workflow_report_create, enabled && worker.isNotBlank() && id.isNotBlank() && requirements != null && recipient != null && start != null && end != null && start < end) {
        send(JSONObject().put("type", "office.report").put("id", id).put("kind", if (annual) "club_annual_review" else "mua_annual")
            .put("period_start_ms", start).put("period_end_ms", end).put("requirements", requirements).put("recipient", recipient).put("worker", worker))
    }
    val reports = office.optJSONObject("reports") ?: JSONObject()
    if (reports.length() == 0) Text(stringResource(R.string.workflow_reports_empty))
    reports.sortedKeys().forEach { reportId -> key(reportId) {
        val report = reports.getJSONObject(reportId)
        Text(reportId, style = MaterialTheme.typography.titleMedium)
        Text(report.optString("recipient"))
        Text(stringResource(when (report.optString("status")) {
            "generating" -> R.string.workflow_generating
            "draft" -> R.string.workflow_draft
            "published" -> R.string.workflow_published
            else -> R.string.office_unknown
        }))
        Text(stringResource(R.string.workflow_evidence)); IdentifierDetails(report.optString("evidence"))
        if (!report.isNull("output")) { Text(stringResource(R.string.workflow_output)); IdentifierDetails(report.getString("output")) }
        val document = workflowField(R.string.workflow_document, enabled)
        WorkflowAction(R.string.workflow_report_revise, enabled && document.isNotBlank() && report.optString("status") in setOf("draft", "published")) {
            send(JSONObject().put("type", "office.report_revise").put("id", reportId).put("document", document))
        }
        Text(stringResource(R.string.workflow_publication_help))
        WorkflowAction(R.string.workflow_report_publish, enabled && report.optString("status") == "draft") {
            send(JSONObject().put("type", "office.report_publish").put("id", reportId))
        }
    } }
}

@Composable
private fun WorkflowSetup(office: JSONObject, members: JSONObject, enabled: Boolean, send: (JSONObject) -> Unit) {
    Text(stringResource(R.string.workflow_admin_help))
    val worker = workflowField(R.string.workflow_worker, enabled, office.optJSONObject("automation")?.optString("worker").orEmpty())
    val model = workflowField(R.string.workflow_stage_model, enabled)
    val limit = workflowField(R.string.workflow_stage_tokens, enabled).toIntOrNull()
    val timeout = workflowField(R.string.workflow_stage_timeout, enabled).toLongOrNull()
    WorkflowAction(R.string.workflow_automation_save, enabled && worker.isNotBlank() && model.isNotBlank() && limit != null && limit in 1..8192 && timeout != null && timeout in 1..43200) {
        send(JSONObject().put("type", "office.automation").put("config", JSONObject().put("worker", worker).put("model", model).put("max_tokens", limit).put("timeout_ms", requireNotNull(timeout) * 60000)))
    }
    var club by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        members.sortedKeys().map { members.getJSONObject(it).optString("club") }.distinct().sorted().forEach {
            WorkflowChoice(it, club == it, enabled) { club = it }
        }
    }
    var official by remember { mutableStateOf(false) }
    WorkflowChoice(stringResource(R.string.workflow_official), official, enabled) { official = !official }
    val muaRequirements = workflowField(R.string.workflow_mua_requirements, enabled)
    val muaRecipient = workflowField(R.string.workflow_mua_recipient, enabled)
    val annualRequirements = workflowField(R.string.workflow_annual_requirements, enabled)
    val annualRecipient = workflowField(R.string.workflow_annual_recipient, enabled)
    WorkflowAction(R.string.workflow_school_save, enabled && club.isNotBlank() && muaRequirements.isNotBlank() && muaRecipient.isNotBlank() && (!official || (annualRequirements.isNotBlank() && annualRecipient.isNotBlank()))) {
        send(JSONObject().put("type", "office.school_profile").put("club", club).put("profile", JSONObject().put("kind", if (official) "official" else "independent")
            .put("mua_requirements", muaRequirements).put("mua_recipient", muaRecipient).put("annual_requirements", annualRequirements.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("annual_recipient", annualRecipient.takeIf { it.isNotBlank() } ?: JSONObject.NULL)))
    }
    val resourceId = workflowField(R.string.workflow_resource_id, enabled)
    val title = workflowField(R.string.office_name, enabled)
    val address = workflowField(R.string.workflow_resource_address, enabled)
    val peer = workflowField(R.string.workflow_resource_peer, enabled)
    val genesis = workflowField(R.string.workflow_resource_genesis, enabled)
    val host = workflowField(R.string.workflow_resource_host, enabled)
    val scorer = workflowField(R.string.workflow_resource_scorer, enabled)
    val seats = workflowField(R.string.workflow_resource_seats, enabled).toIntOrNull()
    val operations = workflowField(R.string.workflow_resource_operations, enabled)
    val instance = workflowField(R.string.workflow_resource_instance, enabled)
    var cross by remember { mutableStateOf(false) }
    WorkflowChoice(stringResource(R.string.workflow_cross_teams), cross, enabled) { cross = !cross }
    WorkflowAction(R.string.workflow_resource_save, enabled && listOf(resourceId, title, address, peer, genesis, host, scorer, operations, instance).all { it.isNotBlank() } && seats != null && seats > 0) {
        send(JSONObject().put("type", "office.resource").put("id", resourceId).put("resource", JSONObject().put("title", title).put("address", address).put("peer", peer)
            .put("genesis", genesis).put("host", host).put("scorer", scorer).put("seats", seats).put("cross_school", cross).put("automatic_teams", true)
            .put("operations_service", operations).put("instance", instance)))
    }
}

