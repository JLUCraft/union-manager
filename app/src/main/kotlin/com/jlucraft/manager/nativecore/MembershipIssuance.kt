package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.manager.R
import kotlinx.serialization.Serializable
import org.json.JSONObject

/** Outcome of the shared identity proposal flow; only a submitted record counts as success. */
internal enum class ProposalOutcome { Submitted, Failed }

/** Member IDs that a club already has, from the verified identity facts. */
internal fun membershipClubMembers(facts: JSONObject?, club: String): List<String> {
    if (club.isBlank()) return emptyList()
    val memberships = facts?.optJSONArray("memberships") ?: return emptyList()
    return (0 until memberships.length()).mapNotNull { index ->
        val pair = memberships.optJSONArray(index) ?: return@mapNotNull null
        pair.optString(1).takeIf { it == club }?.let { pair.optString(0).takeIf(String::isNotBlank) }
    }.distinct()
}

/** Every identity already known to the verified facts; a new member is any ID outside this list. */
internal fun knownMemberIds(facts: JSONObject?): List<String> =
    facts?.optJSONObject("members")?.keys()?.asSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()

/** A new identity must bring the applicant's device; an existing identity binds nothing unless asked. */
internal fun membershipDeviceRequired(member: String, knownMembers: List<String>, bindExistingDevice: Boolean): Boolean =
    member.isNotBlank() && (member.trim() !in knownMembers || bindExistingDevice)

internal fun canDraftMembership(
    member: String, club: String, device: String, knownMembers: List<String>, bindExistingDevice: Boolean,
): Boolean =
    member.isNotBlank() && club.isNotBlank() &&
        (!membershipDeviceRequired(member, knownMembers, bindExistingDevice) || device.isNotBlank())

/** Why a pasted record cannot be signed in the chair's manual membership business. */
internal enum class MembershipIssuanceIssue { None, InvalidRecord, NotManualIssuance, OtherClub, OtherLedger }

/** The identity ledger currently verified for this configuration, used to scope imported records. */
internal fun identityLedgerGenesis(config: String, facts: JSONObject?): String? =
    facts?.optJSONObject("checkpoint")?.optString("genesis")?.takeIf(String::isNotBlank)
        ?: runCatching { JSONObject(config).optString("genesis_hash") }.getOrNull()?.takeIf(String::isNotBlank)

/** Manual member confirmations for a current chair club and the current identity ledger only. */
internal fun membershipIssuanceIssue(proposal: String, chairs: Set<String>, genesis: String?): MembershipIssuanceIssue {
    if (proposal.isBlank()) return MembershipIssuanceIssue.None
    val record = runCatching { JSONObject(proposal) }.getOrNull() ?: return MembershipIssuanceIssue.InvalidRecord
    val action = record.optJSONObject("action") ?: return MembershipIssuanceIssue.InvalidRecord
    if (action.optString("type") != "confirm_member" || action.optString("evidence") != "manual" ||
        !action.isNull("verifier")
    ) return MembershipIssuanceIssue.NotManualIssuance
    if (action.optString("club") !in chairs) return MembershipIssuanceIssue.OtherClub
    if (genesis == null || record.optString("genesis") != genesis) return MembershipIssuanceIssue.OtherLedger
    return MembershipIssuanceIssue.None
}

/** Fields for the shared confirm_member builder; the device key is absent when nothing is bound. */
internal fun membershipFields(member: String, club: String, device: String?): JSONObject =
    JSONObject().put("member", member.trim()).put("club", club.trim()).apply {
        if (!device.isNullOrBlank()) put("device", device.trim())
    }

/** Build through the existing native action builder so evidence/verifier stay protocol-owned. */
internal fun confirmMemberAction(fields: JSONObject, build: (String) -> String = { UnionNative.management(it) }): JSONObject =
    JSONObject(build(JSONObject().put("operation", "build_action").put("action", "confirm_member").put("fields", fields).toString()))

@Serializable internal data object IssuanceHome : NavKey
/** The issuance wizard's steps in order, shared by the wide outline and the progress bar. */
private val ISSUANCE_STEPS = listOf(
    R.string.identity_issuance_step_roster,
    R.string.identity_issuance_step_target,
    R.string.identity_issuance_review_title,
)

@Serializable internal data object IssuanceRoster : NavKey
@Serializable internal data object IssuanceTarget : NavKey
@Serializable internal data object IssuanceReview : NavKey
@Serializable internal data object IssuanceImport : NavKey

