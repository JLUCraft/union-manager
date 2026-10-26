package com.jlucraft.manager.nativecore

import org.junit.Assert.*
import org.junit.Test

class EventSortTest {
    @Test fun soonestSignupWindowComesFirst() {
        val sorted = sortedEventIds(listOf("b" to 300L, "a" to 100L, "c" to 200L))
        assertEquals(listOf("a", "c", "b"), sorted)
    }

    @Test fun eventsWithoutWindowSortLast() {
        val sorted = sortedEventIds(listOf("b" to null, "a" to 100L, "c" to 50L))
        assertEquals(listOf("c", "a", "b"), sorted)
    }

    @Test fun tiesBreakByIdSoOrderIsStable() {
        val sorted = sortedEventIds(listOf("b" to 100L, "a" to 100L, "c" to 100L))
        assertEquals(listOf("a", "b", "c"), sorted)
    }
}
