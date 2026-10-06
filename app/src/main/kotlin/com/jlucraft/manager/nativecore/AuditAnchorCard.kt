package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONObject

@Composable
internal fun AuditAnchorCard(state: JSONObject?, device: String, busy: Boolean, submit: (JSONObject) -> Unit) {
    var id by remember { mutableStateOf("") }
    var digest by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val authorized = state?.optJSONArray("admins").containsString(device)
    ManagerCard(title = stringResource(R.string.audit_anchor_title), helper = stringResource(R.string.audit_anchor_helper)) {
        if (state == null) {
            Text(stringResource(R.string.audit_anchor_disconnected))
            return@ManagerCard
        }
        val anchors = state.optJSONObject("digest_anchors") ?: JSONObject()
        if (anchors.length() == 0) Text(stringResource(R.string.audit_anchor_empty))
        else IdentifierDetails(anchors.toString(2))
        if (!authorized) { Text(stringResource(R.string.audit_anchor_admin)); return@ManagerCard }
        OutlinedTextField(id, { id = it; invalid = false }, enabled = !busy, label = { Text(stringResource(R.string.audit_anchor_id)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(digest, { digest = it; invalid = false }, enabled = !busy, label = { Text(stringResource(R.string.audit_anchor_digest)) }, modifier = Modifier.fillMaxWidth())
        if (invalid) Text(stringResource(R.string.audit_anchor_invalid), color = MaterialTheme.colorScheme.error)
        ActionButton(R.string.audit_anchor_submit, !busy && id.isNotBlank() && digest.isNotBlank(), ActionKind.Confirmed, R.string.audit_anchor_confirm) {
            try {
                val action = UnionNative.management(JSONObject().put("operation", "build_anchor").put("id", id.trim()).put("digest", digest.trim()).toString())
                submit(JSONObject(action)); invalid = false
            } catch (_: Exception) { invalid = true }
        }
    }
}
