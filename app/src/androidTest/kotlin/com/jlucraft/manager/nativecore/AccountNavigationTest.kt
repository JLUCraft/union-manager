package com.jlucraft.manager.nativecore

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Account pages are children of the one workspace connection, never a second organization switch. */
@RunWith(AndroidJUnit4::class)
class AccountNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(text(id))

    /**
     * The founder roster is defined by the server source and immutable once published, so the
     * settings menu must never offer an authoring entry for it again.
     */
    @Test fun settingsMenuOffersNoFounderRosterAuthoring() {
        compose.setContent {
            JluManagerTheme {
                AccountHomeNav(
                    state = null, busy = false, identity = "device", configured = true, pushReady = null,
                    onOpenIdentity = {}, onOpenIssuance = {}, onOpenPersonnel = {}, onOpenConnection = {},
                    onOpenDevice = {}, onOpenAdmin = {}, onOpenAudit = {}, onOpenHelp = {},
                )
            }
        }
        compose.onAllNodesWithText("创始", substring = true).assertCountEquals(0)
        node(R.string.account_menu_identity).assertExists()
        node(R.string.identity_issuance_title).assertExists()
        node(R.string.help_who_title).assertExists()
    }

    @Test fun accountSubpagesOpenFromTheWorkspaceWithoutReconnecting() {
        var destination by mutableStateOf(WorkspaceDestination.Home)
        compose.setContent {
            JluManagerTheme {
                WorkspaceNavigation(destination, false, { destination = it }) {
                    val stack = rememberNavBackStack(WorkspaceRoot)
                    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                    NavDisplay(
                        backStack = stack,
                        onBack = back,
                        entryProvider = entryProvider {
                            entry<WorkspaceRoot> {
                                if (destination == WorkspaceDestination.Settings) {
                                    AccountHomeNav(
                                        state = null,
                                        busy = false,
                                        identity = "device",
                                        configured = true,
                                        pushReady = null,
                                        onOpenIdentity = {},
                                        onOpenIssuance = {},
                                        onOpenPersonnel = {},
                                        onOpenConnection = {},
                                        onOpenDevice = { stack.add(AccountDevice) },
                                        onOpenAdmin = {},
                                        onOpenAudit = {},
                                        onOpenHelp = {},
                                    )
                                } else Text("workspace-root-content")
                            }
                            entry<AccountDevice> { AccountDevicePage("", false) { back() } }
                        },
                    )
                }
            }
        }

        compose.onNodeWithText("workspace-root-content").assertExists()
        compose.onNodeWithText(text(R.string.workspace_nav_settings)).performClick()
        compose.onNodeWithText(text(R.string.account_title)).assertExists()
        compose.onNodeWithText(text(R.string.workspace_nav_settings)).assertIsSelected()

        compose.onNodeWithText(text(R.string.device_identity_title)).scrollToIfScrollable().performClick()
        compose.onNodeWithText(text(R.string.device_identity_empty)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.navigation_back)).performClick()
        compose.onNodeWithText(text(R.string.account_title)).assertExists()
    }
}