private val issuanceFormSaver = androidx.compose.runtime.saveable.listSaver<IssuanceForm, Any>(
    save = { listOf(it.club, it.member, it.device, it.bindExistingDevice) },
    restore = { values ->
        IssuanceForm().apply {
            club = values[0] as String
            member = values[1] as String
            device = values[2] as String
            bindExistingDevice = values[3] as Boolean
        }
    },
)

/** Fields that must survive the move between the target page and the review page. */
internal class IssuanceForm {
    var club by mutableStateOf("")
    var member by mutableStateOf("")
    var device by mutableStateOf("")
    var bindExistingDevice by mutableStateOf(false)
}

/**
 * Chair issuance as explicit pages: roster, target, review/sign. An office president role alone
 * never grants issuance; only the verified chair set selects the clubs, so losing the seat
 * removes the write pages on the next sync.
 */
@Composable
internal fun IssuanceFlowHost(
    ops: IdentityOps,
    busy: Boolean,
    current: Boolean,
    configured: Boolean,
    facts: JSONObject?,
    knownMembers: List<String>,
    onBack: () -> Unit,
) {
    val chairs = ops.state.member?.chairOf.orEmpty()
    val orderedChairs = remember(chairs) { chairs.sorted() }
    val genesis = identityLedgerGenesis(ops.config, facts)
    val form = androidx.compose.runtime.saveable.rememberSaveable(ops.config, ops.device, saver = issuanceFormSaver) { IssuanceForm() }
    val backStack = rememberNavBackStack(IssuanceHome)
    // A changed chair set removes every write page instead of keeping an old form actionable.
    // A transient syncing identity only disables the buttons (current=false) and never pops a page,
    // and the draft record itself stays until an explicit reset.
    var previousChairs by remember { mutableStateOf(orderedChairs) }
    LaunchedEffect(orderedChairs) {
        if (form.club !in orderedChairs) form.club = orderedChairs.firstOrNull().orEmpty()
        if (previousChairs != orderedChairs) {
            previousChairs = orderedChairs
            if (backStack.size > 1) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }
        }
    }
    val pop: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack()
    }
    NavDisplay(
        backStack = backStack,
        onBack = { pop() },
        entryProvider = entryProvider {
            entry<IssuanceHome> {
                IssuanceHomePage(
                    ops = ops,
                    form = form,
                    chairs = orderedChairs,
                    current = current,
                    configured = configured,
                    busy = busy,
                    onOpenRoster = { backStack.add(IssuanceRoster) },
                    onOpenTarget = { backStack.add(IssuanceTarget) },
                    onOpenImport = { backStack.add(IssuanceImport) },
                    onBack = pop,
                )
            }
            entry<IssuanceRoster> { IssuanceRosterPage(facts, form.club, busy, onBack = pop) }
            entry<IssuanceTarget> {
                IssuanceTargetPage(
                    ops = ops,
                    form = form,
                    knownMembers = knownMembers,
                    current = current,
                    configured = configured,
                    busy = busy,
                    onDrafted = { backStack.add(IssuanceReview) },
                    onBack = pop,
                )
            }
            entry<IssuanceReview> {
                IssuanceReviewPage(
                    ops = ops,
                    chairs = chairs,
                    genesis = genesis,
                    current = current,
                    configured = configured,
                    busy = busy,
                    onBack = pop,
                )
            }
            entry<IssuanceImport> {
                IssuanceImportPage(
                    ops = ops,
                    chairs = chairs,
                    genesis = genesis,
                    current = current,
                    configured = configured,
                    busy = busy,
                    onBack = pop,
                )
            }
        },
    )
}

