package com.jlucraft.manager.nativecore

import android.hardware.biometrics.BiometricManager
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import com.jlucraft.manager.BuildConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Actual MainActivity tests. They never substitute a Compose fixture for the activity: the first
 * test exercises the real biometric enrollment, and every later launch must restore that identity
 * from the sealed session without another unlock tap or biometric prompt.
 */
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

    private fun awaitUnlocked() {
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText(text(R.string.management_locked)).fetchSemanticsNodes().isEmpty()
        }
    }

    /** The one initial enrollment the fixture performs; later launches never use this path. */
    private fun authenticateInitially() {
        if (compose.onAllNodesWithText(text(R.string.management_unlock)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(text(R.string.management_unlock)).performClick()
        }
        awaitUnlocked()
    }

    private fun sessionSecret(): ByteArray =
        checkNotNull(BiometricVault(compose.activity).restoreSession())

    private fun sessionPublicIdentity(): String {
        val secret = sessionSecret()
        return try { UnionNative.publicIdentity(secret) } finally { secret.fill(0) }
    }

    private fun sessionPeer(): String {
        val secret = sessionSecret()
        return try { UnionNative.peerId(secret) } finally { secret.fill(0) }
    }

    private fun assertOwnerOnly(file: File) {
        val permissionMask = OsConstants.S_IRWXU or OsConstants.S_IRWXG or OsConstants.S_IRWXO
        assertEquals(OsConstants.S_IRUSR or OsConstants.S_IWUSR, Os.stat(file.path).st_mode and permissionMask)
    }

    @Test fun explicitDebugAcceptanceAuthenticatesWithTheExistingSealedIdentity() {
        assumeTrue(BuildConfig.DEBUG && BuildConfig.SKIP_BIOMETRIC_FOR_ACCEPTANCE)
        val expected = sessionPublicIdentity()
        var authenticated: String? = null
        var failure: String? = null
        var callbackMaterial: ByteArray? = null
        compose.runOnUiThread {
            BiometricVault(compose.activity).authenticate(
                text(R.string.identity_authenticate),
                success = {
                    authenticated = UnionNative.publicIdentity(it)
                    callbackMaterial = it
                },
                failure = { failure = it },
            )
        }
        assertNull(failure)
        assertEquals(expected, authenticated)
        assertNotNull(callbackMaterial)
        assertTrue(callbackMaterial!!.all { it == 0.toByte() })
        assertEquals(expected, sessionPublicIdentity())
    }

    @Test fun rememberedSessionIsEncryptedStableAndReadsWithoutAnotherPrompt() {
        authenticateInitially()
        val first = sessionPublicIdentity()
        val peer = sessionPeer()
        val directory = compose.activity.noBackupFilesDir
        val encrypted = File(directory, "union-device.enc")
        val session = File(directory, "union-device.session.enc")
        assertTrue(encrypted.isFile)
        assertTrue(session.isFile)
        assertOwnerOnly(encrypted)
        assertOwnerOnly(session)
        // Neither the identity nor the remembered session is plaintext, and no plaintext file remains.
        assertFalse(session.readBytes().toString(Charsets.ISO_8859_1).contains(peer))
        assertFalse(File(directory, "union-device.debug").exists())
        repeat(3) {
            // Repeat reads use only the remembered session: no prompt/callback is involved.
            assertEquals(first, sessionPublicIdentity())
        }
    }

    @Test fun coldRecreationAndForegroundReturnRestoreWithoutAnUnlockTap() {
        authenticateInitially()
        val first = sessionPublicIdentity()
        compose.activityRule.scenario.recreate()
        awaitUnlocked()
        assertEquals(first, sessionPublicIdentity())
        assertTrue(compose.onAllNodesWithText(text(R.string.management_unlock)).fetchSemanticsNodes().isEmpty())

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitUnlocked()
        assertEquals(first, sessionPublicIdentity())
        assertTrue(compose.onAllNodesWithText(text(R.string.management_unlock)).fetchSemanticsNodes().isEmpty())
    }

    /**
     * Opt-in actual-activity/real-backend check. The staging fixture supplies a live identity
     * backend; this generated device is not one of its members, so the server result must stop at
     * the identity retry step instead of exposing a workbench or looping unlock requests.
     */
    @Test fun actualActivityAgainstRealIdentityBackendStopsAtTheIdentityRetryStep() {
        val fixture = InstrumentationRegistry.getArguments().getString("stagingFixture")?.let(::File)
        assumeTrue(fixture != null)
        val fixtureDirectory = fixture!!
        assumeTrue(File(fixtureDirectory, "identity-config.json").isFile)
        authenticateInitially()
        val config = JSONObject(File(fixtureDirectory, "identity-config.json").readText())
        compose.activity.getPreferences(android.content.Context.MODE_PRIVATE).edit().apply {
            putString("identity_configuration", config.toString())
            putString("address", config.optString("address"))
            putString("peer", config.optString("peer"))
            putString("genesis_hash", config.optString("genesis_hash"))
        }.apply()
        compose.activityRule.scenario.recreate()
        awaitUnlocked()
        compose.waitUntil(120_000) {
            val unavailable = compose.onAllNodesWithText(text(R.string.startup_identity_unavailable_body)).fetchSemanticsNodes().isNotEmpty()
            val unbound = compose.onAllNodesWithText(text(R.string.startup_identity_unbound_body)).fetchSemanticsNodes().isNotEmpty()
            unavailable || unbound
        }
        assertTrue(compose.onAllNodesWithText(text(R.string.management_unlock)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(text(R.string.office_workbench_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.startup_identity_retry)).assertExists()
    }
}
