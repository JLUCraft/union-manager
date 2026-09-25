package cn.jlucraft.manager.nativecore

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
import cn.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Test fun identifiersAreCollapsedAndControlsMeetTouchMinimum() {
        val identifier = "bafy-test-identifier-full-content"
        compose.setContent { MaterialTheme { Column { IdentifierDetails(identifier) } } }
        compose.onNodeWithText(identifier).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.identifier_details)).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(identifier).assertExists()
        compose.onNodeWithText(text(R.string.identifier_copy)).assertHeightIsAtLeast(48.dp)
    }
    @Test fun filesRequireConnectedStateBeforeImportOrExport() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OfficeFilesCard(null, false) { error("must not upload") }
        } } }
        compose.onNodeWithText(text(R.string.office_files_upload)).assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(text(R.string.office_files_export)).assertIsNotEnabled()
    }
    @Test fun aiTemplateShowsConfiguredExecutorAndPurpose() {
        val template = JSONObject("""{"name":"Review","payload_kinds":["events.approve"],"stages":[{"name":"Summary","approver":{"type":"ai","worker":"fixture-worker","model":"fixture-model","purpose":"events.summary"},"timeout_ms":null,"on_timeout":"reject"}]}""")
        compose.setContent { MaterialTheme { Column { FlowTemplatePreview(template) } } }
        compose.onNodeWithText("fixture-worker", substring = true).assertExists()
        compose.onNodeWithText("events.summary", substring = true).assertExists()
    }
}
