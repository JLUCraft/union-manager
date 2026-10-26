package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The catalog minus the actions that would author the founder roster. */
internal fun offeredIdentityForms(catalog: JSONArray): List<JSONObject> =
    (0 until catalog.length()).map { catalog.getJSONObject(it) }
        .filterNot { it.getString("action") == FOUNDER_ROSTER_ACTION }

/** Rewrites the whole founder roster in one action; the app never offers it. */
private const val FOUNDER_ROSTER_ACTION = "replace_chairs"

internal fun identityActionLabel(action: String): Int = when (action) {
    "confirm_member" -> R.string.identity_action_confirm_member
    "revoke_membership" -> R.string.identity_action_revoke_membership
    "bind_device" -> R.string.identity_action_bind_device
    "bind_game_profile" -> R.string.identity_action_bind_game_profile
    "revoke_device" -> R.string.identity_action_revoke_device
    "register_service" -> R.string.identity_action_register_service
    "grant_permission" -> R.string.identity_action_grant_permission
    "revoke_permission" -> R.string.identity_action_revoke_permission
    "authorize_verifier" -> R.string.identity_action_authorize_verifier
    "revoke_verifier" -> R.string.identity_action_revoke_verifier
    "propose_succession" -> R.string.identity_action_propose_succession
    "approve_succession" -> R.string.identity_action_approve_succession
    "execute_succession" -> R.string.identity_action_execute_succession
    "cancel_succession" -> R.string.identity_action_cancel_succession
    else -> R.string.identity_invalid
}
internal fun identityFieldLabel(field: String): Int = when (field) {
    "member" -> R.string.identity_field_member
    "club" -> R.string.identity_field_club
    "device" -> R.string.identity_field_device
    "profile" -> R.string.identity_field_profile
    "service" -> R.string.identity_field_service
    "audience" -> R.string.identity_field_audience
    "id" -> R.string.identity_field_id
    "permission" -> R.string.identity_field_permission
    "quorum" -> R.string.identity_field_quorum
    "chairs" -> R.string.identity_field_chairs
    "evidence" -> R.string.identity_field_evidence
    "verifier" -> R.string.identity_field_verifier
    else -> R.string.identity_invalid
}

@Composable
internal fun IdentityActionForm(
    enabled: Boolean,
    members: List<String> = emptyList(),
    /** Open the form on one specific action instead of the catalog's first; no behaviour change. */
    initialAction: String? = null,
    onDraft: (JSONObject) -> Unit,
) {
    val catalog = remember { runCatching { JSONArray(UnionNative.management("{\"operation\":\"forms\"}")) } }
    if (catalog.isFailure) { Text(stringResource(R.string.identity_forms_unavailable)); return }
    // The founder roster is defined by the server's source and immutable once the genesis is
    // published, so the catalog's bulk chair rewrite is not offered here: changing who leads a club
    // goes through multi-signature succession, and changing a device goes through bind/revoke.
    val forms = remember { offeredIdentityForms(catalog.getOrThrow()) }
    if (forms.isEmpty()) { Text(stringResource(R.string.identity_forms_unavailable)); return }
    var selected by remember {
        mutableStateOf(forms.firstOrNull { it.getString("action") == initialAction }?.getString("action") ?: forms.first().getString("action"))
    }
    var expanded by remember { mutableStateOf(false) }
    var invalid by remember(selected) { mutableStateOf(false) }
    val values = remember(selected) { mutableStateMapOf<String, Any>() }
    Text(stringResource(R.string.identity_actions_title), style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(identityActionLabel(selected))) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            forms.forEach { form ->
                val action = form.getString("action")
                DropdownMenuItem(text = { Text(stringResource(identityActionLabel(action))) }, onClick = { selected = action; expanded = false })
            }
        }
    }
    key(selected) {
        val fields = forms.first { it.getString("action") == selected }.getJSONArray("fields")
        for (index in 0 until fields.length()) {
            val field = fields.getJSONObject(index)
            val name = field.getString("name")
            val label = stringResource(identityFieldLabel(name))
            when (field.getString("kind")) {
                "chairs" -> ChairFields(enabled) { values[name] = it; invalid = false }
                "choices" -> {
                    Text(label)
                    val choices = field.getJSONArray("choices")
                    for (choiceIndex in 0 until choices.length()) {
                        val choice = choices.getString(choiceIndex)
                        val text = when (choice) {
                            "institutional_email" -> R.string.identity_evidence_email
                            "game_profile" -> R.string.identity_evidence_profile
                            else -> R.string.identity_invalid
                        }
                        val current = (values[name] as? List<*>)?.filterIsInstance<String>().orEmpty()
                        FilterChip(selected = choice in current, enabled = enabled, onClick = {
                            values[name] = if (choice in current) current - choice else current + choice
                            invalid = false
                        }, label = { Text(stringResource(text)) })
                    }
                }
                else -> {
                    var text by remember(name) { mutableStateOf("") }
                    if (name == "member" && members.isNotEmpty()) {
                        ChoiceField(identityFieldLabel(name), text, members, enabled) { picked ->
                            text = picked; values[name] = picked; invalid = false
                        }
                    } else {
                        OutlinedTextField(text, { input ->
                            text = input
                            values[name] = if (field.getString("kind") == "number") input.toLongOrNull() ?: input else input
                            invalid = false
                        }, enabled = enabled, label = { Text(label) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        FieldSuggestions(
                            name,
                            when (name) {
                                "service" -> SERVICE_SUGGESTIONS
                                "permission" -> PERMISSION_SUGGESTIONS
                                else -> emptyList()
                            }, text, enabled,
                        ) { picked ->
                            text = picked; values[name] = picked; invalid = false
                        }
                        if (field.optBoolean("generated")) TextButton(enabled = enabled, onClick = {
                            try {
                                text = org.json.JSONTokener(UnionNative.management("{\"operation\":\"new_service_id\"}")).nextValue() as String
                                values[name] = text; invalid = false
                            } catch (_: Exception) { invalid = true }
                        }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.identity_generate_id)) }
                    }
                }
            }
        }
    }
    if (invalid) Text(stringResource(R.string.identity_form_invalid), color = MaterialTheme.colorScheme.error)
    ActionButton(R.string.identity_draft, enabled, kind = ActionKind.Secondary) {
        try {
            val request = JSONObject().put("operation", "build_action").put("action", selected).put("fields", JSONObject(values.toMap()))
            onDraft(JSONObject(UnionNative.management(request.toString())))
            invalid = false
        } catch (_: Exception) { invalid = true }
    }
}

