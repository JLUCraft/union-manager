package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private data class ReplicaDraft(val rowId: String = UUID.randomUUID().toString(), val id: String = "", val peer: String = "", val addresses: String = "")

/** One consensus backup server this change would affect, classified against the committed set. */
private enum class ReplicaChange { Added, Changed, Removed }

private data class ReplicaDelta(val id: String, val change: ReplicaChange)

/**
 * The committed and drafted consensus backup servers reduced to the identifiers this submit would
 * change. Identifiers are the unit the confirmation names, so the operator reads exactly which
 * servers are added, modified or removed before the destructive button can be reached.
 */
private fun replicaDelta(committed: JSONObject, drafted: List<ReplicaDraft>): List<ReplicaDelta> {
    fun addresses(value: String): String =
        value.lines().map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString("\n")
    val before = committed.keys().asSequence().associateWith { id ->
        val node = committed.optJSONObject(id)
        JSONObject().put("peer", node?.optString("peer").orEmpty())
            .put("addresses", node?.optJSONArray("addresses")?.strings()?.sorted()?.joinToString("\n").orEmpty())
    }
    val after = drafted.filter { it.id.isNotBlank() }.associateBy { it.id.trim() }
    return buildList {
        after.forEach { (id, row) ->
            val draftedValue = JSONObject().put("peer", row.peer.trim()).put("addresses", addresses(row.addresses))
            val committedValue = before[id]
            when {
                committedValue == null -> add(ReplicaDelta(id, ReplicaChange.Added))
                committedValue.toString() != draftedValue.toString() -> add(ReplicaDelta(id, ReplicaChange.Changed))
            }
        }
        before.keys.filter { it !in after }.forEach { add(ReplicaDelta(it, ReplicaChange.Removed)) }
    }.sortedWith(compareBy({ it.change.ordinal }, { it.id }))
}

private fun ReplicaChange.label(): Int = when (this) {
    ReplicaChange.Added -> R.string.business_change_added
    ReplicaChange.Changed -> R.string.business_change_changed
    ReplicaChange.Removed -> R.string.business_change_removed
}

private fun ReplicaChange.confirmLabel(): Int = when (this) {
    ReplicaChange.Added -> R.string.business_confirm_added
    ReplicaChange.Changed -> R.string.business_confirm_changed
    ReplicaChange.Removed -> R.string.business_confirm_removed
}

/**
 * Consensus backup servers only; member roles live on the personnel pages and none of this copy
 * ever mentions them.
 *
 * The editable list and the read-only change preview are two separate states: editing drafts the
 * request, preview builds the exact action, and only a preview can be submitted. The confirmation
 * itself names every affected server identifier, makes the submit a solid danger button and keeps
 * cancel a secondary outline action.
 */
