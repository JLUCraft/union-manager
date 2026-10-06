package com.jlucraft.manager.nativecore

import android.content.ClipboardManager
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagementScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun node(id: Int) = compose.onNodeWithText(text(id))
    @Test fun serviceRegistrationFormUsesRealNativeBuilder() {
        val secret = UnionNative.generateSecret()
        val peer = try { UnionNative.peerId(secret) } finally { secret.fill(0) }
        var result: JSONObject? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { IdentityActionForm(true) { result = it } } } }
        node(R.string.identity_action_confirm_member).performClick()
        node(R.string.identity_action_register_service).performScrollTo().performClick()
        node(R.string.identity_field_audience).performScrollTo().performTextInput(peer)
        node(R.string.identity_field_club).performScrollTo().performTextInput("test-school")
        node(R.string.identity_field_service).performScrollTo().performTextInput("test-service")
        node(R.string.identity_draft).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("register_service", result?.getString("type"))
            assertEquals(peer, result?.getString("audience"))
            assertEquals("test-school", result?.getString("club"))
        }
    }
}
