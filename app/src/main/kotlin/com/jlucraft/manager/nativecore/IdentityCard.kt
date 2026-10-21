package com.jlucraft.manager.nativecore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jlucraft.manager.R
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

@Composable
internal fun IdentityCard(
    config: String, device: String, busy: Boolean, directory: File,
    address: String = "", peer: String = "",
    onConfig: (String) -> Unit,
    proposalState: MutableState<String> = rememberSaveable { mutableStateOf("") },
    fetch: ((JSONObject) -> Unit) -> Unit = {},
    review: (String) -> String = { text -> UnionNative.management(text) },
    onOfficeGenesis: (String) -> Unit = {},
    onFetchFailed: () -> Unit = {},
    execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
) {
    var configuration by remember(config) { mutableStateOf(config) }
    var proposal by proposalState
    var state by remember(config, device) { mutableStateOf(IdentityState()) }
    var facts by remember(config, device) { mutableStateOf<JSONObject?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var proposalInvalid by remember { mutableStateOf(false) }
    var fetching by remember(config) { mutableStateOf(false) }
    var fetched by remember(config) { mutableStateOf<JSONObject?>(null) }
    // Raw settings and records are advanced material; the summary is what an operator reviews.
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    var showProposal by rememberSaveable { mutableStateOf(false) }
    fun run(operation: String, action: JSONObject? = null) {
        try {
            val input = JSONObject().put("config", identityConfig(config, directory)).put("operation", operation)
            if (operation == "draft") input.put("action", action ?: JSONObject(proposal))
            if (operation == "sign" || operation == "sign_offline" || operation == "submit") input.put("record", JSONObject(proposal))
            val previous = state
            state = state.syncing(); proposalInvalid = false
            execute(input) { result ->
                if (result.has("error")) state = state.failed()
                else if (operation == "draft" || operation == "sign" || operation == "sign_offline") {
                    proposal = result.toString(2)
                    state = previous
                } else {
                    try { state = state.received(identityMember(result, device), operation != "inspect"); facts = result }
                    catch (_: Exception) { state = state.failed() }
                }
            }
        } catch (_: Exception) { proposalInvalid = true }
    }
    ManagerCard(
        title = stringResource(R.string.identity_title),
        helper = stringResource(R.string.identity_explanation),
        statusText = stringResource(when (state.availability) {
            IdentityAvailability.Unconfigured -> R.string.identity_empty
            IdentityAvailability.Syncing -> R.string.identity_syncing
            IdentityAvailability.Cached -> R.string.identity_cached
            IdentityAvailability.Current -> R.string.identity_current
            IdentityAvailability.Unavailable -> R.string.identity_state_unavailable
        }),
        statusActive = state.availability == IdentityAvailability.Current,
    ) {
        if (config.isBlank() || showConfiguration) {
            OutlinedTextField(configuration, { configuration = it; invalid = false }, label = { Text(stringResource(R.string.identity_configuration)) }, enabled = !busy, isError = invalid,
                supportingText = if (invalid) ({ Text(stringResource(R.string.identity_invalid)) }) else null, modifier = Modifier.fillMaxWidth())
            ActionButton(R.string.identity_save, !busy) {
                try {
                    val verified = review(JSONObject().put("operation", "review_configuration").put("config", JSONObject(configuration)).toString())
                    onConfig(identityConfig(verified, directory).toString()); invalid = false
                }
                catch (_: Exception) { invalid = true }
            }
        }
        if (config.isBlank()) {
            ActionButton(R.string.identity_fetch, !busy && !fetching && address.isNotBlank() && peer.isNotBlank(), kind = ActionKind.Secondary) {
                fetching = true
                fetch { result ->
                    fetching = false
                    if (result.has("error")) onFetchFailed() else fetched = result
                }
            }
            if (address.isBlank() || peer.isBlank()) {
                Text(stringResource(R.string.identity_fetch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (fetching) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        fetched?.let { result ->
            GenesisConfirmDialog(result, onConfirm = {
                fetched = null
                try {
                    val identity = result.getJSONObject("identity")
                    val candidate = JSONObject()
                        .put("genesis", identity.getJSONObject("genesis"))
                        .put("genesis_hash", identity.getString("genesis_hash"))
                        .put("address", address)
                        .put("peer", peer)
                    val verified = review(JSONObject().put("operation", "review_configuration").put("config", candidate).toString())
                    onConfig(identityConfig(verified, directory).toString())
                    result.optJSONObject("office")?.optString("genesis_hash")
                        ?.takeIf { GENESIS_HASH_PATTERN.matches(it) }
                        ?.let(onOfficeGenesis)
                } catch (_: Exception) {
                    onFetchFailed()
                }
            }, onDismiss = { fetched = null })
        }
        if (config.isNotBlank()) TextButton(onClick = { showConfiguration = !showConfiguration }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (showConfiguration) R.string.identity_hide_configuration else R.string.identity_show_configuration))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S)) {
            ActionButton(R.string.identity_inspect, !busy && config.isNotBlank(), kind = ActionKind.Secondary, modifier = Modifier.weight(1f)) { run("inspect") }
            ActionButton(R.string.identity_sync, !busy && config.isNotBlank(), kind = ActionKind.Secondary, modifier = Modifier.weight(1f)) { run("sync") }
        }
        if (state.availability == IdentityAvailability.Unavailable) {
            Text(stringResource(R.string.identity_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.member?.let { member ->
            Text(stringResource(R.string.identity_my_member), style = MaterialTheme.typography.labelMedium)
            IdentifierDetails(member.memberId)
            Text(stringResource(R.string.identity_my_clubs), style = MaterialTheme.typography.labelMedium)
            IdentifierDetails(member.clubs.joinToString())
            val none = stringResource(R.string.identity_none)
            Text(stringResource(R.string.identity_chairs, member.chairOf.joinToString().ifEmpty { none }))
            Text(stringResource(R.string.identity_permissions, member.permissions.joinToString().ifEmpty { none }))
        }
        if (state.availability == IdentityAvailability.Current && state.member == null) Text(stringResource(R.string.identity_unbound))
        facts?.let { value ->
            Text(stringResource(R.string.identity_verified_facts), style = MaterialTheme.typography.titleSmall)
            IdentifierDetails(remember(value) { value.toString(2) }, R.string.identity_show_facts)
        }
        TextButton(onClick = { showProposal = !showProposal }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (showProposal) R.string.identity_proposal_hide else R.string.identity_proposal_show))
        }
        AnimatedVisibility(visible = showProposal, enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING_M)) {
                IdentityActionForm(!busy && config.isNotBlank()) { action -> run("draft", action) }
                OutlinedTextField(proposal, { proposal = it; proposalInvalid = false }, label = { Text(stringResource(R.string.identity_proposal)) }, enabled = !busy, isError = proposalInvalid,
                    modifier = Modifier.fillMaxWidth())
                if (proposalInvalid) Text(stringResource(R.string.identity_proposal_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.identity_review), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (proposal.isNotBlank()) {
                    ProposalSummary(proposal)
                    PublicJsonActions(proposal, R.string.identity_proposal, !busy)
                }
                val ready = !busy && config.isNotBlank() && proposal.isNotBlank()
                Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S)) {
                    ActionButton(R.string.identity_draft, ready, kind = ActionKind.Secondary, modifier = Modifier.weight(1f)) { run("draft") }
                    ActionButton(R.string.identity_sign, ready, kind = ActionKind.Secondary, modifier = Modifier.weight(1f)) { run("sign") }
                }
                ActionButton(R.string.identity_sign_offline, ready, kind = ActionKind.Secondary) { run("sign_offline") }
                ActionButton(R.string.identity_submit, ready, kind = ActionKind.Confirmed, confirmText = R.string.identity_submit_confirm) { run("submit") }
            }
        }
    }
}

