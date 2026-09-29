package com.jlucraft.manager.nativecore

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.jlucraft.manager.R

@Composable
internal fun ManagementNavigation(
    office: Boolean,
    destination: OfficeDestination,
    busy: Boolean,
    onLeague: () -> Unit,
    onOffice: (OfficeDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layout = if (adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)) {
        NavigationSuiteType.NavigationDrawer
    } else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    NavigationSuiteScaffold(
        modifier = Modifier.imePadding(),
        layoutType = layout,
        navigationSuiteItems = {
            OfficeDestination.entries.forEach { item ->
                if (item == OfficeDestination.Settings) {
                    item(selected = !office && destination != OfficeDestination.Settings, enabled = !busy || !office, onClick = onLeague,
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text(stringResource(R.string.mode_league)) })
                }
                item(selected = destination == item && (office || item == OfficeDestination.Settings), enabled = !busy || office || item == OfficeDestination.Settings, onClick = { onOffice(item) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(stringResource(item.label)) })
            }
        },
        content = content,
    )
}