@Composable
internal fun BusinessAdminCard(
    state: JSONObject?,
    device: String,
    busy: Boolean,
    bottomAction: BottomActionSlot? = null,
    submit: (JSONObject) -> Unit,
) {
    if (state == null || !state.optJSONArray("admins").containsString(device)) return
    var expanded by remember { mutableStateOf(false) }
    var replicas by remember { mutableStateOf(listOf<ReplicaDraft>()) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    fun prepare(request: JSONObject) {
        try { preview = JSONObject(UnionNative.management(request.toString())); invalid = false }
        catch (_: Exception) { preview = null; invalid = true }
    }
    fun changeReplicas(next: List<ReplicaDraft>) { replicas = next; preview = null; invalid = false }
    fun editReplica(rowId: String, edit: (ReplicaDraft) -> ReplicaDraft) =
        changeReplicas(replicas.map { if (it.rowId == rowId) edit(it) else it })
    val committed = state.optJSONObject("consensus_members") ?: JSONObject()
    val delta = replicaDelta(committed, replicas)
    // The pinned bar holds the one forward action of the current state: build the preview while
    // editing, confirm while reviewing. Both used to sit at the bottom of the scrolling card, where
    // an open keyboard hid them.
    val buildPreview: () -> Unit = {
        try {
            val members = JSONArray()
            replicas.forEach { row ->
                members.put(
                    JSONObject().put("id", row.id.trim()).put(
                        "node",
                        JSONObject().put("peer", row.peer.trim())
                            .put("addresses", JSONArray(row.addresses.lines().map { it.trim() }.filter { it.isNotEmpty() })),
                    ),
                )
            }
            prepare(JSONObject().put("operation", "build_consensus").put("members", members))
        } catch (_: Exception) { preview = null; invalid = true }
    }
    val forwardAction: @Composable () -> Unit = {
        if (preview == null) Button(
            onClick = buildPreview,
            enabled = !busy && replicas.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        ) { Text(stringResource(R.string.business_replicas_preview)) }
        else Button(
            onClick = { confirming = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        ) { Text(stringResource(R.string.business_submit)) }
    }
    bottomAction?.set {
        if (!expanded) return@set
        forwardAction()
    }
    ManagerCard(title = stringResource(R.string.business_admin_title), helper = stringResource(R.string.business_admin_helper)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(if (expanded) R.string.advanced_hide else R.string.business_admin_open))
        }
        if (!expanded) return@ManagerCard
        if (preview == null) {
            Text(stringResource(R.string.business_replicas_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.business_replicas_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The committed set and the draft are distinct facts: an empty committed set never
            // means the draft the operator already typed is empty.
            if (committed.length() == 0) Text(
                stringResource(if (replicas.isEmpty()) R.string.business_replicas_empty else R.string.business_replicas_empty_draft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else {
                Text(
                    stringResource(R.string.business_replicas_committed, committed.length()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                committed.keys().asSequence().sorted().forEach { id ->
                    key(id) {
                        val node = committed.optJSONObject(id)
                        DetailSurface {
                            Text(id, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                            node?.optString("peer")?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                ActionButton(R.string.business_replicas_load, !busy, kind = ActionKind.Secondary) {
                    changeReplicas(committed.keys().asSequence().map { id ->
                        val node = committed.getJSONObject(id)
                        ReplicaDraft(
                            id = id,
                            peer = node.getString("peer"),
                            addresses = node.getJSONArray("addresses").strings().joinToString("\n"),
                        )
                    }.toList())
                }
            }
            replicas.forEach { row ->
                key(row.rowId) {
                    OutlinedTextField(row.id, { value -> editReplica(row.rowId) { it.copy(id = value) } }, enabled = !busy, label = { Text(stringResource(R.string.business_replica_id)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.peer, { value -> editReplica(row.rowId) { it.copy(peer = value) } }, enabled = !busy, label = { Text(stringResource(R.string.business_replica_peer)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(row.addresses, { value -> editReplica(row.rowId) { it.copy(addresses = value) } }, enabled = !busy, label = { Text(stringResource(R.string.business_replica_addresses)) }, modifier = Modifier.fillMaxWidth())
                    TextButton(
                        enabled = !busy,
                        onClick = { changeReplicas(replicas.filter { it.rowId != row.rowId }) },
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    ) { Text(stringResource(R.string.business_replica_remove)) }
                }
            }
            ActionButton(R.string.business_replica_add, !busy, kind = ActionKind.Secondary) {
                changeReplicas(replicas + ReplicaDraft())
            }
        } else {
            Text(stringResource(R.string.business_preview_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.business_preview_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (delta.isEmpty()) Text(
                stringResource(R.string.business_preview_unchanged),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else ManagerCard(emphasized = false) {
                ReplicaChange.entries.forEach { change ->
                    val rows = delta.filter { it.change == change }
                    if (rows.isEmpty()) return@forEach
                    Text(
                        stringResource(change.label(), rows.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (change == ReplicaChange.Removed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    rows.forEach { row ->
                        key(row.id, change) {
                            Text(row.id, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            // A delegated property is not stable enough to smart-cast; bind it once for this branch.
            val previewAction: JSONObject = preview ?: JSONObject()
            IdentifierDetails(previewAction.toString(2))
            ActionButton(R.string.business_preview_back, !busy, kind = ActionKind.Secondary) {
                preview = null
                invalid = false
            }
        }
        if (invalid) Text(stringResource(R.string.business_invalid), color = MaterialTheme.colorScheme.error)
        if (bottomAction == null) forwardAction()
    }
    // Read the delegated state once: a snapshot local is what makes the null check a real cast.
    val action: JSONObject = preview ?: return
    if (!confirming) return
    AlertDialog(
        onDismissRequest = { confirming = false },
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.business_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING_S)) {
                Text(stringResource(R.string.business_confirm))
                if (delta.isNotEmpty()) {
                    HorizontalDivider()
                    delta.forEach { row ->
                        key("confirm", row.id, row.change) {
                            Text(
                                stringResource(row.change.confirmLabel(), row.id),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.change == ReplicaChange.Removed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { confirming = false; submit(action) },
                enabled = !busy,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.business_submit)) }
        },
        dismissButton = {
            OutlinedButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(R.string.confirm_dismiss))
            }
        },
    )
}
