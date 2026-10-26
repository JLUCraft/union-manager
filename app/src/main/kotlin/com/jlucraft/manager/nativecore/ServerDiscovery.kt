package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject

/** A business connection requires the application's own genesis hash. */
internal fun JSONObject.applicationConnectionTarget(): ConnectionTarget? {
    val address = optString("address").takeIf { it.isNotBlank() } ?: return null
    val peer = optString("peer").takeIf { it.isNotBlank() } ?: return null
    val genesis = optJSONObject("application")?.optString("genesis_hash")
        ?.takeIf { GENESIS_HASH_PATTERN.matches(it) } ?: return null
    return ConnectionTarget(address, peer, genesis)
}

/** LAN discovery for servers that offer a business connection. */
@Composable
internal fun ServerDiscovery(
    busy: Boolean,
    discover: ((JSONObject) -> Unit) -> Unit,
    label: Int = R.string.connection_discover,
    onSelect: (ConnectionTarget) -> Unit,
) {
    var discovering by rememberSaveable { mutableStateOf(false) }
    var discoverFailed by rememberSaveable { mutableStateOf(false) }
    var discoverEmpty by rememberSaveable { mutableStateOf(false) }
    var foundJson by rememberSaveable { mutableStateOf("") }
    Button(
        onClick = {
            discovering = true; discoverFailed = false; discoverEmpty = false; foundJson = ""
            discover { result ->
                discovering = false
                if (result.has("error")) { discoverFailed = true; return@discover }
                val announced = result.optJSONArray("servers") ?: JSONArray()
                val servers = JSONArray()
                for (index in 0 until announced.length()) {
                    val server = announced.optJSONObject(index) ?: continue
                    if (server.applicationConnectionTarget() != null) servers.put(server)
                }
                if (servers.length() == 0) discoverEmpty = true else foundJson = servers.toString()
            }
        },
        enabled = !busy && !discovering,
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
    ) { Text(stringResource(label)) }
    if (discovering) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (discoverFailed) Text(stringResource(R.string.connection_discover_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (discoverEmpty) Text(stringResource(R.string.connection_discover_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (foundJson.isNotBlank()) {
        val servers = JSONArray(foundJson)
        Text(stringResource(R.string.connection_discover_found, servers.length()), style = MaterialTheme.typography.labelMedium)
        for (index in 0 until servers.length()) {
            val server = servers.getJSONObject(index)
            val target = server.applicationConnectionTarget() ?: continue
            // Each candidate is a bordered card with an explicit select action, so it reads as
            // something to tap instead of as unbordered blue text.
            ManagerCard(emphasized = false) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SPACING_XS)) {
                    Text(
                        server.optString("federation").ifBlank { stringResource(R.string.identity_none) },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.connection_discover_server_peer, target.peer),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        stringResource(R.string.connection_discover_server_services, server.optJSONArray("services")?.length() ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { onSelect(target); foundJson = "" },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.connection_discover_select)) }
            }
        }
    }
}
