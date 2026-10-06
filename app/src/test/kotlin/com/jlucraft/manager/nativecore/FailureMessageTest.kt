package com.jlucraft.manager.nativecore

import com.jlucraft.manager.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FailureMessageTest {
    private fun message(code: String) = failureMessageRes(NativeFailure(code, "diagnostic"))

    @Test fun membershipCodesPointToIdentity() {
        listOf("identity_unavailable", "authority_unavailable", "access_denied").forEach {
            assertEquals(it, R.string.identity_unavailable, message(it))
        }
    }

    @Test fun connectivityCodesPointToTheNetwork() {
        listOf("network", "timeout").forEach { assertEquals(it, R.string.management_network, message(it)) }
    }

    @Test fun invalidInputCodesPointToTheLink() {
        listOf("invalid_request", "not_found").forEach { assertEquals(it, R.string.management_invalid_request, message(it)) }
    }

    @Test fun unknownAndNonNativeFailuresStayGeneric() {
        assertEquals(R.string.management_failed, message("operation_failed"))
        assertEquals(R.string.management_failed, failureMessageRes(IllegalStateException("boom")))
    }
}
