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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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

/** One office page, rendered inside the shell owned by the caller ([ManagementNavigation]); [destination] is required. */
@Composable
internal fun OfficeHomeNav(
    state: JSONObject?,
    busy: Boolean,
    identity: String,
    focusedNotice: String?,
    pushReady: Boolean?,
    openLeague: (JSONObject, String) -> Unit,
    request: (JSONObject) -> Unit,
    destination: OfficeDestination,
    modifier: Modifier = Modifier,
    connectionContent: (@Composable () -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenEvent: ((String) -> Unit)? = null,
    onOpenFlow: ((String) -> Unit)? = null,
) {
    ManagerScreenScaffold(stringResource(destination.label), busy, modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SPACING_L),
            verticalArrangement = Arrangement.spacedBy(SPACING_L),
        ) {
            key(destination) {
                if (state == null && destination != OfficeDestination.Settings && connectionContent != null) {
                    DisconnectedNotice { onOpenSettings?.invoke() }
                }
                when (destination) {
                    OfficeDestination.Requests -> OfficeCard(state, busy, identity, showTitle = false, send = request, onOpenEvent = onOpenEvent)
                    OfficeDestination.Approvals -> OfficeCard(state, busy, identity, approvals = true, showTitle = false, send = request, onOpenFlow = onOpenFlow)
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
}

@Composable
private fun DisconnectedNotice(openSettings: () -> Unit) {
    ManagerCard(title = stringResource(R.string.office_disconnected_title), helper = stringResource(R.string.office_disconnected_help)) {
        Button(onClick = openSettings, modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)) {
            Text(stringResource(R.string.office_disconnected_action))
        }
    }
}
