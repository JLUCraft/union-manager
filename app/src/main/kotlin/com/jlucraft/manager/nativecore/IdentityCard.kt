package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun identityConfig(text: String, directory: File): JSONObject {
    val config = JSONObject(text)
    val pin = config.getString("genesis_hash")
    require(GENESIS_HASH_PATTERN.matches(pin))
    require(config.getString("address").isNotBlank() && config.getString("peer").isNotBlank())
    config.getJSONObject("genesis")
    require(directory.isDirectory || directory.mkdirs())
    return config.put("log", File(directory, "$pin.jsonl").absolutePath)
}

internal fun identityMember(state: JSONObject, device: String): MemberIdentityView? {
    val members = state.getJSONObject("members")
    val id = members.keys().asSequence().firstOrNull { id ->
        device in members.getJSONArray(id).strings()
    } ?: return null
    val clubs = state.getJSONArray("memberships").let { values ->
        (0 until values.length()).map { values.getJSONArray(it) }.filter { it.getString(0) == id }.map { it.getString(1) }.toSet()
    }
    val chairs = state.getJSONObject("chairs")
    val permissions = state.getJSONObject("permissions")
    return MemberIdentityView(id, members.getJSONArray(id).strings().toSet(), clubs,
        chairs.keys().asSequence().filter { chairs.getString(it) == id }.toSet(),
        permissions.keys().asSequence().map { permissions.getJSONObject(it) }
            .filter { it.getString("member") == id && !it.getBoolean("revoked") }
            .map { "${it.getString("service")}: ${it.getString("permission")}" }.toSet())
}

/**
 * One shared view over the identity operations. It owns the verified state, records and the
 * draft record, so dedicated pages can be navigated without copying or re-deriving any rule.
 * [onStateChange] is the single bridge to the startup gate; pages only render and call here.
 */
internal class IdentityOps(
    val config: String,
    val device: String,
    private val directory: File,
    private val review: (String) -> String,
    private val execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
    private val onConfig: (String) -> Unit,
    private val onStateChange: (IdentityState, JSONObject?) -> Unit,
    private val onBusinessGeneses: (JSONObject) -> Unit,
    private val onFetchFailed: () -> Unit,
    initialState: IdentityState,
    initialFacts: JSONObject?,
    proposalState: MutableState<String> = mutableStateOf(""),
) {
    var state by mutableStateOf(initialState)
    var facts by mutableStateOf(initialFacts)
    var proposal by proposalState
    var proposalInvalid by mutableStateOf(false)
    var outcome by mutableStateOf<ProposalOutcome?>(null)

    fun editProposal(value: String) {
        proposal = value
        proposalInvalid = false
        outcome = null
    }

    fun clearProposal() {
        proposal = ""
        proposalInvalid = false
        outcome = null
    }

    /** Review pasted material without rewriting it; only a record with an action is signable. */
    fun reviewProposal(): Boolean {
        proposalInvalid = runCatching { JSONObject(proposal).optJSONObject("action") != null }
            .getOrDefault(false).not()
        outcome = null
        return !proposalInvalid
    }

    /** Sync and inspect decide whether the verified identity still holds; record writes never revoke it. */
    fun run(operation: String, action: JSONObject? = null) {
        val previous = state
        val identityCheck = isIdentityCheck(operation)
        try {
            val input = JSONObject().put("config", identityConfig(config, directory)).put("operation", operation)
            if (operation == "draft") input.put("action", action ?: JSONObject(proposal))
            if (operation == "sign" || operation == "sign_offline" || operation == "submit") input.put("record", JSONObject(proposal))
            state = state.syncing()
            proposalInvalid = false
            execute(input) { result ->
                if (result.has("error")) {
                    state = identityStateAfterFailure(operation, state, previous)
                    if (identityCheck) onStateChange(state, facts)
                    if (operation == "submit") outcome = ProposalOutcome.Failed
                } else if (operation == "draft" || operation == "sign" || operation == "sign_offline") {
                    proposal = result.toString(2)
                    state = previous
                    outcome = null
                } else {
                    try {
                        val next = state.received(identityMember(result, device), operation != "inspect")
                        state = next
                        facts = result
                        if (operation == "sync" || operation == "submit") onStateChange(next, facts)
                        if (operation == "submit") outcome = ProposalOutcome.Submitted
                    } catch (_: Exception) {
                        state = identityStateAfterFailure(operation, state, previous)
                        if (identityCheck) onStateChange(state, facts)
                        if (operation == "submit") outcome = ProposalOutcome.Failed
                    }
                }
            }
        } catch (_: Exception) {
            proposalInvalid = true
            state = identityStateAfterFailure(operation, state, previous)
            if (identityCheck) onStateChange(state, facts)
            if (operation == "submit") outcome = ProposalOutcome.Failed
        }
    }

    /** Review pasted material through the shared native configuration path. */
    fun saveConfiguration(text: String): Boolean = try {
        val verified = review(JSONObject().put("operation", "review_configuration").put("config", JSONObject(text)).toString())
        onConfig(identityConfig(verified, directory).toString())
        true
    } catch (_: Exception) {
        false
    }

    /** Trust a fetched genesis only after the operator confirms it; the business pins follow. */
    fun confirmFetched(result: JSONObject, address: String, peer: String): Boolean = try {
        val fetchedIdentity = result.getJSONObject("identity")
        val candidate = JSONObject()
            .put("genesis", fetchedIdentity.getJSONObject("genesis"))
            .put("genesis_hash", fetchedIdentity.getString("genesis_hash"))
            .put("address", address)
            .put("peer", peer)
        val verified = review(JSONObject().put("operation", "review_configuration").put("config", candidate).toString())
        onConfig(identityConfig(verified, directory).toString())
        val applicationPins = JSONObject()
        result.optJSONObject("application")?.optString("genesis_hash")
            ?.takeIf { GENESIS_HASH_PATTERN.matches(it) }
            ?.let { applicationPins.put("application", JSONObject().put("genesis_hash", it)) }
        onBusinessGeneses(applicationPins)
        true
    } catch (_: Exception) {
        onFetchFailed()
        false
    }
}

