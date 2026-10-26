package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.jlucraft.manager.R

enum class WorkspaceDestination(val label: Int, val icon: ImageVector) {
    Home(R.string.workspace_nav_home, Icons.Default.Home),
    Activities(R.string.workspace_nav_activities, Icons.Default.DateRange),
    Tasks(R.string.workspace_nav_tasks, Icons.Default.CheckCircle),
    Settings(R.string.workspace_nav_settings, Icons.Default.Settings),
}

private val NAVIGATION_DRAWER_WIDTH = 220.dp
private val NAVIGATION_DIVIDER_WIDTH = 1.dp

/**
 * The one responsive navigation shell.
 *
 * All four primary destinations are mounted inside this single shell, so the compact bottom bar and
 * the expanded permanent drawer are present on every one of them and no primary page can become a
 * dead end without a way back to the others.
 *
 * The shell places the navigation surface and the page content as sibling panes instead of
 * delegating to a scaffold that lets page content fill the same area. A page's own opaque background
 * therefore cannot cover the navigation items, which previously left the tasks and settings
 * destinations with no navigation at all.
 *
 * The shell adds no IME padding of its own. Each page's scaffold already applies the
 * window insets once; applying them here as well shifted the whole shell up while the page shrank,
 * and pushed the page title bar off screen, leaving the primary submit button behind the
 * software keyboard.
 */
@Composable
fun WorkspaceNavigation(
    destination: WorkspaceDestination,
    busy: Boolean,
    onNavigate: (WorkspaceDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val expanded = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.width(NAVIGATION_DRAWER_WIDTH).fillMaxHeight()
                    .safeDrawingPadding().verticalScroll(rememberScrollState()),
            ) {
                WorkspaceDestination.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(item.label)) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = destination == item,
                        onClick = { if (!busy) onNavigate(item) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SPACING_S, vertical = SPACING_XS),
                    )
                }
            }
            HorizontalDivider(Modifier.fillMaxHeight().width(NAVIGATION_DIVIDER_WIDTH))
            // The expanded shell needs the same one-time IME reservation as the compact one: without
            // it a pinned action bar stays at the window bottom and the keyboard covers it. The page
            // receives neither inset afterwards, so nothing is applied twice.
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .imePadding()
                    .consumeWindowInsets(WindowInsets.ime),
            ) { content() }
        }
    } else {
        // Edge-to-edge on this target keeps the IME as a reported inset instead of a window
        // resize, so the keyboard is applied exactly once here: the bottom navigation rides above
        // the keyboard, and the page below receives neither the IME nor the navigation-bar inset
        // because this shell already reserved both. Applying them twice is what previously pushed
        // the page title bar off screen and hid the primary submit button behind the keyboard.
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .consumeWindowInsets(WindowInsets.navigationBars)
                    .consumeWindowInsets(WindowInsets.ime),
            ) { content() }
            NavigationBar {
                WorkspaceDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { if (!busy) onNavigate(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        }
    }
}
