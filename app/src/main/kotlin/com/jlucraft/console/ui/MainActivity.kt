package com.jlucraft.console.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.console.di.ServiceLocator
import com.jlucraft.console.ui.navigation.AppRoute
import com.jlucraft.console.ui.navigation.Tabs
import com.jlucraft.console.ui.screens.audit.AuditLogScreen
import com.jlucraft.console.ui.screens.dashboard.DashboardScreen
import com.jlucraft.console.ui.screens.governance.GovernanceScreen
import com.jlucraft.console.ui.screens.instances.InstancesScreen
import com.jlucraft.console.ui.screens.league.LeagueScreen
import com.jlucraft.console.ui.screens.settings.SettingsScreen
import com.jlucraft.console.ui.theme.UnionManagerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        ServiceLocator.initActivity(this)
        enableEdgeToEdge()
        setContent {
            UnionManagerTheme {
                UnionManagerApp()
            }
        }
    }
}

@Composable
fun UnionManagerApp() {
    val backStack = rememberNavBackStack(AppRoute.Dashboard)
    val current = backStack.lastOrNull() as? AppRoute ?: AppRoute.Dashboard

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tabs.forEach { tab ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.icon else tab.iconOutlined,
                                contentDescription = tab.title,
                            )
                        },
                        label = { Text(tab.title) },
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                backStack.clear()
                                backStack.add(tab.route)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                entry<AppRoute.Dashboard>  { DashboardScreen() }
                entry<AppRoute.Instances>  { InstancesScreen() }
                entry<AppRoute.League>     { LeagueScreen() }
                entry<AppRoute.Governance> { GovernanceScreen() }
                entry<AppRoute.AuditLog>   { AuditLogScreen() }
                entry<AppRoute.Settings>   { SettingsScreen() }
            }
        )
    }
}
