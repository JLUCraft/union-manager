package com.jlucraft.manager.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionLinkTest {
    private val genesis = "a".repeat(64)

    @Test fun parsesEncodedLink() {
        val link = "folly://federation?address=%2Fip4%2F10.0.0.8%2Ftcp%2F29637&peer=peer-org&genesis=$genesis"
        assertEquals(ConnectionTarget("/ip4/10.0.0.8/tcp/29637", "peer-org", genesis), parseConnectionLink("  $link\n"))
    }

    @Test fun rejectsForeignOrIncompleteLinks() {
        listOf(
            "",
            "not a link",
            "folly://connect?address=a&peer=b&genesis=$genesis",
            "other://federation?address=a&peer=b&genesis=$genesis",
            "folly://federation?address=a&peer=b",
            "folly://federation?address=a&peer=b&genesis=${"A".repeat(64)}",
        ).forEach { assertNull(it, parseConnectionLink(it)) }
    }
}