/**
 * One centered single-task page body with the shared top bar and back control.
 *
 * [bottomBar] pins the page's advance/submit action below the scrolling body, so a long form or an
 * open software keyboard can never push the primary action off screen.
 */
@Composable
internal fun TaskPage(
    title: String,
    busy: Boolean,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    steps: List<Int>? = null,
    currentStep: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    ManagerScreenScaffold(
        title = title,
        busy = busy,
        navigationIcon = { if (onBack != null) BackIcon(onBack) },
        bottomBar = bottomBar,
    ) {
        if (!scrollable) {
            // The page body owns its own panes and scrolling (see MasterDetailPage).
            // A Column keeps the ColumnScope receiver the page content is written against.
            Column(Modifier.fillMaxSize()) { content() }
            return@ManagerScreenScaffold
        }
        if (steps != null && isWideLayout()) {
            // Multi-step page on an expanded window: the outline stays in view beside the editor
            // while the editor scrolls on its own.
            Row(Modifier.fillMaxSize()) {
                PagePane(Modifier.width(WIDE_LEADING_PANE_WIDTH)) {
                    StepOutline(current = currentStep, steps = steps)
                }
                PagePane(Modifier.weight(1f), content = content)
            }
            return@ManagerScreenScaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(SPACING_L),
                verticalArrangement = Arrangement.spacedBy(SPACING_L),
                content = content,
            )
        }
    }
}

@Composable
private fun identityAvailabilityText(state: IdentityState): String = stringResource(
    when (state.availability) {
        IdentityAvailability.Unconfigured -> R.string.identity_empty
        IdentityAvailability.Syncing -> R.string.identity_syncing
        IdentityAvailability.Cached -> R.string.identity_cached
        IdentityAvailability.Current -> R.string.identity_current
        IdentityAvailability.Unavailable -> R.string.identity_state_unavailable
    },
)

/**
 * First identity page. A member has one primary task (import the material the chair gave them)
 * and two secondary ways to get it; every engineering field lives on its own page.
 */
