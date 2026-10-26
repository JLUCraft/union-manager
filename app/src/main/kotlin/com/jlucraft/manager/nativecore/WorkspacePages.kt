package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R
import org.json.JSONObject

internal const val ACTIVITY_CREATE_DRAFT_TAG = "activity_create_draft"
internal const val ACTIVITY_CREATE_ONLINE_TAG = "activity_create_online"
internal const val ACTIVITY_CREATE_MEETING_TAG = "activity_create_meeting"
internal const val ACTIVITY_CREATE_COMPETITION_TAG = "activity_create_competition"

/**
 * The primary-destination body.
 *
 * On a compact window it is one centered column. On an expanded window the same content is laid out
 * as a two-pane master/detail: the workbench summary and entries stay in the leading pane while the
 * trailing pane holds the selected detail, so an expanded tablet no longer wastes most of its
 * width on empty margins around a phone-width column.
 */
/**
 * The primary-destination body, split into the workbench summary ([master]) and the destination's
 * own entries ([detail]). Compact windows keep the previous stacked order; expanded ones show both
 * panes side by side through the shared [MasterDetailPage].
 */
@Composable
private fun WorkspaceContainer(
    master: @Composable ColumnScope.() -> Unit,
    detail: @Composable ColumnScope.() -> Unit,
) {
    MasterDetailPage(master = master, detail = detail)
}


