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
            "sjmcl://federation?address=a&peer=b&genesis=$genesis",
            "folly:///federation?address=a&peer=b&genesis=$genesis",
            "folly://federation/extra?address=a&peer=b&genesis=$genesis",
            "folly://user@federation?address=a&peer=b&genesis=$genesis",
            "folly://federation:1234?address=a&peer=b&genesis=$genesis",
            "folly://federation?address&address=a&peer=b&genesis=$genesis",
            "folly://federation?address=++&peer=b&genesis=$genesis",
        ).forEach { assertNull(it, parseConnectionLink(it)) }
    }

    @Test fun matchesLauncherQueryAndRouteSemantics() {
        val query = "address=%2Fip4%2F10.0.0.8%2Ftcp%2F29637%2Fp2p%2F12D3KooWtest%3Fx%3D1%26y%3D2&peer=peer-org&genesis=$genesis"
        val target = ConnectionTarget("/ip4/10.0.0.8/tcp/29637/p2p/12D3KooWtest?x=1&y=2", "peer-org", genesis)
        for (route in listOf("federation", "federation/")) {
            assertEquals(target, parseConnectionLink("folly://$route?$query"))
        }
        assertEquals(target, parseConnectionLink("folly://federation?$query&peer=ignored"))
        assertEquals(target, parseConnectionLink("folly://federation?${query.replace("address=", "%61ddress=")}"))
    }
}
