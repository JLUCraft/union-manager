package com.jlucraft.manager.nativecore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performScrollTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceIdentityTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exportsOnlyPublicMaterialWithoutServerConfiguration() {
        val secret = UnionNative.generateSecret()
        val exported = try {
            val text = devicePublicIdentity(secret)
            val value = JSONObject(text)
            assertEquals(setOf("peer", "public_key"), value.keys().asSequence().toSet())
            assertEquals(UnionNative.peerId(secret), value.getString("peer"))
            assertEquals(JSONArray(UnionNative.publicKey(secret)).toString(), value.getJSONArray("public_key").toString())
            assertTrue(value.getJSONArray("public_key").length() > 0)
            assertEquals(text, devicePublicIdentity(secret))
            text
        } finally { secret.fill(0) }
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DeviceIdentityCard(exported) } } }
        compose.onNodeWithText(context.getString(R.string.identifier_details)).performClick()
        compose.onNodeWithText(exported).assertExists()
        compose.onNodeWithText(context.getString(R.string.identifier_copy)).performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(exported, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

    @Test fun missingDeviceShowsEmptyState() {
        compose.setContent { MaterialTheme { DeviceIdentityCard("") } }
        compose.onNodeWithText(context.getString(R.string.device_identity_empty)).assertExists()
    }
}
