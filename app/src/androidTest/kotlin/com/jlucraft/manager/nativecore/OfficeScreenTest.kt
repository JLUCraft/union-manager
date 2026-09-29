package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Test fun primaryNavigationHasFiveDestinationsAndNoFiles() {
        var selected: OfficeDestination? = null
        compose.setContent { JluManagerTheme {
            ManagementNavigation(true, OfficeDestination.Requests, false, {}, { selected = it }) {}
        } }
        listOf(R.string.office_title, R.string.office_flows, R.string.navigation_activities,
            R.string.mode_league, R.string.navigation_account).forEach {
            compose.onNodeWithText(text(it)).assertExists().assertHasClickAction()
        }
        compose.onNodeWithText(text(R.string.office_files_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_flows)).performClick()
        org.junit.Assert.assertEquals(OfficeDestination.Approvals, selected)
    }
    @Test fun disconnectedOfficeCanOpenConnectionSettings() {
        compose.setContent { JluManagerTheme {
            OfficeHomeNav(null, false, "", null, null, { _, _ -> }, {},
                connectionContent = {
                    ConnectionCard("", "", "", "", "", false, false, {}, {}, {}, {})
                },
            )
        } }
        captureScreen("android-office-01-disconnected-menu")
        compose.onNodeWithText(text(R.string.navigation_account)).performClick()
        compose.onNodeWithText(text(R.string.connection_link)).assertExists()
        compose.onNodeWithText(text(R.string.connection_manual)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.connection_address)).assertExists()
        compose.onNodeWithText(text(R.string.connection_refresh)).performScrollTo().assertIsNotEnabled()
        compose.waitForIdle()
        captureScreen("android-office-02-connection-settings")
    }
    @Test fun identifiersAreCollapsedAndControlsMeetTouchMinimum() {
        val identifier = "bafy-test-identifier-full-content"
        compose.setContent { MaterialTheme { Column { IdentifierDetails(identifier) } } }
        compose.onNodeWithText(identifier).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.identifier_details)).assertHeightIsAtLeast(MIN_TOUCH_TARGET).performClick()
        compose.onNodeWithText(identifier).assertExists()
        compose.onNodeWithText(text(R.string.identifier_copy)).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }
    @Test fun filesRequireConnectedStateBeforeImportOrExport() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeAttachment(null, false, "test-request", "", {}, { error("must not upload") })
        } } }
        compose.onNodeWithText(text(R.string.office_attachment_add)).assertIsNotEnabled().assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        compose.onNodeWithText(text(R.string.office_attachment_download)).assertDoesNotExist()
    }
    @Test fun aiTemplateShowsWorkflowStageWithoutModelControls() {
        val template = JSONObject("""{"name":"Review","payload_kinds":["events.approve"],"stages":[{"name":"Summary","approver":{"type":"ai","mode":"review","worker":"fixture-worker","model":"fixture-model","purpose":"events.summary"},"timeout_ms":null,"on_timeout":"reject"}]}""")
        compose.setContent { MaterialTheme { Column { FlowTemplatePreview(template) } } }
        compose.onNodeWithText("fixture-worker", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_stage_review)).assertExists()
    }
    @Test fun reviewShowsRequiredChangesAsWorkflowFeedback() {
        val flow = JSONObject("""{"proposer":"member","template":"review","template_epoch":1,"status":"changes_requested","records":[{"ai_runs":[{"job":"job","output":"output-cid","error":null,"review":{"decision":"changes_requested","summary":"Consent document is missing","issues":["Attach the club consent document"]}}]}]}""")
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            FlowReview(flow, null, null, false)
        } } }
        compose.onNodeWithText("Consent document is missing").assertExists()
        compose.onNodeWithText("Attach the club consent document").assertExists()
        compose.onNodeWithText(text(R.string.office_revision_required)).assertExists()
        compose.onNodeWithText(text(R.string.office_ai_retry)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.office_approve)).assertDoesNotExist()
    }

}
