package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

/**
 * Built-in business role ids the current schema names and an administrator may assign. The
 * president seat is derived from chair facts, so it is never offered as a business role.
 */
internal val PERSONNEL_KNOWN_ROLES = listOf("admin", "manager")

/** Roles projected read-only from identity facts, never written as business roles. */
internal val PERSONNEL_IDENTITY_ROLE_IDS = setOf("president")

/** Test hook for the personnel page's route into the identity record actions. */
internal const val PERSONNEL_TRANSFER_TAG = "personnel_transfer"

internal fun personnelMemberTag(member: String): String = "personnel_member:$member"

internal fun personnelRoleTag(role: String): String = "personnel_role:$role"

/** Roles currently published for [member]; never inferred from the office actor's own label. */
internal fun personnelRoles(state: JSONObject?, member: String): Set<String> {
    if (member.isBlank()) return emptySet()
    val roles = state?.optJSONObject("members")?.optJSONObject(member)?.optJSONArray("roles") ?: return emptySet()
    return (0 until roles.length()).mapNotNull { roles.optString(it).takeIf(String::isNotBlank) }.toSet()
}

/** Explicit choices: the built-in roles first, then every business role the snapshot already uses. */
internal fun personnelRoleChoices(state: JSONObject?, current: Set<String>): List<String> {
    val observed = officeMemberProfiles(state).flatMap { it.roles }.toSet()
    val business = (observed + current) - PERSONNEL_IDENTITY_ROLE_IDS
    return (PERSONNEL_KNOWN_ROLES + business.sorted()).distinct()
}

/** Build through the existing native builder so role validation stays server-owned. */
internal fun personnelRoleAction(member: String, roles: Collection<String>): JSONObject = JSONObject(
    UnionNative.management(
        JSONObject().put("operation", "build_roles").put("member", member).put("roles", JSONArray(roles.sorted())).toString(),
    ),
)

/** A missing target can never look like an accepted empty role set. */
internal fun personnelMemberExists(snapshot: JSONObject?, member: String): Boolean =
    member.isNotBlank() && snapshot?.optJSONObject("members")?.has(member) == true

/**
 * Roles granted by the unified identity log, not by the business role editor. A chair is projected
 * as president from the current identity facts; the editor must show that seat as read-only.
 */
internal fun personnelIdentityRoles(facts: JSONObject?, state: JSONObject?, member: String): Set<String> {
    if (!personnelMemberExists(state, member)) return emptySet()
    val club = state?.optJSONObject("members")?.optJSONObject(member)?.optString("club").orEmpty()
    val chairs = facts?.optJSONObject("chairs") ?: return emptySet()
    return if (club.isNotBlank() && chairs.optString(club) == member) setOf("president") else emptySet()
}

/** A role write is accepted only when the target still exists and shows exactly the selected roles. */
internal fun personnelRolesAccepted(snapshot: JSONObject?, member: String, roles: Set<String>): Boolean =
    personnelMemberExists(snapshot, member) && personnelRoles(snapshot, member) == roles

private val personnelDraftSaver = listSaver<PersonnelDraft, String>(
    save = {
        listOf(
            it.member,
            it.choices.joinToString("\n"),
            it.selected.sorted().joinToString("\n"),
            it.locked.sorted().joinToString("\n"),
        )
    },
    restore = { values ->
        PersonnelDraft().apply {
            restore(
                values[0],
                values[1].lines().filter { it.isNotBlank() },
                values[2].toRoleSet(),
                values[3].toRoleSet(),
            )
        }
    },
)

private fun String.toRoleSet(): Set<String> = lines().filter { it.isNotBlank() }.toSet()

/**
 * One member's pending role choice. Every current role starts selected, so opening a member and
 * changing one chip never discards the roles the administrator did not touch.
 */
internal class PersonnelDraft {
    var member by mutableStateOf("")
        private set
    var choices by mutableStateOf<List<String>>(emptyList())
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var locked by mutableStateOf<Set<String>>(emptySet())
        private set

    /** The roles the member holds right now, kept so the review page can show what it replaces. */
    var currentRoles by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Business roles to write: identity-derived seats stay projected, never persisted. */
    val businessRoles: Set<String>
        get() = selected - locked

