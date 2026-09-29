package com.jlucraft.manager.nativecore

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

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