@Composable
private fun WorkspaceRoleSummary(actor: OfficeActor) {
    val labels = buildList {
        if (actor.president) add(R.string.office_role_president)
        if (actor.admin) add(R.string.office_role_global_admin)
        if (actor.manager) add(R.string.office_role_manager)
    }
    val role = if (labels.isEmpty()) stringResource(R.string.office_role_member)
    else labels.map { stringResource(it) }.joinToString(stringResource(R.string.governance_separator))
    if (actor.bound) {
        Text(
            stringResource(R.string.office_role_identity, actor.member, role),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        if (actor.admin) {
            Text(
                role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.office_role_unbound),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WorkspaceDashboard(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onCreateActivity: () -> Unit,
    onMembershipIssuance: () -> Unit,
    onPersonnel: () -> Unit,
    onOpenActivities: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenResources: () -> Unit,
) {
    val actor = officeActor(state, identity)
    val scope = officeTaskScope(actor)
    if (state == null) {
        WorkspaceContainer(
            master = {
                ManagerCard(
                    title = stringResource(R.string.office_disconnected_title),
                    helper = stringResource(R.string.office_disconnected_help),
                    emphasized = false,
                ) {}
            },
            detail = {},
        )
        return
    }
    // Counts are used by both panes (the task desk and the approvals entry), so they are computed
    // once per page instead of per pane.
    val taskIds = officeTaskEventIds(state, actor)
    val pendingApprovals = workspacePendingApprovalCount(state, identity)
    val pendingTodos = workspacePendingTodoCount(state, actor)
    WorkspaceContainer(
        master = {
        WorkspaceRoleSummary(actor)
        ManagerCard(
            title = stringResource(scope.title),
            helper = stringResource(scope.helper),
            emphasized = true,
        ) {
            if (actor.canCreateDraft) MenuEntry(
                label = stringResource(R.string.office_create),
                help = stringResource(R.string.office_tasks_create_help),
                enabled = !busy,
                modifier = Modifier.testTag(OFFICE_TASK_CREATE_TAG),
                onClick = onCreateActivity,
            )
            if (actor.president) MenuEntry(
                label = stringResource(R.string.office_tasks_issuance),
                help = stringResource(R.string.office_tasks_issuance_help),
                enabled = !busy,
                modifier = Modifier.testTag(OFFICE_TASK_ISSUANCE_TAG),
                onClick = onMembershipIssuance,
            )
            if (actor.admin) MenuEntry(
                label = stringResource(R.string.office_tasks_personnel),
                help = stringResource(R.string.office_tasks_personnel_help),
                enabled = !busy,
                modifier = Modifier.testTag(OFFICE_TASK_PERSONNEL_TAG),
                onClick = onPersonnel,
            )
            if (taskIds.isEmpty() && pendingApprovals == 0 && pendingTodos == 0) {
                Text(
                    stringResource(scope.empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DetailSurface {
                    if (taskIds.isNotEmpty()) Text(
                        stringResource(R.string.workspace_dashboard_event_tasks, taskIds.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (pendingApprovals > 0) Text(
                        stringResource(R.string.workspace_dashboard_approval_tasks, pendingApprovals),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (pendingTodos > 0) Text(
                        stringResource(R.string.workspace_dashboard_todo_tasks, pendingTodos),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        },
        detail = {
        val totalActivities = allOrderedEventIds(state).size + standaloneLeagueIds(state).size
        val organization = state.optJSONObject("organization")
        val reportCount = organization?.optJSONObject("reports")?.length() ?: 0
        val resourceCount = organization?.optJSONObject("resources")?.length() ?: 0
        MenuEntry(
            label = stringResource(R.string.workspace_entry_activities),
            help = if (totalActivities > 0) stringResource(R.string.workspace_entry_activities_count, totalActivities)
            else stringResource(R.string.workspace_entry_activities_empty),
            enabled = !busy,
            modifier = Modifier.testTag(WORKSPACE_ACTIVITIES_TAG),
            onClick = onOpenActivities,
        )
        MenuEntry(
            label = stringResource(R.string.office_flows),
            help = if (pendingApprovals > 0) stringResource(R.string.workspace_entry_approvals_count, pendingApprovals)
            else stringResource(R.string.office_no_pending_approvals),
            enabled = !busy,
            modifier = Modifier.testTag(WORKSPACE_APPROVALS_TAG),
            onClick = onOpenApprovals,
        )
        MenuEntry(
            label = stringResource(R.string.workflow_reports),
            help = if (reportCount > 0) stringResource(R.string.workspace_entry_reports_count, reportCount)
            else stringResource(R.string.workflow_reports_empty),
            enabled = !busy,
            modifier = Modifier.testTag(WORKSPACE_REPORTS_TAG),
            onClick = onOpenReports,
        )
        MenuEntry(
            label = stringResource(R.string.workflow_resource_title),
            help = if (resourceCount > 0) stringResource(R.string.workspace_entry_resources_count, resourceCount)
            else stringResource(R.string.workspace_entry_resources_empty),
            enabled = !busy,
            modifier = Modifier.testTag(WORKSPACE_RESOURCES_TAG),
            onClick = onOpenResources,
        )
        },
    )
}

@Composable
fun ActivityCatalog(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onCreate: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenCompetition: (String) -> Unit,
) {
    val actor = officeActor(state, identity)
    if (state == null) {
        WorkspaceContainer(
            master = {
                ManagerCard(emphasized = false) {
                    Text(stringResource(R.string.office_connect))
                }
            },
            detail = {},
        )
        return
    }
    WorkspaceContainer(
        master = {
        WorkspaceRoleSummary(actor)
        if (actor.canManage) MenuEntry(
            label = stringResource(R.string.office_create),
            help = stringResource(R.string.office_tasks_create_help),
            enabled = !busy,
            modifier = Modifier.testTag(OFFICE_TASK_CREATE_TAG),
            onClick = onCreate,
        )
        },
        detail = {
        val events = state.optJSONObject("events") ?: JSONObject()
        val leagues = state.optJSONObject("leagues") ?: JSONObject()
        val orderedEvents = allOrderedEventIds(state)
        val linkedByEvent = eventLinkedCompetitions(state)
        val standaloneCompetitions = standaloneLeagueIds(state)
        if (orderedEvents.isEmpty() && standaloneCompetitions.isEmpty()) {
            ManagerCard(emphasized = false) {
                Text(
                    stringResource(R.string.office_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@WorkspaceContainer
        }
        ManagerCard {
            var rowIndex = 0
            orderedEvents.forEach { eventId ->
                val event = events.optJSONObject(eventId) ?: return@forEach
                val title = event.optString("title").ifBlank { eventId }
                val phase = event.optString("phase")
                val organizer = event.optString("organizer")
                val signedUp = actor.bound && phase == "open" &&
                    state.optJSONObject("signups")?.optJSONArray(eventId).containsString(actor.member)
                val linked = linkedByEvent[eventId].orEmpty()
                key("event:$eventId") {
                    if (rowIndex > 0) HorizontalDivider()
                    rowIndex++
                    Column(verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextButton(
                                onClick = { onOpenEvent(eventId) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET).testTag("event:$eventId"),
                            ) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
                                    Text(title, style = MaterialTheme.typography.titleMedium)
                                    if (organizer.isNotBlank()) {
                                        Text(
                                            stringResource(R.string.office_organizer, organizer),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            StatusPill(
                                text = stringResource(
                                    if (signedUp) R.string.office_tasks_state_signed else eventPhaseString(phase),
                                ),
                                active = phase in setOf("approved", "open"),
                            )
                        }
                        if (linked.isNotEmpty()) {
                            DetailSurface {
                                Text(
                                    stringResource(R.string.activity_linked_competitions),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                linked.forEach { comp ->
                                    key("event:$eventId:competition:${comp.leagueId}") {
                                        val (compStatus, compActive) = linkedCompetitionStatus(comp)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            TextButton(
                                                onClick = { onOpenCompetition(comp.leagueId) },
                                                enabled = !busy && comp.league != null,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = MIN_TOUCH_TARGET)
                                                    .testTag("competition:${comp.leagueId}"),
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(SPACING_XS),
                                                ) {
                                                    Text(comp.leagueId, style = MaterialTheme.typography.bodyMedium)
                                                    if (!comp.resourceTitle.isNullOrBlank() && comp.resourceTitle != comp.leagueId) {
                                                        Text(
                                                            comp.resourceTitle,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                            StatusPill(text = compStatus, active = compActive)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            standaloneCompetitions.forEach { leagueId ->
                val league = leagues.optJSONObject(leagueId) ?: return@forEach
                val (phaseLabel, phaseActive) = phaseStatus(league.optString("phase"))
                key("competition:$leagueId") {
                    if (rowIndex > 0) HorizontalDivider()
                    rowIndex++
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            onClick = { onOpenCompetition(leagueId) },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = MIN_TOUCH_TARGET)
                                .testTag("competition:$leagueId"),
                        ) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
                                Text(leagueId, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.activity_standalone_competition),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        StatusPill(text = phaseLabel, active = phaseActive)
                    }
                }
            }
        }
        },
    )
}

@Composable
fun ActivityCreateMenu(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onCreateDraft: () -> Unit,
    onCreateOnline: () -> Unit,
    onCreateMeeting: () -> Unit,
    onCreateCompetition: () -> Unit,
    onBack: () -> Unit,
) {
    val actor = officeActor(state, identity)
    TaskPage(stringResource(R.string.activity_create_title), busy, onBack) {
        if (state == null) {
            ManagerCard(
                title = stringResource(R.string.office_disconnected_title),
                helper = stringResource(R.string.office_disconnected_help),
                emphasized = false,
            ) {}
            return@TaskPage
        }
        WorkspaceRoleSummary(actor)
        ManagerCard(
            title = stringResource(R.string.activity_create_choose),
            helper = stringResource(R.string.activity_create_help),
            emphasized = true,
        ) {
            if (actor.canCreateDraft) MenuEntry(
                label = stringResource(R.string.office_create),
                help = stringResource(R.string.office_tasks_create_help),
                enabled = !busy,
                modifier = Modifier.testTag(ACTIVITY_CREATE_DRAFT_TAG),
                onClick = onCreateDraft,
            )
            if (actor.canManage) {
                MenuEntry(
                    label = stringResource(R.string.activity_create_online),
                    help = stringResource(R.string.office_workflow_online_help),
                    enabled = !busy,
                    modifier = Modifier.testTag(ACTIVITY_CREATE_ONLINE_TAG),
                    onClick = onCreateOnline,
                )
                MenuEntry(
                    label = stringResource(R.string.activity_create_meeting),
                    help = stringResource(R.string.office_workflow_meeting_help),
                    enabled = !busy,
                    modifier = Modifier.testTag(ACTIVITY_CREATE_MEETING_TAG),
                    onClick = onCreateMeeting,
                )
            }
            if (actor.admin) MenuEntry(
                label = stringResource(R.string.activity_create_competition),
                help = stringResource(R.string.activity_create_competition_help),
                enabled = !busy,
                modifier = Modifier.testTag(ACTIVITY_CREATE_COMPETITION_TAG),
                onClick = onCreateCompetition,
            )
            if (!actor.canCreateDraft) Text(
                stringResource(R.string.office_create_restricted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!actor.canManage) Text(
                stringResource(R.string.activity_create_restricted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actor.canManage && !actor.admin) Text(
                stringResource(R.string.activity_create_competition_restricted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Root Tasks opens the existing approval list; no second approval implementation. */
@Composable
fun TaskInbox(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onOpenFlow: (String) -> Unit,
) {
    TaskPage(stringResource(R.string.workspace_nav_tasks), busy) {
        if (state == null) {
            ManagerCard(
                title = stringResource(R.string.office_disconnected_title),
                helper = stringResource(R.string.office_disconnected_help),
                emphasized = false,
            ) {}
            return@TaskPage
        }
        WorkspaceRoleSummary(officeActor(state, identity))
        OfficeCard(state, busy, identity, approvals = true, showTitle = false, onOpenFlow = onOpenFlow)
    }
}

private fun eventPhaseString(phase: String): Int = when (phase) {
    "draft" -> R.string.office_phase_draft
    "review" -> R.string.office_phase_review
    "approved" -> R.string.office_phase_approved
    "open" -> R.string.office_phase_open
    "done" -> R.string.office_phase_done
    "cancelled" -> R.string.office_phase_cancelled
    else -> R.string.office_unknown
}

@Composable
private fun linkedCompetitionStatus(comp: LinkedCompetition): Pair<String, Boolean> {
    comp.league?.optString("phase")?.takeIf { it.isNotBlank() }?.let { return phaseStatus(it) }
    val game = comp.game
    return when {
        game != null && !game.isNull("results") -> stringResource(R.string.workflow_game_finished) to false
        game?.optBoolean("started") == true -> stringResource(R.string.workflow_game_started) to true
        else -> stringResource(R.string.workflow_game_waiting) to false
    }
}
