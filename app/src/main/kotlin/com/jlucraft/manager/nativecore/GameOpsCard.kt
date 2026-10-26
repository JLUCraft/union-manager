package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.jlucraft.manager.R
import org.json.JSONObject

@Composable
internal fun GameOpsContent(
    target: GameOpsTarget, onTarget: (GameOpsTarget) -> Unit,
    busy: Boolean, state: GameOpsState,
    onSelect: (String) -> Unit, onRun: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val enabled = !busy && target.complete
    ManagerCard(title = stringResource(R.string.ops_title), helper = stringResource(R.string.ops_description)) {
        OutlinedTextField(target.address, { onTarget(target.copy(address = it)) }, label = { Text(stringResource(R.string.ops_address)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(target.peer, { onTarget(target.copy(peer = it)) }, label = { Text(stringResource(R.string.ops_peer)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(target.service, { onTarget(target.copy(service = it)) }, label = { Text(stringResource(R.string.assistant_service)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onRun("list_instances") }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.ops_refresh)) }
        if (busy) Text(stringResource(R.string.ops_loading))
        if (state.instances.isEmpty() && !busy) Text(stringResource(R.string.ops_empty))
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = enabled && state.instances.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
                Text(state.selected.ifEmpty { stringResource(R.string.ops_select) })
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                state.instances.forEach { instance ->
                    key(instance.id) {
                        DropdownMenuItem(text = { Text(instance.id) }, onClick = { onSelect(instance.id); menu = false })
                    }
                }
            }
        }
        state.instances.firstOrNull { it.id == state.selected }?.let { instance ->
            val stateLabel = when (instance.state) {
                "stopped" -> R.string.ops_stopped; "starting" -> R.string.ops_starting
                "running" -> R.string.ops_running; "stopping" -> R.string.ops_stopping
                "busy" -> R.string.ops_busy; else -> R.string.ops_error_state
            }
            if (instance.version.isBlank()) {
                Text(stringResource(stateLabel))
            } else {
                Text(stringResource(R.string.ops_instance_status, stringResource(stateLabel), instance.version, instance.online, instance.maximum))
            }
        }
        val actions = listOf("status" to R.string.ops_status, "start" to R.string.ops_start, "stop" to R.string.ops_stop,
            "restart" to R.string.ops_restart, "kill" to R.string.ops_kill, "logs" to R.string.ops_logs)
        // One filled action per card (refresh); operations stay outlined, kill is destructive with confirmation.
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SPACING_S), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (op, label) ->
                    ActionButton(label, enabled && state.selected.isNotBlank(),
                        kind = if (op == "kill") ActionKind.Destructive else ActionKind.Secondary,
                        modifier = Modifier.weight(1f)) { onRun(op) }
                }
            }
        }
        if (state.failed) Text(stringResource(R.string.ops_error), color = MaterialTheme.colorScheme.error)
        state.logs?.takeIf { it.isNotEmpty() }?.let { logs ->
            DetailSurface {
                SelectionContainer {
                    Text(logs, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