@Composable
private fun IssuanceHomePage(
    ops: IdentityOps,
    form: IssuanceForm,
    chairs: List<String>,
    current: Boolean,
    configured: Boolean,
    busy: Boolean,
    onOpenRoster: () -> Unit,
    onOpenTarget: () -> Unit,
    onOpenImport: () -> Unit,
    onBack: () -> Unit,
) {
    val editable = current && configured && !busy
    TaskPage(stringResource(R.string.identity_issuance_title), busy, onBack) {
        Text(
            stringResource(R.string.identity_issuance_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!configured) Text(stringResource(R.string.identity_issuance_unconfigured), style = MaterialTheme.typography.bodyMedium)
        else if (!current) Text(stringResource(R.string.identity_issuance_not_current), style = MaterialTheme.typography.bodyMedium)
        if (chairs.isEmpty()) {
            Text(stringResource(R.string.identity_issuance_no_chair), style = MaterialTheme.typography.bodyMedium)
            if (current) Text(
                stringResource(R.string.identity_issuance_member_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TaskPage
        }
        if (!current) Text(
            stringResource(R.string.identity_issuance_cached),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.identity_issuance_club), style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SPACING_S),
            verticalArrangement = Arrangement.spacedBy(SPACING_S),
        ) {
            chairs.forEach { club ->
                FilterChip(
                    selected = form.club == club,
                    enabled = !busy,
                    onClick = { form.club = club },
                    label = { Text(club) },
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                )
            }
        }
        MenuEntry(
            label = stringResource(R.string.identity_issuance_open_roster),
            help = stringResource(R.string.identity_issuance_open_roster_help),
            enabled = !busy,
            onClick = onOpenRoster,
        )
        MenuEntry(
            label = stringResource(R.string.identity_issuance_open_target),
            help = stringResource(R.string.identity_issuance_open_target_help),
            enabled = editable,
            onClick = onOpenTarget,
        )
        MenuEntry(
            label = stringResource(R.string.identity_issuance_open_import),
            help = stringResource(R.string.identity_issuance_open_import_help),
            enabled = !busy,
            onClick = onOpenImport,
        )
    }
}

@Composable
private fun IssuanceRosterPage(facts: JSONObject?, club: String, busy: Boolean, onBack: () -> Unit) {
    val roster = remember(facts, club) { membershipClubMembers(facts, club) }
    TaskPage(stringResource(R.string.identity_issuance_roster_title, club), busy, onBack) {
        if (roster.isEmpty()) Text(
            stringResource(R.string.identity_issuance_roster_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ) else roster.forEach { id ->
            key(id) { RosterRow(label = id) }
        }
    }
}

@Composable
private fun IssuanceTargetPage(
    ops: IdentityOps,
    form: IssuanceForm,
    knownMembers: List<String>,
    current: Boolean,
    configured: Boolean,
    busy: Boolean,
    onDrafted: () -> Unit,
    onBack: () -> Unit,
) {
    val editable = current && configured && !busy
    var invalid by remember(form.club, form.member, form.device, form.bindExistingDevice) { mutableStateOf(false) }
    var choosingMember by remember { mutableStateOf(false) }
    val existing = form.member.trim() in knownMembers
    val deviceRequired = membershipDeviceRequired(form.member, knownMembers, form.bindExistingDevice)
    val draftable = editable && form.club in (ops.state.member?.chairOf.orEmpty()) &&
        canDraftMembership(form.member, form.club, form.device, knownMembers, form.bindExistingDevice)
    TaskPage(stringResource(R.string.identity_issuance_target_title, form.club), busy, onBack, steps = ISSUANCE_STEPS, currentStep = 2) {
        StepProgress(step = 2, total = 3)
        OutlinedTextField(
            value = form.member,
            onValueChange = { form.member = it; invalid = false },
            label = { Text(stringResource(R.string.identity_field_member)) },
            enabled = editable,
            singleLine = true,
            supportingText = { Text(stringResource(R.string.identity_issuance_member_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (knownMembers.isNotEmpty()) {
            Box {
                OutlinedButton(
                    enabled = editable,
                    onClick = { choosingMember = true },
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.identity_issuance_known)) }
                DropdownMenu(expanded = choosingMember, onDismissRequest = { choosingMember = false }) {
                    knownMembers.forEach { id ->
                        DropdownMenuItem(text = { Text(id) }, onClick = {
                            form.member = id
                            choosingMember = false
                            invalid = false
                        })
                    }
                }
            }
        }
        Text(
            stringResource(if (existing) R.string.identity_issuance_existing else R.string.identity_issuance_new),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (existing) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
                    .toggleable(value = form.bindExistingDevice, enabled = editable, role = Role.Checkbox,
                        onValueChange = { form.bindExistingDevice = it; invalid = false }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = form.bindExistingDevice, onCheckedChange = null, enabled = editable)
                Text(stringResource(R.string.identity_issuance_bind_device))
            }
        }
        if (deviceRequired) {
            OutlinedTextField(
                value = form.device,
                onValueChange = { form.device = it; invalid = false },
                label = { Text(stringResource(R.string.identity_issuance_device)) },
                enabled = editable,
                singleLine = true,
                supportingText = { Text(stringResource(R.string.identity_issuance_device_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (invalid) Text(stringResource(R.string.identity_issuance_invalid), color = MaterialTheme.colorScheme.error)
        ActionButton(R.string.identity_issuance_draft, draftable) {
            try {
                ops.run("draft", confirmMemberAction(membershipFields(form.member, form.club, form.device.takeIf { deviceRequired })))
                invalid = false
                onDrafted()
            } catch (_: Exception) {
                invalid = true
            }
        }
        TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.workflow_back))
        }
    }
}

@Composable
private fun IssuanceReviewPage(
    ops: IdentityOps,
    chairs: Set<String>,
    genesis: String?,
    current: Boolean,
    configured: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
) {
    val editable = current && configured && !busy
    val issue = membershipIssuanceIssue(ops.proposal, chairs, genesis)
    TaskPage(stringResource(R.string.identity_issuance_review_title), busy, onBack, steps = ISSUANCE_STEPS, currentStep = 3) {
        StepProgress(step = 3, total = 3)
        when {
            ops.proposal.isBlank() && ops.proposalInvalid -> Text(
                stringResource(R.string.identity_issuance_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            ops.proposal.isBlank() -> {
                Text(stringResource(R.string.identity_issuance_waiting), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            issue != MembershipIssuanceIssue.None -> Text(
                stringResource(issuanceIssueText(issue)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> {
                ProposalSummary(ops.proposal)
                when (ops.outcome) {
                    ProposalOutcome.Submitted -> Text(stringResource(R.string.identity_issuance_submitted), color = MaterialTheme.colorScheme.primary)
                    ProposalOutcome.Failed -> Text(stringResource(R.string.identity_issuance_failed), color = MaterialTheme.colorScheme.error)
                    null -> Unit
                }
                SignSubmitActions(
                    signed = proposalSignatureCount(ops.proposal) > 0,
                    ready = editable,
                    onSign = { ops.run("sign") },
                    onSubmit = { ops.run("submit") },
                    confirmText = R.string.identity_issuance_submit_confirm,
                )
                PublicJsonActions(ops.proposal, R.string.identity_issuance_record, editable)
                TextButton(
                    onClick = { ops.clearProposal() },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.identity_issuance_reset)) }
            }
        }
    }
}

@Composable
private fun IssuanceImportPage(
    ops: IdentityOps,
    chairs: Set<String>,
    genesis: String?,
    current: Boolean,
    configured: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
) {
    val editable = current && configured && !busy
    val issue = membershipIssuanceIssue(ops.proposal, chairs, genesis)
    TaskPage(
        title = stringResource(R.string.identity_issuance_import_title),
        busy = busy,
        onBack = onBack,
        bottomBar = {
            if (ops.proposal.isNotBlank() && issue == MembershipIssuanceIssue.None) BottomActionBar {
                SignSubmitActions(
                    signed = proposalSignatureCount(ops.proposal) > 0,
                    ready = editable,
                    onSign = { ops.run("sign") },
                    onSubmit = { ops.run("submit") },
                    confirmText = R.string.identity_issuance_submit_confirm,
                )
            }
        },
    ) {
        Text(
            stringResource(R.string.identity_issuance_import_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProposalDetails(ops, busy, R.string.identity_issuance_import)
        if (ops.proposal.isNotBlank() && issue != MembershipIssuanceIssue.None) {
            Text(
                stringResource(issuanceIssueText(issue)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (ops.proposal.isNotBlank() && issue == MembershipIssuanceIssue.None) {
            HorizontalDivider()
            when (ops.outcome) {
                ProposalOutcome.Submitted -> Text(stringResource(R.string.identity_issuance_submitted), color = MaterialTheme.colorScheme.primary)
                ProposalOutcome.Failed -> Text(stringResource(R.string.identity_issuance_failed), color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            // The sign/submit pair lives in the pinned bar above; this page's body keeps only the
            // outcome, the copy/share actions and the reset link.
            PublicJsonActions(ops.proposal, R.string.identity_issuance_record, editable)
            TextButton(
                onClick = { ops.clearProposal() },
                enabled = !busy,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(R.string.identity_issuance_reset)) }
        }
    }
}

private fun issuanceIssueText(issue: MembershipIssuanceIssue): Int = when (issue) {
    MembershipIssuanceIssue.InvalidRecord -> R.string.identity_proposal_invalid
    MembershipIssuanceIssue.NotManualIssuance -> R.string.identity_issuance_import_not_member
    MembershipIssuanceIssue.OtherClub -> R.string.identity_issuance_import_other_club
    MembershipIssuanceIssue.OtherLedger -> R.string.identity_issuance_import_other_ledger
    MembershipIssuanceIssue.None -> R.string.identity_issuance_invalid
}
