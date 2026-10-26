package com.jlucraft.manager.nativecore

import org.junit.Assert.*
import org.junit.Test

class CreateLeagueValidationTest {
    @Test fun acceptsBase58PeerIdStyleIdentities() {
        assertTrue(IDENTITY_ID_PATTERN.matches("12D3KooWGrg8mUjLBipoUgKiqhRgz2CgGktDMftVwJnFUYm2abcd"))
    }

    @Test fun rejectsEmptyAndShortValues() {
        assertFalse(IDENTITY_ID_PATTERN.matches(""))
        assertFalse(IDENTITY_ID_PATTERN.matches("abc"))
    }

    @Test fun rejectsCharactersOutsideBase58() {
        // 0, O, I, l are not in the base58 alphabet, so a pasted hex digest fails.
        assertFalse(IDENTITY_ID_PATTERN.matches("0f".repeat(32)))
        assertFalse(IDENTITY_ID_PATTERN.matches("lO0I" + "a".repeat(40)))
    }

    @Test fun rejectsOverlyLongValues() {
        assertFalse(IDENTITY_ID_PATTERN.matches("a".repeat(65)))
    }
}
