package com.jlucraft.manager.nativecore

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Captures only the real locked entry; never clicks unlock or fabricates system authorization. */
@RequiresFixture
class LockedActivityGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lockedEntryWithoutAuthentication() {
        val activity = compose.activity
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(activity.getString(com.jlucraft.manager.R.string.management_locked))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle {
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        try {
            compose.waitForIdle()
            Thread.sleep(500)
            captureScreen("leaf-locked-real-activity")
        } finally {
            compose.runOnIdle { activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }
}