    /** Selects one member: every current role starts selected and every observed role stays a choice. */
    fun open(state: JSONObject?, member: String, identityRoles: Set<String> = emptySet()) {
        val current = personnelRoles(state, member)
        this.member = member
        locked = identityRoles
        currentRoles = current
        choices = (personnelRoleChoices(state, current) + identityRoles).distinct()
        selected = current + identityRoles
    }

    fun restore(member: String, choices: List<String>, selected: Set<String>, locked: Set<String> = emptySet()) {
        this.member = member
        this.choices = choices
        this.selected = selected
        this.locked = locked
        // A restored draft reads its baseline from the snapshot again, so a review that survives a
        // configuration change still compares against the roles the member has.
        currentRoles = selected - locked
    }

    /** A changed chair record never leaves an old identity role selectable in the draft. */
    fun applyIdentityRoles(roles: Set<String>) {
        if (roles == locked) return
        val removed = locked - roles
        val added = roles - locked
        selected = (selected - removed) + added
        choices = ((choices - removed) + added).distinct()
        locked = roles
    }

    fun toggle(role: String) {
        if (role in locked) return
        selected = if (role in selected) selected - role else selected + role
    }
}

@Serializable internal data object PersonnelRoster : NavKey
@Serializable internal data object PersonnelEdit : NavKey
@Serializable internal data object PersonnelReview : NavKey

/**
 * Server administrator personnel pages: roster, one member's explicit role choices, then review.
 * The roster and every page re-read the snapshot, so losing the admin slot removes the forms at
 * once. The review page returns to the roster only after the accepted snapshot proves the exact
 * write; a failure keeps the same draft for a retry.
 */
@Composable
internal fun PersonnelFlowHost(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    send: FormSender,
    onBack: () -> Unit,
    facts: JSONObject? = null,
    /**
     * Opens the shared identity record action page: the founder seat moves through multi-signature
     * succession and a device through bind/revoke. The personnel page only offers the way in; the
     * actions themselves stay in [IdentityActionForm].
     */
    onOpenTransfer: () -> Unit = {},
) {
    val draft = rememberSaveable(saver = personnelDraftSaver) { PersonnelDraft() }
    val submission = rememberFormSubmission()
    val backStack = rememberNavBackStack(PersonnelRoster)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    val backToRoster: () -> Unit = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val identityRoles = personnelIdentityRoles(facts, state, draft.member)
    androidx.compose.runtime.LaunchedEffect(draft.member, identityRoles) {
        draft.applyIdentityRoles(identityRoles)
    }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<PersonnelRoster> {
                PersonnelRosterPage(state, identity, busy, onOpenTransfer = onOpenTransfer, onOpenMember = { member ->
                    // A different member starts a new draft; an old page's error must not follow it.
                    submission.clearFailure()
                    draft.open(state, member, personnelIdentityRoles(facts, state, member))
                    backStack.add(PersonnelEdit)
                }, onBack = pop)
            }
            entry<PersonnelEdit> {
                PersonnelEditPage(draft, state, identity, busy, onNext = { backStack.add(PersonnelReview) }, onBack = pop)
            }
            entry<PersonnelReview> {
                PersonnelReviewPage(
                    draft = draft,
                    state = state,
                    identity = identity,
                    busy = busy,
                    submission = submission,
                    send = send,
                    onAccepted = backToRoster,
                    onBack = pop,
                )
            }
        },
    )
}

