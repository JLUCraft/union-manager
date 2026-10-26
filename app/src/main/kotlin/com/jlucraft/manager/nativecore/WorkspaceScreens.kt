package com.jlucraft.manager.nativecore

import com.jlucraft.manager.R
import org.json.JSONObject

internal const val OFFICE_TASK_CREATE_TAG = "office_task_create"
internal const val OFFICE_TASK_ISSUANCE_TAG = "office_task_issuance"
internal const val OFFICE_TASK_PERSONNEL_TAG = "office_task_personnel"
internal const val WORKSPACE_ACTIVITIES_TAG = "workspace_entry_activities"
internal const val WORKSPACE_APPROVALS_TAG = "workspace_entry_approvals"
internal const val WORKSPACE_REPORTS_TAG = "workspace_entry_reports"
internal const val WORKSPACE_RESOURCES_TAG = "workspace_entry_resources"

internal fun officeTaskTag(eventId: String): String = "office_task:$eventId"

/** What the workspace task desk offers as actionable work for the signed-in role. */
internal enum class OfficeTaskScope(val title: Int, val helper: Int, val empty: Int) {
    President(R.string.office_tasks_title_president, R.string.office_tasks_scope_president, R.string.office_tasks_empty_president),
    PresidentAdmin(R.string.office_tasks_title_president_admin, R.string.office_tasks_scope_president_admin, R.string.office_tasks_empty_admin),
    ServerAdmin(R.string.office_tasks_title_admin, R.string.office_tasks_scope_admin, R.string.office_tasks_empty_admin),
    ClubManager(R.string.office_tasks_title_manager, R.string.office_tasks_scope_manager, R.string.office_tasks_empty_club),
    Member(R.string.office_tasks_title_member, R.string.office_tasks_scope_member, R.string.office_tasks_empty_member),
}

internal fun officeTaskScope(actor: OfficeActor): OfficeTaskScope = when {
    actor.president && actor.admin -> OfficeTaskScope.PresidentAdmin
    actor.president -> OfficeTaskScope.President
    actor.admin -> OfficeTaskScope.ServerAdmin
    actor.manager -> OfficeTaskScope.ClubManager
    else -> OfficeTaskScope.Member
}

/** Event ids in the activity list's own filter order, so the task desk and the list never disagree. */
internal fun officeEventIds(state: JSONObject?, actor: OfficeActor, filter: OfficeEventFilter): List<String> {
    val events = state?.optJSONObject("events") ?: return emptyList()
    return events.keys().asSequence().mapNotNull { eventId ->
        val event = events.optJSONObject(eventId) ?: return@mapNotNull null
        if (!officeEventMatchesFilter(event, actor, filter)) return@mapNotNull null
        eventId to event.optJSONObject("window")?.takeIf { it.has("opens_ms") }?.getLong("opens_ms")
    }.toList().let(::sortedEventIds)
}

/**
 * Drafts the actor may act on now: own club only for presidents and club managers, every club for
 * server administrators. Members get the formal activities they can open and sign up for.
 */
internal fun officeTaskEventIds(state: JSONObject?, actor: OfficeActor): List<String> =
    officeEventIds(
        state,
        actor,
        if (officeTaskScope(actor) == OfficeTaskScope.Member) OfficeEventFilter.Public else OfficeEventFilter.Todo,
    )

internal fun workspacePendingApprovalCount(state: JSONObject?, identity: String): Int {
    val flows = state?.optJSONObject("flows") ?: return 0
    return flows.keys().asSequence().count { flowId ->
        val flow = flows.optJSONObject(flowId) ?: return@count false
        officeFlowVisible(state, identity, flow) && officeFlowNeedsAttention(state, identity, flow)
    }
}

internal fun workspacePendingTodoCount(state: JSONObject?, actor: OfficeActor): Int {
    if (state == null) return 0
    val tasks = state.optJSONObject("tasks") ?: JSONObject()
    val notices = state.optJSONObject("organization")?.optJSONObject("notices") ?: JSONObject()
    val assignedTasks = if (!actor.bound) emptySet() else tasks.keys().asSequence().filter { taskId ->
        val task = tasks.optJSONObject(taskId) ?: return@filter false
        !task.optBoolean("done") && task.optString("assignee") == actor.member
    }.toSet()
    val unacknowledgedNotices = if (!actor.bound) emptySet() else notices.keys().asSequence().filter { noticeId ->
        val notice = notices.optJSONObject(noticeId) ?: return@filter false
        notice.optString("member") == actor.member && notice.isNull("acknowledged_ms")
    }.toSet()
    val unresolvedDisputes = if (!actor.admin) 0 else {
        val disputes = state.optJSONObject("disputes") ?: JSONObject()
        disputes.keys().asSequence().count { id ->
            disputes.optJSONObject(id)?.optJSONObject("resolution") == null
        }
    }
    return (assignedTasks + unacknowledgedNotices).size + unresolvedDisputes
}

internal data class LinkedCompetition(
    val leagueId: String,
    val resourceTitle: String?,
    val game: JSONObject?,
    val league: JSONObject?,
)

internal fun allOrderedEventIds(state: JSONObject?): List<String> {
    val events = state?.optJSONObject("events") ?: return emptyList()
    return events.keys().asSequence().mapNotNull { eventId ->
        val event = events.optJSONObject(eventId) ?: return@mapNotNull null
        eventId to event.optJSONObject("window")?.takeIf { it.has("opens_ms") }?.getLong("opens_ms")
    }.toList().let(::sortedEventIds)
}

internal fun eventLinkedCompetitions(state: JSONObject?): Map<String, List<LinkedCompetition>> {
    val events = state?.optJSONObject("events") ?: return emptyMap()
    val organization = state.optJSONObject("organization") ?: return emptyMap()
    val plans = organization.optJSONObject("plans") ?: return emptyMap()
    val resources = organization.optJSONObject("resources") ?: JSONObject()
    val leagues = state.optJSONObject("leagues") ?: JSONObject()
    return buildMap {
        events.keys().asSequence().forEach { eventId ->
            val games = plans.optJSONObject(eventId)?.optJSONObject("games") ?: return@forEach
            val linked = games.keys().asSequence().toList().sorted().mapNotNull { gameKey ->
                val gameObj = games.optJSONObject(gameKey) ?: return@mapNotNull null
                val leagueId = gameObj.optString("league").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val resourceKey = gameObj.optString("resource").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val resourceTitle = resources.optJSONObject(resourceKey)?.optString("title")?.takeIf { it.isNotBlank() }
                LinkedCompetition(
                    leagueId = leagueId,
                    resourceTitle = resourceTitle,
                    game = gameObj,
                    league = leagues.optJSONObject(leagueId),
                )
            }.distinctBy { it.leagueId }
            if (linked.isNotEmpty()) put(eventId, linked)
        }
    }
}

internal fun standaloneLeagueIds(state: JSONObject?): List<String> {
    val leagues = state?.optJSONObject("leagues") ?: return emptyList()
    val linkedLeagueIds = eventLinkedCompetitions(state).values.flatten().map { it.leagueId }.toSet()
    return leagues.keys().asSequence().filter { it !in linkedLeagueIds }.toList().sorted()
}
