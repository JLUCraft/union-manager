package com.jlucraft.manager.nativecore

import android.content.ClipboardManager
import android.hardware.biometrics.BiometricManager
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RequiresFixture
@RunWith(AndroidJUnit4::class)
class BiometricVaultTest {
    companion object {
        @JvmStatic @BeforeClass fun requireEnrolledTestDevice() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assumeTrue(InstrumentationRegistry.getArguments().getString("biometricVault") == "true")
            assumeTrue(context.getSystemService(BiometricManager::class.java).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
        }
    }
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private fun publicIdentityAfterAuthentication(): String {
        compose.onNodeWithText(text(R.string.management_unlock)).performClick()
        // The host script provides virtual sensor touches, never an authentication bypass.
        compose.waitUntil(60_000) { compose.onAllNodesWithText(text(R.string.navigation_account)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.navigation_account)).performClick()
        compose.onNodeWithText(text(R.string.device_identity_copy)).performScrollTo().performClick()
        var exported = ""
        compose.runOnIdle { exported = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString() }
        val value = JSONObject(exported)
        assertEquals(setOf("peer", "public_key"), value.keys().asSequence().toSet())
        return value.getString("peer")
    }
    @Test fun biometricIdentityIsEncryptedAndStableAcrossRecreation() {
        val first = publicIdentityAfterAuthentication()
        val directory = compose.activity.noBackupFilesDir
        val encrypted = File(directory, "union-device.enc")
        assertTrue(encrypted.isFile)
        val permissionMask = OsConstants.S_IRWXU or OsConstants.S_IRWXG or OsConstants.S_IRWXO
        assertEquals(OsConstants.S_IRUSR or OsConstants.S_IWUSR, Os.stat(encrypted.path).st_mode and permissionMask)
        compose.activityRule.scenario.recreate()
        val next = publicIdentityAfterAuthentication()
        assertEquals(first, next)
    }
}
