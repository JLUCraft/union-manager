package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONObject

internal enum class OfficeDestination(val label: Int, val icon: ImageVector) {
    Requests(R.string.office_title, Icons.Default.List),
    Approvals(R.string.office_flows, Icons.Default.CheckCircle),
    Workflows(R.string.navigation_activities, Icons.Default.DateRange),
    Settings(R.string.navigation_account, Icons.Default.Person),
}

@Composable
internal fun OfficeHomeNav(
    state: JSONObject?,
    busy: Boolean,
    identity: String,
    focusedNotice: String?,
    pushReady: Boolean?,
    openLeague: (JSONObject, String) -> Unit,
    request: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
    connectionContent: (@Composable () -> Unit)? = null,
    destination: OfficeDestination? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    var selected by rememberSaveable { mutableStateOf(OfficeDestination.Requests) }
    LaunchedEffect(focusedNotice) {
        if (focusedNotice != null) selected = OfficeDestination.Workflows
    }
    val content: @Composable () -> Unit = {
        key(destination ?: selected) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SPACING_L),
                verticalArrangement = Arrangement.spacedBy(SPACING_L),
            ) {
                val shown = destination ?: selected
                if (state == null && shown != OfficeDestination.Settings && connectionContent != null) {
                    DisconnectedNotice { onOpenSettings?.invoke() ?: run { selected = OfficeDestination.Settings } }
                }
                when (shown) {
                    OfficeDestination.Requests -> OfficeCard(state, busy, identity, send = request)
                    OfficeDestination.Approvals -> OfficeCard(state, busy, identity, approvals = true, send = request)
                    OfficeDestination.Workflows -> OfficeWorkflowsCard(state, busy, identity, focusedNotice, openLeague, request)
                    OfficeDestination.Settings -> {
                        connectionContent?.invoke()
                        pushReady?.let {
                            Text(stringResource(if (it) R.string.office_push_ready else R.string.office_push_pending),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (destination != null) content() else NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            OfficeDestination.entries.filter { it != OfficeDestination.Settings || connectionContent != null }.forEach { destination ->
                item(selected = selected == destination, enabled = !busy, onClick = { selected = destination },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.label)) })
            }
        },
        content = content,
    )
}

@Composable
private fun DisconnectedNotice(openSettings: () -> Unit) {
    ManagerCard(title = stringResource(R.string.office_disconnected_title), helper = stringResource(R.string.office_connect)) {
        Button(onClick = openSettings, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.office_disconnected_action))
        }
    }
}