private data class ChairInput(val id: String = UUID.randomUUID().toString(), val club: String = "", val member: String = "")

// Protocol tokens only suggest common values; the text field decides for both.
private val SERVICE_SUGGESTIONS = listOf("application")
private val PERMISSION_SUGGESTIONS = listOf("read", "write", "admin", "use", "manage")

private fun suggestionLabel(field: String, value: String): Int? = when (field) {
    "service" -> when (value) {
        "application" -> R.string.identity_service_application
        else -> null
    }
    "permission" -> when (value) {
        "read" -> R.string.identity_permission_read
        "write" -> R.string.identity_permission_write
        "admin" -> R.string.identity_permission_admin
        "use" -> R.string.identity_permission_use
        "manage" -> R.string.identity_permission_manage
        else -> null
    }
    else -> null
}

@Composable
private fun FieldSuggestions(
    field: String,
    suggestions: List<String>,
    selected: String,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SPACING_S), verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
        suggestions.forEach { suggestion ->
            val label = suggestionLabel(field, suggestion)
            FilterChip(
                selected = selected == suggestion,
                enabled = enabled,
                onClick = { onPick(suggestion) },
                label = { Text(if (label != null) stringResource(label) else suggestion) },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
            )
        }
    }
}

@Composable
private fun ChairFields(enabled: Boolean, onValue: (Any) -> Unit) {
    var rows by remember { mutableStateOf(listOf(ChairInput())) }
    fun change(next: List<ChairInput>) {
        rows = next
        val pairs = next.map { it.club to it.member }
        // Invalid/duplicate rows must not silently overwrite one another in a JSON map.
        onValue(if (pairs.any { it.first.isBlank() || it.second.isBlank() } || pairs.map { it.first }.toSet().size != pairs.size) JSONObject.NULL else JSONObject(pairs.toMap()))
    }
    Text(stringResource(R.string.identity_field_chairs))
    rows.forEach { row -> key(row.id) {
        OutlinedTextField(row.club, { value -> change(rows.map { if (it.id == row.id) it.copy(club = value) else it }) }, enabled = enabled,
            label = { Text(stringResource(R.string.identity_field_club)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(row.member, { value -> change(rows.map { if (it.id == row.id) it.copy(member = value) else it }) }, enabled = enabled,
            label = { Text(stringResource(R.string.identity_field_member)) }, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = enabled, onClick = { change(rows.filter { it.id != row.id }) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.identity_remove_chair)) }
    } }
    OutlinedButton(enabled = enabled, onClick = { change(rows + ChairInput()) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.identity_add_chair)) }
}
