package com.jlucraft.console.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jlucraft.console.app.AppServices
import com.jlucraft.console.ui.navigation.AppRoute
import com.jlucraft.console.ui.navigation.OnboardingScreen
import com.jlucraft.console.ui.navigation.Tabs
import com.jlucraft.console.ui.screens.admin.AlertsScreen
import com.jlucraft.console.ui.screens.admin.DeviceScreen
import com.jlucraft.console.ui.screens.admin.DidResolutionScreen
import com.jlucraft.console.ui.screens.admin.NodeScoresScreen
import com.jlucraft.console.ui.screens.admin.SchedulingScreen
import com.jlucraft.console.ui.screens.admin.SeasonScreen
import com.jlucraft.console.ui.screens.audit.AuditLogScreen
import com.jlucraft.console.ui.screens.dashboard.DashboardScreen
import com.jlucraft.console.ui.screens.governance.GovernanceScreen
import com.jlucraft.console.ui.screens.instances.InstancesScreen
import com.jlucraft.console.ui.screens.league.LeagueScreen
import com.jlucraft.console.ui.screens.settings.SettingsScreen
import com.jlucraft.console.ui.theme.UnionManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
public open class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var services: AppServices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureUnionManagerActivity(this, services)
    }
}

fun configureUnionManagerActivity(activity: AppCompatActivity, services: AppServices) {
    val splash = activity.installSplashScreen()
    services.attachActivity(activity)
    activity.enableEdgeToEdge()

    val onboardingReady = AtomicBoolean(false)
    splash.setKeepOnScreenCondition { !onboardingReady.get() }

    var onboardingDone = false

    activity.setContent {
        UnionManagerTheme {
            var showOnboarding by remember { mutableStateOf(!onboardingDone) }

            LaunchedEffect(Unit) {
                onboardingDone = services.settingsStore.isOnboardingCompleted()
                showOnboarding = !onboardingDone
                onboardingReady.set(true)
            }

            if (showOnboarding) {
                OnboardingScreen(
                    teeAuth = services.teeAuthManager,
                    settingsStore = services.settingsStore,
                    onComplete = { showOnboarding = false }
                )
            } else {
                UnionManagerApp(services = services)
            }
        }
    }
}

@Composable
fun UnionManagerApp(services: com.jlucraft.console.app.AppServices) {
    val backStack = rememberNavBackStack(AppRoute.Dashboard)
    val current = backStack.lastOrNull() as? AppRoute ?: AppRoute.Dashboard

    val navigateTo: (AppRoute) -> Unit = { route ->
        backStack.add(route)
    }

    Scaffold(
        bottomBar = {
            // Only show bottom bar for main tabs
            if (current in setOf(AppRoute.Dashboard, AppRoute.Instances, AppRoute.League,
                    AppRoute.Governance, AppRoute.AuditLog, AppRoute.Settings)) {
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
                                backStack.clear()
                                backStack.add(tab.route)
                            }
                        )
                    }
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
                entry<AppRoute.Dashboard>  {
                    Column { DashboardScreen(services = services, onNavigate = navigateTo) }
                }
                entry<AppRoute.Instances>  {
                    Column { InstancesScreen(services = services, onNavigate = navigateTo) }
                }
                entry<AppRoute.League>     { LeagueScreen(services = services) }
                entry<AppRoute.Governance> { GovernanceScreen(services = services) }
                entry<AppRoute.AuditLog>   { AuditLogScreen(services = services) }
                entry<AppRoute.Settings>   { SettingsScreen(services = services, onNavigate = navigateTo) }
                entry<AppRoute.Season>     { SeasonScreen(services = services) }
                entry<AppRoute.Alerts>     { AlertsScreen(services = services) }
                entry<AppRoute.NodeScores> { NodeScoresScreen(services = services) }
                entry<AppRoute.DidResolution> { DidResolutionScreen(services = services) }
                entry<AppRoute.Devices>    { DeviceScreen(services = services) }
                entry<AppRoute.Scheduling> { SchedulingScreen(services = services) }
            }
        )
    }
}
