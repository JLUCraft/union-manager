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

/**
 * Draft brief with its editable copy; saving uses amend, which bumps the event epoch and returns it
 * to draft.
 *
 * [onDocumentState] reports whether the draft content file could be read, so a page that must not
 * sign a missing draft can disable its own signing entry and offer a repair path.
 */
@Composable
internal fun OfficeRequestDetails(
    state: JSONObject,
    id: String,
    event: JSONObject,
    busy: Boolean,
    editable: Boolean,
    send: (JSONObject) -> Unit,
    onSaved: (() -> Unit)? = null,
    submit: FormSender? = null,
    onDocumentState: ((Boolean) -> Unit)? = null,
    bottomAction: BottomActionSlot? = null,
) {
    val submission = rememberFormSubmission()
    val cid = event.optString("brief_hash")
    var document by remember(cid) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(cid) { mutableStateOf(true) }
    var editing by remember(cid) { mutableStateOf(false) }
    var title by remember(cid) { mutableStateOf(event.optString("title")) }
    var description by remember(cid) { mutableStateOf("") }
    var attachment by remember(cid) { mutableStateOf("") }
    val window = event.optJSONObject("window")
    var opens by remember(cid) { mutableStateOf(window?.let { formatLocalDateTime(it.optLong("opens_ms")) }.orEmpty()) }
    var closes by remember(cid) { mutableStateOf(window?.let { formatLocalDateTime(it.optLong("closes_ms")) }.orEmpty()) }
    var starts by remember(cid) { mutableStateOf(window?.let { formatLocalDateTime(it.optLong("starts_ms")) }.orEmpty()) }
    LaunchedEffect(cid, state, editable) {
        document = withContext(Dispatchers.IO) {
            runCatching { JSONObject(String(UnionNative.readFile(state.toString(), cid), Charsets.UTF_8)) }
                .getOrNull()?.takeIf { it.has("description") }
        }
        if (!editing) {
            description = document?.optString("description").orEmpty()
            attachment = document?.optString("attachment")?.takeUnless { it == "null" }.orEmpty()
        }
        if (!editable) editing = false
        else if (document == null) editing = true
        onDocumentState?.invoke(document != null)
        loading = false
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    else if (editing && editable) {
        Text(
            stringResource(R.string.office_draft_edit_notice, event.optLong("epoch") + 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            title,
            { title = it },
            label = { Text(stringResource(R.string.office_name)) },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            description,
            { description = it },
            label = { Text(stringResource(R.string.office_description)) },
            enabled = !busy,
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        OfficeAttachment(state, busy, id, attachment, { attachment = it }, send)
        Text(
            stringResource(R.string.office_window_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DateTimeField(opens, stringResource(R.string.office_opens), !busy) { opens = it }
        DateTimeField(closes, stringResource(R.string.office_closes), !busy) { closes = it }
        DateTimeField(starts, stringResource(R.string.office_starts), !busy) { starts = it }
        val openTime = parseLocalDateTime(opens)
        val closeTime = parseLocalDateTime(closes)
        val startTime = parseLocalDateTime(starts)
        val timesValid = openTime != null && closeTime != null && startTime != null &&
            openTime < closeTime && closeTime <= startTime
        val saveReady = !busy && submit != null && !submission.pending &&
            title.isNotBlank() && description.isNotBlank() && timesValid
        // The editor's own actions: published to the pinned bar when the host provides one, and
        // rendered inline otherwise, so a caller that forgets the slot still gets working buttons.
        val requestActions: @Composable () -> Unit = {
            ActionButton(R.string.office_save_revision, saveReady) {
                submit?.let { sender ->
                val request = JSONObject().put("type", "local.events.amend")
                    .put("id", id)
                    .put("title", title.trim())
                    .put("description", description)
                    .put("attachment", attachment.ifBlank { null } ?: JSONObject.NULL)
                    .put(
                        "window",
                        JSONObject().put("opens_ms", openTime).put("closes_ms", closeTime).put("starts_ms", startTime),
                    )
                // The editor closes only once the accepted snapshot proves this event's newer revision.
                    submission.submit(sender, request) { snapshot ->
                        val amended = snapshot.optJSONObject("events")?.optJSONObject(id)
                        if (amended != null && amended.optLong("epoch") > event.optLong("epoch")) {
                            editing = false
                            onSaved?.invoke()
                            true
                        } else false
                    }
                }
            }
            OutlinedButton(
                onClick = { editing = false },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(R.string.dialog_cancel)) }
        }
        bottomAction?.set { requestActions() }
        submission.failure?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (bottomAction == null) requestActions()
    } else if (document == null) {
        // A missing draft file is an actionable failure, not a dead end: the error comes with the
        // repair entry, and a read-only visitor is told who can repair it.
        ManagerCard(emphasized = false) {
            Text(stringResource(R.string.office_document_missing), color = MaterialTheme.colorScheme.error)
            if (editable) {
                Text(
                    stringResource(R.string.office_document_missing_repair),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButton(R.string.office_document_reupload, !busy, kind = ActionKind.Primary) {
                    editing = true
                }
            } else {
                Text(
                    stringResource(R.string.office_document_missing_readonly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        if (attachment.isNotBlank()) OfficeAttachment(state, busy, id, attachment, {}, send, editable = false)
        if (editable) TextButton(
            onClick = { editing = true },
            enabled = !busy,
            modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
        ) { Text(stringResource(R.string.office_amend)) }
    }
}
