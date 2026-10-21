package com.jlucraft.manager.nativecore

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable
internal fun PublicJsonActions(value: String, label: Int, enabled: Boolean = true) {
    val context = LocalContext.current
    val labelText = stringResource(label)
    Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(enabled = enabled && value.isNotBlank(), onClick = { copyToClipboard(context, labelText, value) }, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.identity_export_copy))
        }
        OutlinedButton(enabled = enabled && value.isNotBlank(), onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value)
            context.startActivity(Intent.createChooser(send, labelText))
        }, modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.identity_export_share)) }
    }
}

/** The parent retains this public draft while the vault is locked or another app is opened. */
@Composable
internal fun GenesisBuilderCard(draft: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val input = remember(draft) { runCatching { JSONObject(draft) }.getOrElse { JSONObject() } }
    val founders = input.optJSONArray("founders") ?: JSONArray()
    var output by remember(draft) { mutableStateOf<JSONObject?>(null) }
    var invalid by remember(draft) { mutableStateOf(false) }
    fun change(name: String, value: Any) { onChange(JSONObject(input.toString()).put(name, value).toString()) }
    fun changeFounder(id: String, field: String, value: String) {
        val updated = JSONArray()
        for (index in 0 until founders.length()) {
            val row = JSONObject(founders.getJSONObject(index).toString())
            if (row.getString("row_id") == id) row.put(field, value)
            updated.put(row)
        }
        change("founders", updated)
    }
    ManagerCard(title = stringResource(R.string.genesis_builder_title), helper = stringResource(R.string.genesis_builder_helper)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(if (expanded) R.string.genesis_builder_hide else R.string.genesis_builder_open)) }
        if (!expanded) return@ManagerCard
        OutlinedTextField(input.optString("federation"), { change("federation", it) }, label = { Text(stringResource(R.string.genesis_federation)) }, modifier = Modifier.fillMaxWidth())
        for (index in 0 until founders.length()) {
            val row = founders.getJSONObject(index)
            val id = row.getString("row_id")
            key(id) {
                HorizontalDivider()
                OutlinedTextField(row.optString("member"), { changeFounder(id, "member", it) }, label = { Text(stringResource(R.string.identity_field_member)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.optString("club"), { changeFounder(id, "club", it) }, label = { Text(stringResource(R.string.identity_field_club)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.optString("identity"), { changeFounder(id, "identity", it) }, label = { Text(stringResource(R.string.genesis_founder_public)) }, supportingText = { Text(stringResource(R.string.genesis_founder_public_hint)) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    val next = JSONArray()
                    for (i in 0 until founders.length()) if (founders.getJSONObject(i).getString("row_id") != id) next.put(founders.getJSONObject(i))
                    change("founders", next)
                }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.genesis_remove_founder)) }
            }
        }
        OutlinedButton(onClick = { change("founders", JSONArray(founders.toString()).put(JSONObject().put("row_id", UUID.randomUUID().toString()))) }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.genesis_add_founder)) }
        OutlinedTextField(input.optString("quorum"), { change("quorum", it) }, label = { Text(stringResource(R.string.identity_field_quorum)) }, modifier = Modifier.fillMaxWidth())
        ActionButton(R.string.genesis_validate, true, kind = ActionKind.Secondary) {
            try {
                val entries = JSONArray()
                for (index in 0 until founders.length()) {
                    val row = founders.getJSONObject(index)
                    entries.put(JSONObject().put("member", row.optString("member").trim()).put("club", row.optString("club").trim()).put("identity", JSONObject(row.optString("identity"))))
                }
                val request = JSONObject().put("operation", "build_genesis").put("federation", input.optString("federation").trim()).put("quorum", input.optString("quorum").toInt()).put("founders", entries)
                output = JSONObject(UnionNative.management(request.toString())); invalid = false
            } catch (_: Exception) { output = null; invalid = true }
        }
        if (invalid) Text(stringResource(R.string.genesis_invalid), color = MaterialTheme.colorScheme.error)
        output?.let { result ->
            Text(stringResource(R.string.genesis_review), style = MaterialTheme.typography.bodyMedium)
            IdentifierDetails(result.toString(2))
            PublicJsonActions(result.getJSONObject("genesis").toString(2), R.string.genesis_builder_title)
        }
    }
}
