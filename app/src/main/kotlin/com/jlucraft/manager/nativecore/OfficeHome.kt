package com.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONObject

/**
 * Activity workflow home. It is a child task page below activity setup/details and reports/resources,
 * never a root destination or a second business selector.
 */
@Composable
internal fun ActivityWorkflowHome(
    state: JSONObject?,
    busy: Boolean,
    identity: String,
    focusedNotice: String?,
    onOpenKind: (String) -> Unit,
    onOpenEvent: (String, String) -> Unit,
    onOpenReports: () -> Unit,
    onOpenSetup: () -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.workflow_title), busy, onBack) {
        if (state == null) {
            ManagerCard(
                title = stringResource(R.string.office_disconnected_title),
                helper = stringResource(R.string.office_disconnected_help),
                emphasized = false,
            ) {}
            return@TaskPage
        }
        OfficeWorkflowHome(
            state = state,
            busy = busy,
            identity = identity,
            focusedNotice = focusedNotice,
            onOpenKind = onOpenKind,
            onOpenEvent = onOpenEvent,
            onOpenReports = onOpenReports,
            onOpenSetup = onOpenSetup,
        )
    }
}

/**
 * Workflow entry menu. Online and meeting preparation, reports and workflow configuration are
 * separate tasks; the five-step plan never starts on this page.
 */
@Composable
internal fun OfficeWorkflowHome(
    state: JSONObject,
    busy: Boolean,
    identity: String,
    focusedNotice: String?,
    onOpenKind: (String) -> Unit,
    onOpenEvent: (String, String) -> Unit,
    onOpenReports: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val organization = state.optJSONObject("organization") ?: JSONObject()
    val noticeEvent = focusedNotice?.let { organization.optJSONObject("notices")?.optJSONObject(it)?.optString("event") }
    val noticeKind = noticeEvent?.let { organization.optJSONObject("plans")?.optJSONObject(it)?.optString("kind") }
    if (!noticeEvent.isNullOrBlank()) MenuEntry(
        label = stringResource(R.string.office_workflow_notice_open),
        help = stringResource(R.string.office_workflow_notice_help),
        enabled = !busy,
        onClick = { onOpenEvent(noticeKind?.takeIf { it.isNotBlank() } ?: "online", noticeEvent) },
    )
    ManagerCard(
        title = stringResource(R.string.workflow_title),
        helper = stringResource(R.string.workflow_help),
        emphasized = false,
    ) {
        Text(stringResource(R.string.office_workflow_choose_kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    MenuEntry(
        label = stringResource(R.string.workflow_online),
        help = stringResource(R.string.office_workflow_online_help),
        enabled = !busy,
        onClick = { onOpenKind("online") },
    )
    MenuEntry(
        label = stringResource(R.string.workflow_meeting),
        help = stringResource(R.string.office_workflow_meeting_help),
        enabled = !busy,
        onClick = { onOpenKind("meeting") },
    )
    MenuEntry(
        label = stringResource(R.string.workflow_reports),
        enabled = !busy,
        onClick = onOpenReports,
    )
    MenuEntry(
        label = stringResource(R.string.workflow_setup),
        enabled = !busy,
        help = stringResource(R.string.office_workflow_setup_help),
        onClick = onOpenSetup,
    )
    if (identity.isBlank()) Text(
        stringResource(R.string.office_role_unbound),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Event list for one workflow kind; choosing an event opens its own planning page. */
@Composable
internal fun OfficeWorkflowEventsPage(
    state: JSONObject?,
    kind: String,
    busy: Boolean,
    onOpenEvent: (String) -> Unit,
    onBack: () -> Unit,
) {
    val events = state?.optJSONObject("events") ?: JSONObject()
    val plans = state?.optJSONObject("organization")?.optJSONObject("plans") ?: JSONObject()
    TaskPage(stringResource(if (kind == "meeting") R.string.workflow_meeting else R.string.workflow_online), busy, onBack) {
        if (events.length() == 0) Text(stringResource(R.string.office_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        events.keys().asSequence().toList().sorted().forEach { id ->
            val event = events.optJSONObject(id) ?: return@forEach
            val planKind = plans.optJSONObject(id)?.optString("kind")
            if (!planKind.isNullOrBlank() && planKind != kind) return@forEach
            key(id) {
                MenuEntry(
                    label = event.optString("title").ifBlank { id },
                    help = stringResource(
                        R.string.office_workflow_event_help,
                        stringResource(
                            when (event.optString("phase")) {
                                "draft" -> R.string.office_phase_draft
                                "review" -> R.string.office_phase_review
                                "approved" -> R.string.office_phase_approved
                                "open" -> R.string.office_phase_open
                                "done" -> R.string.office_phase_done
                                "cancelled" -> R.string.office_phase_cancelled
                                else -> R.string.office_unknown
                            },
                        ),
                    ),
                    enabled = !busy,
                    onClick = { onOpenEvent(id) },
                )
            }
        }
    }
}
