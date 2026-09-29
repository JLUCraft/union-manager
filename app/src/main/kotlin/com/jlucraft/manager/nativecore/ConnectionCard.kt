package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionCard(
    address: String,
    peer: String,
    genesisHash: String,
    identity: String,
    message: String,
    connected: Boolean,
    busy: Boolean,
    onAddressChange: (String) -> Unit,
    onPeerChange: (String) -> Unit,
    onGenesisChange: (String) -> Unit,
    onConnect: () -> Unit,
    onLink: (ConnectionTarget) -> Unit = {},
) {
    var link by rememberSaveable { mutableStateOf("") }
    var linkInvalid by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(connected || address.isNotBlank() || peer.isNotBlank() || genesisHash.isNotBlank()) }
    ManagerCard(
        title = stringResource(R.string.connection_title),
        helper = if (connected) null else stringResource(R.string.connection_help),
        statusText = stringResource(if (connected) R.string.connection_connected else R.string.connection_disconnected),
        statusActive = connected,
    ) {
        if (!connected) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it; linkInvalid = false },
                label = { Text(stringResource(R.string.connection_link)) },
                placeholder = { Text(stringResource(R.string.connection_link_placeholder)) },
                supportingText = { Text(stringResource(if (linkInvalid) R.string.connection_link_invalid else R.string.connection_link_help)) },
                isError = linkInvalid,
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val target = parseConnectionLink(link)
                    linkInvalid = target == null
                    if (target != null) onLink(target)
                },
                enabled = !busy && link.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            ) { Text(stringResource(R.string.connection_link_connect)) }
            TextButton(onClick = { manual = !manual }, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text(stringResource(if (manual) R.string.connection_manual_hide else R.string.connection_manual))
            }
        }
        if (manual || connected) {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text(stringResource(R.string.connection_address)) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = peer,
            onValueChange = onPeerChange,
            label = { Text(stringResource(R.string.connection_peer)) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = genesisHash,
            onValueChange = onGenesisChange,
            label = { Text(stringResource(R.string.genesis_hash_label)) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onConnect, enabled = !busy && address.isNotBlank() && peer.isNotBlank() && GENESIS_HASH_PATTERN.matches(genesisHash), modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.connection_refresh))
        }
        }
        if (identity.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(SPACING_M)) {
                    Text(
                        stringResource(R.string.connection_identity),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        identity,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        if (message.isNotBlank()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
