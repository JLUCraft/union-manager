package com.jlucraft.manager.nativecore

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jlucraft.manager.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in screenshots of the real activity on a disposable, unenrolled emulator. */
class TutorialCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lockedActivityIsProtectedAndCanBeCapturedForTutorials() {
        val activity = compose.activity
        compose.runOnIdle {
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        compose.onNodeWithText(activity.getString(R.string.management_locked)).assertIsDisplayed()
        if (!captureEnabled()) return
        compose.runOnIdle {
            // Only this instrumentation test clears the flag; production stays protected.
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        try {
            compose.onNodeWithText(activity.getString(R.string.management_locked)).assertIsDisplayed()
            compose.waitForIdle()
            captureScreen("android-01-locked")
            compose.onNodeWithText(activity.getString(R.string.management_unlock)).performClick()
            compose.onNodeWithText(activity.getString(R.string.vault_unavailable)).assertIsDisplayed()
            compose.waitForIdle()
            captureScreen("android-02-biometric-required")
        } finally {
            compose.runOnIdle { activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }
}