@Composable
private fun PersonnelRosterPage(
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onOpenTransfer: () -> Unit,
    onOpenMember: (String) -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.personnel_title), busy, onBack) {
        personnelBlocked(state, identity)?.let { blocked ->
            Text(stringResource(blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        // The founder roster is fixed by the server's genesis, so a change of leadership is a
        // succession or a device move; this is the way in, not a second copy of that flow.
        MenuEntry(
            label = stringResource(R.string.personnel_transfer_open),
            enabled = !busy,
            modifier = Modifier.testTag(PERSONNEL_TRANSFER_TAG),
            onClick = onOpenTransfer,
        )
        val members = officeMemberProfiles(state)
        if (members.isEmpty()) {
            Text(
                stringResource(R.string.personnel_roster_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TaskPage
        }
        members.forEach { profile ->
            key(profile.id) {
                RosterRow(
                    label = profile.id,
                    help = if (profile.roles.isEmpty()) stringResource(R.string.office_role_member)
                    else personnelRoleText(profile.roles),
                    enabled = !busy,
                    modifier = Modifier.testTag(personnelMemberTag(profile.id)),
                    onClick = { onOpenMember(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun PersonnelEditPage(
    draft: PersonnelDraft,
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    TaskPage(stringResource(R.string.personnel_edit_title, draft.member), busy, onBack) {
        personnelBlocked(state, identity)?.let { blocked ->
            Text(stringResource(blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        StepProgress(step = 2, total = 3)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SPACING_S),
            verticalArrangement = Arrangement.spacedBy(SPACING_S),
        ) {
            draft.choices.forEach { role ->
                val label = officeRoleLabel(role)?.let { stringResource(it) } ?: role
                FilterChip(
                    selected = role in draft.selected,
                    enabled = !busy && role !in draft.locked,
                    onClick = { draft.toggle(role) },
                    label = { Text(label) },
                    modifier = Modifier.testTag(personnelRoleTag(role)).heightIn(min = MIN_TOUCH_TARGET),
                )
            }
        }
        Text(
            stringResource(
                R.string.personnel_selected,
                if (draft.selected.isEmpty()) stringResource(R.string.personnel_roles_empty)
                else personnelRoleText(draft.selected),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionButton(R.string.office_next, !busy && draft.member.isNotBlank()) { onNext() }
    }
}

@Composable
private fun PersonnelReviewPage(
    draft: PersonnelDraft,
    state: JSONObject?,
    identity: String,
    busy: Boolean,
    submission: FormSubmission,
    send: FormSender,
    onAccepted: () -> Unit,
    onBack: () -> Unit,
) {
    var invalid by remember { mutableStateOf(false) }
    TaskPage(stringResource(R.string.personnel_review_title), busy, onBack) {
        personnelBlocked(state, identity)?.let { blocked ->
            Text(stringResource(blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@TaskPage
        }
        StepProgress(step = 3, total = 3)
        ManagerCard(
            title = draft.member,
            helper = stringResource(R.string.personnel_review_help),
            emphasized = false,
        ) {
            // The review must show what changes, not only the outcome: a replaced role set is
            // unreadable without the role it replaces.
            val before = personnelRoleText(draft.currentRoles)
            val after = if (draft.selected.isEmpty()) stringResource(R.string.personnel_roles_empty)
            else personnelRoleText(draft.selected)
            ChangeCompareRow(
                label = stringResource(R.string.personnel_review_compare),
                before = before,
                after = after,
                changed = draft.currentRoles != draft.selected,
            )
        }
        val memberPresent = personnelMemberExists(state, draft.member)
        if (!memberPresent) Text(
            stringResource(R.string.personnel_member_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        HorizontalDivider()
        if (submission.pending) {
            Text(
                stringResource(R.string.personnel_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (invalid) Text(stringResource(R.string.personnel_invalid), color = MaterialTheme.colorScheme.error)
        ActionButton(
            R.string.personnel_submit,
            !busy && !submission.pending && memberPresent && draft.member.isNotBlank(),
            kind = ActionKind.Confirmed,
            confirmText = R.string.personnel_submit_confirm,
        ) {
            // The submitted member and role set are captured here; a later edit cannot make a
            // different snapshot look like the accepted write. The snapshot proves the projected
            // roles (identity-derived seats included), while the action holds business roles only.
            val member = draft.member
            val roles = draft.selected
            val action = runCatching { personnelRoleAction(member, draft.businessRoles) }.getOrNull()
            if (action == null) {
                invalid = true
                return@ActionButton
            }
            invalid = false
            // The roster returns only for the accepted snapshot that shows exactly these roles.
            submission.submit(send, action) { snapshot ->
                val accepted = personnelRolesAccepted(snapshot, member, roles)
                if (accepted) onAccepted()
                accepted
            }
        }
        submission.failure?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** The page's own rule: disconnected first, then the server-admin slot from the snapshot. */
private fun personnelBlocked(state: JSONObject?, identity: String): Int? = when {
    state == null -> R.string.personnel_disconnected
    !officeActor(state, identity).admin -> R.string.personnel_restricted
    else -> null
}

@Composable
private fun personnelRoleText(roles: Collection<String>): String =
    roles.sorted().map { role -> officeRoleLabel(role)?.let { stringResource(it) } ?: role }
        .joinToString(stringResource(R.string.governance_separator))
