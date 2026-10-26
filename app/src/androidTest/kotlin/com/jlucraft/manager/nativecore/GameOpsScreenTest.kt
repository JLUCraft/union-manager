package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameOpsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(text(id))
    private val target = GameOpsTarget("address", "peer", "service")

    @Test fun missingTargetShowsEmptyStateAndDisablesOperations() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                GameOpsContent(GameOpsTarget(), {}, false, GameOpsState(), {}, { error("must not execute") })
            } }
        }
        node(R.string.ops_empty).assertExists()
        node(R.string.ops_refresh).assertIsNotEnabled()
        node(R.string.ops_start).assertIsNotEnabled()
    }
    @Test fun refreshSelectAndLogsFollowActualCallbacks() {
        var state by mutableStateOf(GameOpsState())
        val calls = mutableListOf<String>()
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                GameOpsContent(target, {}, false, state, { state = state.select(it) }, { operation ->
                    calls.add(operation)
                    state = state.receive(operation, GameOpsResult(true,
                        listOf(GameInstance("survival", "running", "1.21", 2, 20)),
                        if (operation == "logs") "fixture server log" else null))
                })
            } }
        }
        node(R.string.ops_refresh).scrollToIfScrollable().performClick()
        node(R.string.ops_select).scrollToIfScrollable().performClick()
        compose.onNodeWithText("survival").performClick()
        node(R.string.ops_logs).scrollToIfScrollable().performClick()
        compose.onNodeWithText("fixture server log").assertExists()
        compose.runOnIdle { assertEquals(listOf("list_instances", "logs"), calls); assertEquals("survival", state.selected) }
    }
    @Test fun loadingPreventsRepeatedActionsAndTargetEdits() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                GameOpsContent(target, { error("must not edit") }, true,
                    GameOpsState(listOf(GameInstance("survival", "stopped", "", 0, 0)), "survival"),
                    {}, { error("must not execute") })
            } }
        }
        node(R.string.ops_loading).assertExists()
        node(R.string.ops_refresh).assertIsNotEnabled()
        node(R.string.ops_kill).assertIsNotEnabled()
        node(R.string.ops_peer).assertIsNotEnabled()
    }
    @Test fun failedResponseShowsErrorWithoutInventingInstances() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                GameOpsContent(target, {}, false, GameOpsState(failed = true), {}, {})
            } }
        }
        node(R.string.ops_error).assertExists()
        node(R.string.ops_empty).assertExists()
        node(R.string.ops_select).assertIsNotEnabled()
    }
}
