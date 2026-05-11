package com.jlucraft.console.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Dashboard      : AppRoute
    @Serializable data object Instances      : AppRoute
    @Serializable data object League         : AppRoute
    @Serializable data object Governance     : AppRoute
    @Serializable data object AuditLog       : AppRoute
    @Serializable data object Settings       : AppRoute
    @Serializable data object Season         : AppRoute
    @Serializable data object Alerts         : AppRoute
    @Serializable data object NodeScores     : AppRoute
    @Serializable data object DidResolution  : AppRoute
    @Serializable data object Devices        : AppRoute
    @Serializable data object Scheduling     : AppRoute
}

data class TabSpec(
    val route: AppRoute,
    val title: String,
    val icon: ImageVector,
    val iconOutlined: ImageVector,
)

val Tabs = listOf(
    TabSpec(AppRoute.Dashboard,  "控制台", Icons.Filled.Dashboard,   Icons.Outlined.Dashboard),
    TabSpec(AppRoute.Instances,  "实例",   Icons.Filled.Storage,     Icons.Outlined.Storage),
    TabSpec(AppRoute.League,     "联赛",   Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    TabSpec(AppRoute.Governance, "治理",   Icons.Filled.Gavel,       Icons.Outlined.Gavel),
    TabSpec(AppRoute.AuditLog,   "审计",   Icons.Filled.Policy,      Icons.Outlined.Policy),
    TabSpec(AppRoute.Settings,   "设置",   Icons.Filled.Settings,    Icons.Outlined.Settings),
)