@Composable
internal fun IdentityHomePage(
    ops: IdentityOps,
    state: IdentityState,
    busy: Boolean,
    publicIdentity: String,
    onOpenImport: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenEnrollment: () -> Unit,
) {
    val configured = ops.config.isNotBlank()
    val unbound = configured &&
        (state.availability == IdentityAvailability.Current || state.availability == IdentityAvailability.Cached) &&
        (state.member == null || state.member.clubs.isEmpty())
    val verifying = configured &&
        (state.availability == IdentityAvailability.Unconfigured || state.availability == IdentityAvailability.Syncing)
    TaskPage(
        title = stringResource(if (configured) R.string.startup_identity_verify_title else R.string.startup_identity_join_title),
        busy = busy,
    ) {
        StepProgress(step = 1, total = 3)
        if (!configured) {
            ManagerCard(
                helper = stringResource(R.string.startup_identity_join_helper),
                emphasized = false,
            ) {
                Text(stringResource(R.string.startup_identity_join_body), style = MaterialTheme.typography.bodyMedium)
                ActionButton(R.string.startup_identity_import_open, !busy) { onOpenImport() }
                ActionButton(R.string.startup_identity_find_org, !busy, kind = ActionKind.Secondary) { onOpenDiscover() }
                ActionButton(R.string.startup_identity_share_open, !busy, kind = ActionKind.Secondary) { onOpenShare() }
            }
        } else {
            val status = when {
                verifying -> R.string.startup_identity_verifying
                state.availability == IdentityAvailability.Unavailable -> R.string.startup_identity_unavailable_status
                unbound -> R.string.startup_identity_unbound_status
                state.availability == IdentityAvailability.Cached -> R.string.startup_identity_cached_status
                else -> R.string.identity_current
            }
            ManagerCard(
                helper = stringResource(R.string.startup_identity_verify_helper),
                statusText = stringResource(status),
                statusActive = state.isVerifiedIdentity(),
            ) {
                when {
                    verifying -> {
                        Text(stringResource(R.string.startup_identity_verifying_body), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.availability == IdentityAvailability.Unavailable -> {
                        Text(stringResource(R.string.startup_identity_unavailable_body), style = MaterialTheme.typography.bodyMedium)
                        ActionButton(R.string.startup_identity_retry, !busy) { ops.run("sync") }
                        ActionButton(R.string.startup_identity_change_data, !busy, kind = ActionKind.Secondary) { onOpenImport() }
                    }
                    unbound -> {
                        Text(stringResource(R.string.startup_identity_unbound_body), style = MaterialTheme.typography.bodyMedium)
                        ActionButton(R.string.startup_identity_share_open, !busy, kind = ActionKind.Secondary) { onOpenShare() }
                        ActionButton(R.string.startup_identity_enrollment_open, !busy, kind = ActionKind.Secondary) { onOpenEnrollment() }
                        TextButton(onClick = { ops.run("sync") }, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                            Text(stringResource(R.string.startup_identity_retry))
                        }
                        TextButton(onClick = onOpenImport, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                            Text(stringResource(R.string.startup_identity_change_data))
                        }
                    }
                    state.availability == IdentityAvailability.Cached -> {
                        Text(stringResource(R.string.startup_identity_cached_body), style = MaterialTheme.typography.bodyMedium)
                        ActionButton(R.string.startup_identity_retry, !busy) { ops.run("sync") }
                        ActionButton(R.string.startup_identity_change_data, !busy, kind = ActionKind.Secondary) { onOpenImport() }
                    }
                    else -> {
                        Text(stringResource(R.string.identity_current), style = MaterialTheme.typography.bodyMedium)
                        state.member?.clubs?.takeIf { it.isNotEmpty() }?.let { clubs ->
                            Text(
                                stringResource(R.string.startup_identity_clubs, clubs.joinToString(stringResource(R.string.governance_separator))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.startup_identity_verified_next),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (publicIdentity.isNotBlank()) Text(
            stringResource(R.string.startup_identity_share_entry_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Account summary: the member facts and one explicit entry per task, never the tools themselves. */
@Composable
internal fun IdentitySummaryPage(
    ops: IdentityOps,
    state: IdentityState,
    busy: Boolean,
    onBack: () -> Unit,
    onOpenTechnical: () -> Unit,
    onOpenFacts: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    TaskPage(stringResource(R.string.identity_title), busy, onBack) {
        ManagerCard(
            helper = stringResource(R.string.identity_explanation),
            statusText = identityAvailabilityText(state),
            statusActive = state.isVerifiedIdentity(),
        ) {
            val member = state.member
            val none = stringResource(R.string.identity_none)
            if (member == null) {
                Text(stringResource(R.string.identity_unbound), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.identity_my_member), style = MaterialTheme.typography.labelMedium)
                Text(member.memberId, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                Text(stringResource(R.string.identity_my_clubs), style = MaterialTheme.typography.labelMedium)
                Text(member.clubs.joinToString(stringResource(R.string.governance_separator)).ifEmpty { none }, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.identity_chairs, member.chairOf.joinToString().ifEmpty { none }), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.identity_permissions, member.permissions.joinToString().ifEmpty { none }), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = { ops.run("sync") }, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.startup_connection_recheck_identity))
        }
        MenuEntry(stringResource(R.string.startup_identity_technical_title), stringResource(R.string.account_identity_configuration_help), onClick = onOpenTechnical)
        MenuEntry(stringResource(R.string.identity_verified_facts), stringResource(R.string.account_identity_facts_help), onClick = onOpenFacts)
        MenuEntry(stringResource(R.string.identity_actions_title), stringResource(R.string.account_identity_records_help), onClick = onOpenRecords)
    }
}

/** Paste the material a chair handed over; the native review path decides whether it is valid. */
@Composable
internal fun IdentityImportPage(
    ops: IdentityOps,
    busy: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onOpenTechnical: () -> Unit,
) {
    var configuration by rememberSaveable(ops.config) { mutableStateOf("") }
    var invalid by rememberSaveable(ops.config) { mutableStateOf(false) }
    TaskPage(stringResource(R.string.startup_identity_import_title), busy, onBack) {
        Text(stringResource(R.string.startup_identity_import_help), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = configuration,
            onValueChange = { configuration = it; invalid = false },
            label = { Text(stringResource(R.string.startup_identity_import_label)) },
            supportingText = { Text(stringResource(R.string.startup_identity_import_help)) },
            enabled = !busy,
            isError = invalid,
            modifier = Modifier.fillMaxWidth(),
        )
        if (invalid) Text(stringResource(R.string.startup_identity_import_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        ActionButton(R.string.startup_identity_import_confirm, !busy && configuration.isNotBlank()) {
            invalid = !ops.saveConfiguration(configuration)
            if (!invalid) onSaved()
        }
        TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.dialog_cancel))
        }
        HorizontalDivider()
        MenuEntry(
            label = stringResource(R.string.startup_identity_technical_open),
            help = stringResource(R.string.startup_identity_technical_help),
            enabled = !busy,
            onClick = onOpenTechnical,
        )
    }
}

/** The raw configuration; regular members never need this page. */
@Composable
internal fun IdentityTechnicalPage(
    ops: IdentityOps,
    busy: Boolean,
    onBack: () -> Unit,
    onOpenFacts: () -> Unit,
) {
    var configuration by rememberSaveable(ops.config) { mutableStateOf(ops.config) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    TaskPage(stringResource(R.string.startup_identity_technical_title), busy, onBack) {
        Text(stringResource(R.string.startup_identity_advanced_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = configuration,
            onValueChange = { configuration = it; invalid = false },
            label = { Text(stringResource(R.string.identity_configuration)) },
            enabled = !busy,
            isError = invalid,
            supportingText = if (invalid) ({ Text(stringResource(R.string.identity_invalid)) }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        ActionButton(R.string.identity_save, !busy && configuration.isNotBlank()) {
            invalid = !ops.saveConfiguration(configuration)
        }
        MenuEntry(
            label = stringResource(R.string.identity_verified_facts),
            help = stringResource(R.string.account_identity_facts_help),
            enabled = !busy,
            onClick = onOpenFacts,
        )
    }
}

/** Raw verified facts and the member id as the ledger reports them. */
@Composable
internal fun IdentityFactsPage(ops: IdentityOps, busy: Boolean, onBack: () -> Unit) {
    TaskPage(stringResource(R.string.identity_verified_facts), busy, onBack) {
        ops.state.member?.let { member ->
            Text(stringResource(R.string.identity_my_member), style = MaterialTheme.typography.labelMedium)
            Text(member.memberId, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        }
        val facts = ops.facts
        if (facts == null) {
            Text(stringResource(R.string.identity_state_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ValidationCodeNotice()
            SelectionContainer {
                Text(technicalJson(facts), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Paste a record until one exists; after that the readable summary is primary and raw editing is explicit. */
@Composable
internal fun ProposalDetails(
    ops: IdentityOps,
    busy: Boolean,
    label: Int,
) {
    var editingRaw by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val reviewable = ops.proposal.isNotBlank() &&
        runCatching { JSONObject(ops.proposal).optJSONObject("action") != null }.getOrDefault(false)
    // Typing never collapses the field: only an explicit review turns valid material into the summary.
    val raw = editingRaw || importing || ops.proposalInvalid || (ops.proposal.isNotBlank() && !reviewable)
    if (raw) {
        OutlinedTextField(
            value = ops.proposal,
            onValueChange = { ops.editProposal(it); editingRaw = true },
            label = { Text(stringResource(label)) },
            enabled = !busy,
            isError = ops.proposalInvalid,
            modifier = Modifier.fillMaxWidth(),
        )
        if (ops.proposalInvalid) Text(
            stringResource(R.string.identity_proposal_invalid),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        ActionButton(
            R.string.identity_review_changes,
            !busy && ops.proposal.isNotBlank(),
            kind = ActionKind.Secondary,
        ) {
            if (ops.reviewProposal()) {
                editingRaw = false
                importing = false
            }
        }
    } else if (ops.proposal.isBlank()) {
        ActionButton(R.string.identity_import_record, !busy, kind = ActionKind.Secondary) {
            importing = true
        }
    } else {
        ProposalSummary(ops.proposal)
        TextButton(
            onClick = { editingRaw = true },
            enabled = !busy,
            modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
        ) { Text(stringResource(R.string.identity_raw_record_show)) }
    }
}

/** Generic record work: draft, sign, offline-sign and submit stay on one explicit page. */
@Composable
internal fun IdentityRecordsPage(ops: IdentityOps, busy: Boolean, members: List<String>, onBack: () -> Unit) {
    val ready = !busy && ops.config.isNotBlank() && ops.proposal.isNotBlank()
    TaskPage(stringResource(R.string.identity_actions_title), busy, onBack) {
        Text(stringResource(R.string.identity_records_help), style = MaterialTheme.typography.bodyMedium)
        CollapsibleSection(
            title = stringResource(R.string.identity_create_change),
            showLabel = R.string.identity_create_change_show,
            hideLabel = R.string.identity_create_change_hide,
        ) {
            IdentityActionForm(!busy && ops.config.isNotBlank(), members) { action -> ops.run("draft", action) }
        }
        ProposalDetails(ops, busy, R.string.identity_proposal)
        if (ops.proposal.isNotBlank()) PublicJsonActions(ops.proposal, R.string.identity_proposal, !busy)
        when (ops.outcome) {
            ProposalOutcome.Submitted -> Text(stringResource(R.string.identity_submit_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            ProposalOutcome.Failed -> Text(stringResource(R.string.identity_submit_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            null -> Unit
        }
        // Sign and submit belong to SignSubmitActions alone; repeating 签名 here rendered two
        // identical buttons on the same page.
        ActionButton(R.string.identity_draft, ready, kind = ActionKind.Secondary) { ops.run("draft") }
        ActionButton(R.string.identity_sign_offline, ready, kind = ActionKind.Secondary) { ops.run("sign_offline") }
        SignSubmitActions(
            signed = proposalSignatureCount(ops.proposal) > 0,
            ready = ready,
            onSign = { ops.run("sign") },
            onSubmit = { ops.run("submit") },
        )
    }
}

/**
 * The signature count a drafted record holds, read from the record itself instead of
 * assumed. Zero means the submit action is not the next positive step yet.
 */
internal fun proposalSignatureCount(proposal: String): Int =
    runCatching { JSONObject(proposal).optJSONArray("signatures")?.length() ?: 0 }.getOrDefault(0)

/**
 * Exclusive primary weight for a sign-then-submit pair. While the record has no signature the sign
 * action is the only solid button and submit is disabled; once signed, submit becomes the solid
 * action and sign demotes to an outline. A review page therefore never invites a submit that the
 * server is guaranteed to refuse.
 */
@Composable
internal fun SignSubmitActions(
    signed: Boolean,
    ready: Boolean,
    onSign: () -> Unit,
    onSubmit: () -> Unit,
    confirmText: Int = R.string.identity_submit_confirm,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S), modifier = modifier.fillMaxWidth()) {
        ActionButton(
            label = R.string.identity_sign,
            enabled = ready,
            kind = if (signed) ActionKind.Secondary else ActionKind.Primary,
            modifier = Modifier.weight(1f),
        ) { onSign() }
        ActionButton(
            label = R.string.identity_submit,
            enabled = ready && signed,
            kind = if (signed) ActionKind.Confirmed else ActionKind.Secondary,
            confirmText = confirmText,
            modifier = Modifier.weight(1f),
        ) { onSubmit() }
    }
}

/** The applicant's next step: hand the public half of the identity to the chair. */
@Composable
internal fun IdentitySharePage(publicIdentity: String, busy: Boolean, onBack: () -> Unit) {
    TaskPage(stringResource(R.string.startup_identity_share_title), busy, onBack) {
        MemberIdentityShareSection(publicIdentity, busy)
    }
}

/** A chair sent a member record: the applicant signs and submits it with this device. */
@Composable
internal fun IdentityEnrollmentPage(ops: IdentityOps, busy: Boolean, onBack: () -> Unit) {
    val ready = !busy && ops.proposal.isNotBlank()
    TaskPage(stringResource(R.string.startup_identity_enrollment_title), busy, onBack) {
        Text(stringResource(R.string.startup_identity_enrollment_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProposalDetails(ops, busy, R.string.startup_identity_enrollment_label)
        if (ops.proposal.isNotBlank()) PublicJsonActions(ops.proposal, R.string.identity_proposal, !busy)
        when (ops.outcome) {
            ProposalOutcome.Submitted -> Text(stringResource(R.string.identity_submit_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            ProposalOutcome.Failed -> Text(stringResource(R.string.identity_submit_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            null -> Unit
        }
        SignSubmitActions(
            signed = proposalSignatureCount(ops.proposal) > 0,
            ready = ready,
            onSign = { ops.run("sign") },
            onSubmit = { ops.run("submit") },
        )
    }
}

private const val SUMMARY_SEPARATOR = "、"

/** A readable digest of a drafted record, so raw JSON is never the default review material. */
@Composable
internal fun ProposalSummary(proposal: String) {
    val record = runCatching { JSONObject(proposal) }.getOrNull() ?: return
    val action = record.optJSONObject("action") ?: return
    val type = action.optString("type")
    val evidence = mapOf(
        "institutional_email" to stringResource(R.string.identity_evidence_email),
        "game_profile" to stringResource(R.string.identity_evidence_profile),
        "manual" to stringResource(R.string.identity_issuance_evidence_manual),
    )
    val services = mapOf("application" to stringResource(R.string.identity_service_application))
    val permissions = mapOf(
        "read" to stringResource(R.string.identity_permission_read),
        "write" to stringResource(R.string.identity_permission_write),
        "admin" to stringResource(R.string.identity_permission_admin),
        "use" to stringResource(R.string.identity_permission_use),
        "manage" to stringResource(R.string.identity_permission_manage),
    )
    val actionLabel = identityActionLabel(type)
    val none = stringResource(R.string.identity_none)
    Text(stringResource(R.string.identity_summary_title), style = MaterialTheme.typography.titleSmall)
    Text(
        if (actionLabel == R.string.identity_invalid) {
            stringResource(R.string.identity_summary_unknown_action, type.ifBlank { none })
        } else stringResource(actionLabel),
        style = MaterialTheme.typography.titleMedium,
    )
    for (name in action.keys().asSequence().filter { it != "type" }.toList()) {
        val text = summaryValue(action.opt(name), name, evidence, services, permissions) ?: continue
        if (text.isBlank()) continue
        val label = if (type == "confirm_member" && name == "evidence") R.string.identity_issuance_evidence_label else identityFieldLabel(name)
        Text(if (label == R.string.identity_invalid) name else stringResource(label), style = MaterialTheme.typography.labelMedium)
        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
    }
    Text(stringResource(R.string.identity_signature_count, record.optJSONArray("signatures")?.length() ?: 0), style = MaterialTheme.typography.bodySmall)
}

/** Readable field content: known values are translated and nested records never fall back to raw JSON. */
private fun summaryValue(
    value: Any?,
    field: String,
    evidence: Map<String, String>,
    services: Map<String, String>,
    permissions: Map<String, String>,
): String? = when (value) {
    null, JSONObject.NULL -> null
    is JSONArray -> (0 until value.length())
        .mapNotNull { summaryValue(value.opt(it), field, evidence, services, permissions) }
        .joinToString(SUMMARY_SEPARATOR).ifBlank { null }
    is JSONObject -> if (field == "chairs") {
        value.keys().asSequence().mapNotNull { key ->
            value.opt(key)?.takeUnless { it == JSONObject.NULL }?.let { "$key / $it" }
        }.joinToString(SUMMARY_SEPARATOR).ifBlank { null }
    } else value.keys().asSequence().mapNotNull { key ->
        summaryValue(value.opt(key), key, evidence, services, permissions)?.let { item ->
            "$key: $item"
        }
    }.joinToString(SUMMARY_SEPARATOR).ifBlank { null }
    else -> when (field) {
        "service" -> services[value.toString()]
        "permission" -> permissions[value.toString()]
        "evidence" -> evidence[value.toString()]
        else -> null
    } ?: value.toString()
}

/** Reminds the operator that the raw JSON is technical material, not a verification code. */
@Composable
internal fun ValidationCodeNotice() {
    Text(
        stringResource(R.string.identity_facts_not_code),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = SPACING_XS),
    )
}

/** The readable facts shared by first trust and the genesis builder; raw JSON stays on its own page. */
@Composable
internal fun GenesisSummary(genesis: JSONObject) {
    Text(stringResource(R.string.identity_fetch_federation, genesis.optString("federation")), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.identity_fetch_founders), style = MaterialTheme.typography.labelMedium)
    genesis.optJSONArray("founders")?.let { founders ->
        for (index in 0 until founders.length()) {
            val founder = founders.optJSONObject(index) ?: continue
            Text(stringResource(R.string.identity_fetch_founder, founder.optString("member"), founder.optString("club")), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(stringResource(R.string.identity_fetch_quorum, genesis.optInt("quorum")), style = MaterialTheme.typography.bodyMedium)
}

/** Readable organization summary for the first-trust page; the raw genesis stays on its own page. */
@Composable
internal fun GenesisTrustSummary(fetched: JSONObject): Boolean {
    val genesis = runCatching { fetched.getJSONObject("identity").getJSONObject("genesis") }.getOrNull() ?: return false
    GenesisSummary(genesis)
    if (!fetched.isNull("application")) {
        Text(stringResource(R.string.identity_fetch_application), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.identity_fetch_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    return true
}

/** The raw genesis JSON, reached from the trust page as an explicit technical detail. */
@Composable
internal fun GenesisFactsPage(fetched: JSONObject, onBack: () -> Unit) {
    val genesis = runCatching { fetched.getJSONObject("identity").getJSONObject("genesis") }.getOrNull()
    TaskPage(stringResource(R.string.identity_fetch_details_title), false, onBack) {
        ValidationCodeNotice()
        if (genesis == null) Text(stringResource(R.string.identity_fetch_details_missing), color = MaterialTheme.colorScheme.error)
        else SelectionContainer {
            Text(technicalJson(genesis), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
