package cn.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.jlucraft.manager.R
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

internal fun identityConfig(text: String, directory: File): JSONObject {
    val config = JSONObject(text)
    val pin = config.getString("genesis_hash")
    require(pin.matches(Regex("[a-f0-9]{64}")))
    require(config.getString("address").isNotBlank() && config.getString("peer").isNotBlank())
    config.getJSONObject("genesis")
    require(directory.isDirectory || directory.mkdirs())
    return config.put("log", File(directory, "$pin.jsonl").absolutePath)
}
internal fun identityMember(state: JSONObject, device: String): MemberIdentityView? {
    val members = state.getJSONObject("members")
    val id = members.keys().asSequence().firstOrNull { id ->
        members.getJSONArray(id).strings().contains(device)
    } ?: return null
    val clubs = state.getJSONArray("memberships").let { values ->
        (0 until values.length()).map { values.getJSONArray(it) }.filter { it.getString(0) == id }.map { it.getString(1) }.toSet()
    }
    val chairs = state.getJSONObject("chairs")
    val permissions = state.getJSONObject("permissions")
    return MemberIdentityView(id, members.getJSONArray(id).strings(), clubs,
        chairs.keys().asSequence().filter { chairs.getString(it) == id }.toSet(),
        permissions.keys().asSequence().map { permissions.getJSONObject(it) }
            .filter { it.getString("member") == id && !it.getBoolean("revoked") }
            .map { "${it.getString("service")}: ${it.getString("permission")}" }.toSet())
}
private fun JSONArray.strings(): Set<String> = (0 until length()).map { getString(it) }.toSet()

@Composable
internal fun IdentityCard(
    config: String, device: String, busy: Boolean, directory: File,
    onConfig: (String) -> Unit,
    execute: (JSONObject, (JSONObject) -> Unit) -> Unit,
) {
    var configuration by remember(config) { mutableStateOf(config) }
    var proposal by remember { mutableStateOf("") }
    var state by remember(config, device) { mutableStateOf(IdentityState()) }
    var invalid by remember { mutableStateOf(false) }
    fun run(operation: String) {
        try {
            val input = JSONObject().put("config", identityConfig(config, directory)).put("operation", operation)
            if (operation == "draft") input.put("action", JSONObject(proposal))
            if (operation == "sign" || operation == "sign_offline" || operation == "submit") input.put("record", JSONObject(proposal))
            val previous = state
            state = state.syncing(); invalid = false
            execute(input) { result ->
                if (result.has("error")) state = state.failed()
                else if (operation == "draft" || operation == "sign" || operation == "sign_offline") {
                    proposal = result.toString(2)
                    state = previous
                } else {
                    try { state = state.received(identityMember(result, device)) }
                    catch (_: Exception) { state = state.failed() }
                }
            }
        } catch (_: Exception) { invalid = true }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.identity_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.identity_explanation))
            OutlinedTextField(configuration, { configuration = it }, label = { Text(stringResource(R.string.identity_configuration)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = {
                try { onConfig(identityConfig(configuration, directory).toString()); invalid = false }
                catch (_: Exception) { invalid = true }
            }, enabled = !busy) { Text(stringResource(R.string.identity_save)) }
            Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { run("sync") }, enabled = !busy && config.isNotBlank()) { Text(stringResource(R.string.identity_sync)) }
            Text(stringResource(when (state.availability) {
                IdentityAvailability.Unconfigured -> R.string.identity_empty
                IdentityAvailability.Syncing -> R.string.identity_syncing
                IdentityAvailability.Current -> R.string.identity_current
                IdentityAvailability.Unavailable -> R.string.identity_unavailable
            }))
            state.member?.let { member ->
                IdentifierDetails(member.memberId)
                IdentifierDetails(member.clubs.joinToString())
                Text(stringResource(R.string.identity_chairs, member.chairOf.joinToString()))
                Text(stringResource(R.string.identity_permissions, member.permissions.joinToString()))
            }
            if (state.availability == IdentityAvailability.Current && state.member == null) Text(stringResource(R.string.identity_unbound))
            OutlinedTextField(proposal, { proposal = it }, label = { Text(stringResource(R.string.identity_proposal)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.identity_review))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { run("draft") }, enabled = !busy && config.isNotBlank() && proposal.isNotBlank()) { Text(stringResource(R.string.identity_draft)) }
                Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { run("sign") }, enabled = !busy && config.isNotBlank() && proposal.isNotBlank()) { Text(stringResource(R.string.identity_sign)) }
                Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { run("submit") }, enabled = !busy && config.isNotBlank() && proposal.isNotBlank()) { Text(stringResource(R.string.identity_submit)) }
            }
            Button(modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), onClick = { run("sign_offline") }, enabled = !busy && config.isNotBlank() && proposal.isNotBlank()) { Text(stringResource(R.string.identity_sign_offline)) }
            if (invalid) Text(stringResource(R.string.identity_invalid), color = MaterialTheme.colorScheme.error)
        }
    }
}