/** A readable digest of a drafted record, so the raw JSON is not the only thing a chair reviews before signing. */
@Composable
internal fun ProposalSummary(proposal: String) {
    val record = remember(proposal) { runCatching { JSONObject(proposal) }.getOrNull() } ?: return
    val action = record.optJSONObject("action") ?: return
    val type = action.optString("type")
    Text(stringResource(R.string.identity_summary_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(identityActionLabel(type)), style = MaterialTheme.typography.titleMedium)
    for (name in action.keys().asSequence().filter { it != "type" }.toList()) {
        val text = summaryValue(action.opt(name)) ?: continue
        if (text.isBlank() || identityFieldLabel(name) == R.string.identity_invalid) continue
        Text(stringResource(identityFieldLabel(name)), style = MaterialTheme.typography.labelMedium)
        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
    }
    Text(stringResource(R.string.identity_signature_count, record.optJSONArray("signatures")?.length() ?: 0), style = MaterialTheme.typography.bodySmall)
}

/** A field value as an operator reads it: evidence kinds use their plain names, never raw protocol identifiers. */
@Composable
private fun summaryValue(value: Any?): String? {
    val parts = when (value) {
        is org.json.JSONArray -> (0 until value.length()).map { value.get(it).toString() }
        null, JSONObject.NULL -> return null
        else -> listOf(value.toString())
    }
    val email = stringResource(R.string.identity_evidence_email)
    val profile = stringResource(R.string.identity_evidence_profile)
    return parts.joinToString("、") { part ->
        when (part) {
            "institutional_email" -> email
            "game_profile" -> profile
            else -> part
        }
    }
}

/** First-connection review: the operator confirms the fetched identity genesis before it becomes trusted. */
@Composable
internal fun GenesisConfirmDialog(fetched: JSONObject, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val genesis = runCatching { fetched.getJSONObject("identity").getJSONObject("genesis") }.getOrNull() ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.identity_fetch_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                Text(stringResource(R.string.identity_fetch_federation, genesis.optString("federation")), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.identity_fetch_founders), style = MaterialTheme.typography.labelMedium)
                genesis.optJSONArray("founders")?.let { founders ->
                    for (index in 0 until founders.length()) {
                        val founder = founders.optJSONObject(index) ?: continue
                        Text(stringResource(R.string.identity_fetch_founder, founder.optString("member"), founder.optString("club")), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.identity_fetch_quorum, genesis.optInt("quorum")), style = MaterialTheme.typography.bodyMedium)
                if (!fetched.isNull("office")) {
                    Text(stringResource(R.string.identity_fetch_office), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.identity_fetch_genesis), style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(genesis.toString(2), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(stringResource(R.string.identity_fetch_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.identity_fetch_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
