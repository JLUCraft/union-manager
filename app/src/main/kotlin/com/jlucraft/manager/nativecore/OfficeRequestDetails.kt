package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun OfficeRequestDetails(state: JSONObject, id: String, event: JSONObject, busy: Boolean, editable: Boolean, send: (JSONObject) -> Unit) {
    val cid = event.optString("brief_hash")
    var document by remember(cid) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(cid) { mutableStateOf(true) }
    var editing by remember(cid) { mutableStateOf(false) }
    var description by remember(cid) { mutableStateOf("") }
    var attachment by remember(cid) { mutableStateOf("") }
    LaunchedEffect(cid, state) {
        document = withContext(Dispatchers.IO) {
            runCatching { JSONObject(String(UnionNative.readOfficeFile(state.toString(), cid), Charsets.UTF_8)) }
                .getOrNull()?.takeIf { it.has("description") }
        }
        if (!editing) {
            description = document?.optString("description").orEmpty()
            attachment = document?.optString("attachment")?.takeUnless { it == "null" }.orEmpty()
        }
        loading = false
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    else if (document == null) {
        OfficeAttachment(state, busy, id, cid, { replacement ->
            send(JSONObject().put("type", "events.amend").put("id", id).put("brief_hash", replacement))
        }, send, editable)
    } else {
        if (editing) {
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.office_description)) }, enabled = !busy, minLines = 4, modifier = Modifier.fillMaxWidth())
            OfficeAttachment(state, busy, id, attachment, { attachment = it }, send)
            Button(enabled = !busy && description.isNotBlank(), modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = {
                send(JSONObject().put("type", "local.events.amend").put("id", id).put("title", event.getString("title"))
                    .put("description", description).put("attachment", attachment.ifBlank { null } ?: JSONObject.NULL).put("window", event.getJSONObject("window")))
            }) { Text(stringResource(R.string.office_save_revision)) }
        } else {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (attachment.isNotBlank()) OfficeAttachment(state, busy, id, attachment, {}, send, editable = false)
            if (editable) TextButton(onClick = { editing = true }, enabled = !busy, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.office_amend)) }
        }
    }
}
