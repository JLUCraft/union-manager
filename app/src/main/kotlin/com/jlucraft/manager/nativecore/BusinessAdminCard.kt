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

private data class ReplicaDraft(val rowId: String = UUID.randomUUID().toString(), val id: String = "", val peer: String = "", val addresses: String = "")

@Composable
internal fun BusinessAdminCard(state: JSONObject?, device: String, busy: Boolean, submit: (JSONObject) -> Unit) {
    if (state == null || !state.optJSONArray("admins").containsString(device)) return
    var expanded by remember { mutableStateOf(false) }
    var member by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf("") }
    var replicas by remember { mutableStateOf(listOf<ReplicaDraft>()) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var invalid by remember { mutableStateOf(false) }
    fun prepare(request: JSONObject) {
        try { preview = JSONObject(UnionNative.management(request.toString())); invalid = false }
        catch (_: Exception) { preview = null; invalid = true }
    }
    fun changeReplicas(next: List<ReplicaDraft>) { replicas = next; preview = null; invalid = false }
    fun editReplica(rowId: String, edit: (ReplicaDraft) -> ReplicaDraft) = changeReplicas(replicas.map { if (it.rowId == rowId) edit(it) else it })
    ManagerCard(title = stringResource(R.string.business_admin_title), helper = stringResource(R.string.business_admin_helper)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(if (expanded) R.string.genesis_builder_hide else R.string.business_admin_open)) }
        if (!expanded) return@ManagerCard
        Text(stringResource(R.string.business_roles_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(member, { member = it; preview = null }, enabled = !busy, label = { Text(stringResource(R.string.identity_field_member)) }, modifier = Modifier.fillMaxWidth())
        val committedMember = state.optJSONObject("members")?.optJSONObject(member)
        committedMember?.let { current ->
            val knownRoles = (current.optJSONArray("roles") ?: JSONArray()).strings()
            Text(stringResource(R.string.business_current_roles, knownRoles.joinToString(", ")))
            ActionButton(R.string.business_load_roles, !busy, kind = ActionKind.Secondary) {
                roles = knownRoles.joinToString("\n"); preview = null
            }
        }
        OutlinedTextField(roles, { roles = it; preview = null }, enabled = !busy, label = { Text(stringResource(R.string.business_roles)) }, supportingText = { Text(stringResource(R.string.business_roles_hint)) }, modifier = Modifier.fillMaxWidth())
        ActionButton(R.string.business_preview, !busy && member.isNotBlank(), kind = ActionKind.Secondary) {
            prepare(JSONObject().put("operation", "build_roles").put("member", member).put("roles", JSONArray(roles.lines().filter { it.isNotBlank() })))
        }
        HorizontalDivider()
        Text(stringResource(R.string.business_replicas_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.business_replicas_hint), style = MaterialTheme.typography.bodySmall)
        val committed = state.optJSONObject("consensus_members") ?: JSONObject()
        if (committed.length() == 0) Text(stringResource(R.string.business_replicas_empty)) else IdentifierDetails(committed.toString(2))
        ActionButton(R.string.business_replicas_load, !busy && committed.length() > 0, kind = ActionKind.Secondary) {
            changeReplicas(committed.keys().asSequence().map { id ->
                val node = committed.getJSONObject(id)
                ReplicaDraft(id = id, peer = node.getString("peer"), addresses = node.getJSONArray("addresses").strings().joinToString("\n"))
            }.toList())
        }
        replicas.forEach { row -> key(row.rowId) {
            OutlinedTextField(row.id, { value -> editReplica(row.rowId) { it.copy(id = value) } }, enabled = !busy, label = { Text(stringResource(R.string.business_replica_id)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(row.peer, { value -> editReplica(row.rowId) { it.copy(peer = value) } }, enabled = !busy, label = { Text(stringResource(R.string.identity_field_audience)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(row.addresses, { value -> editReplica(row.rowId) { it.copy(addresses = value) } }, enabled = !busy, label = { Text(stringResource(R.string.business_replica_addresses)) }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy, onClick = { changeReplicas(replicas.filter { it.rowId != row.rowId }) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.business_replica_remove)) }
        } }
        ActionButton(R.string.business_replica_add, !busy, kind = ActionKind.Secondary) { changeReplicas(replicas + ReplicaDraft()) }
        ActionButton(R.string.business_replicas_preview, !busy && replicas.isNotEmpty(), kind = ActionKind.Secondary) {
            try {
                val members = JSONArray()
                replicas.forEach { row -> members.put(JSONObject().put("id", row.id.trim()).put("node", JSONObject().put("peer", row.peer.trim()).put("addresses", JSONArray(row.addresses.lines().map { it.trim() }.filter { it.isNotEmpty() })))) }
                prepare(JSONObject().put("operation", "build_consensus").put("members", members))
            } catch (_: Exception) { preview = null; invalid = true }
        }
        if (invalid) Text(stringResource(R.string.business_invalid), color = MaterialTheme.colorScheme.error)
        preview?.let { action ->
            Text(stringResource(R.string.business_review))
            IdentifierDetails(action.toString(2))
            ActionButton(R.string.business_submit, !busy, kind = ActionKind.Confirmed, confirmText = R.string.business_confirm) { submit(action) }
        }
    }
}
