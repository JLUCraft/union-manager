package com.jlucraft.manager.nativecore

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Bring a node into view when it lives in a scroll container.
 *
 * The pinned bottom action bar sits outside the page's scroll container, so a plain
 * `performScrollTo()` on its buttons throws "no parent layout with a Scroll SemanticsAction".
 * Scrolling is an optimisation here, not an assertion, so a bar button simply stays put.
 */
internal fun SemanticsNodeInteraction.scrollToIfScrollable(): SemanticsNodeInteraction {
    runCatching { performScrollTo() }
    return this
}

internal fun captureEnabled(): Boolean =
    InstrumentationRegistry.getArguments().getString("tutorialCapture") == "true"

/** Opt-in screenshot for tutorial/design evidence; no-op unless screenshots mode passes the arguments. */
internal fun captureScreen(name: String) {
    if (!captureEnabled()) return
    val runId = checkNotNull(InstrumentationRegistry.getArguments().getString("captureRunId"))
    require(runId.matches(Regex("[A-Za-z0-9_-]+")))
    require(name.matches(Regex("[A-Za-z0-9-]+")))
    // Shell-owned artifacts survive Gradle uninstalling its disposable test APKs.
    ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/jlucraft-e2e-$runId-$name.png")
    ).use { stream -> check(stream.readBytes().isEmpty()) }
}
