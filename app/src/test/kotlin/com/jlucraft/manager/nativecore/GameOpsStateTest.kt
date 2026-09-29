package com.jlucraft.manager.nativecore

import org.junit.Assert.*
import org.junit.Test

class GameOpsStateTest {
    private val stopped = GameInstance("survival", "stopped", "", 0, 0)
    private val running = stopped.copy(state = "running", version = "1.21", online = 3, maximum = 20)

    @Test fun incompleteTargetsCannotExecute() {
        assertFalse(GameOpsTarget().complete)
        assertFalse(GameOpsTarget("address", " ", "service").complete)
        assertTrue(GameOpsTarget(" address ", "peer", "service").complete)
    }
    @Test fun refreshRemovesStaleSelectionAndLogs() {
        val before = GameOpsState(listOf(stopped), stopped.id, "old logs")
        assertEquals(GameOpsState(), before.receive("list_instances", GameOpsResult(true, emptyList())))
    }
    @Test fun statusUpdatesOnlyReturnedInstances() {
        val other = stopped.copy(id = "creative")
        val before = GameOpsState(listOf(stopped, other), stopped.id)
        val after = before.receive("start", GameOpsResult(true, listOf(running)))
        assertEquals(listOf(running, other), after.instances)
        assertEquals(stopped.id, after.selected)
    }
    @Test fun failedRefreshPreservesKnownInstancesButClearsLogs() {
        val before = GameOpsState(listOf(stopped), stopped.id, "stale")
        val after = before.receive("list_instances", GameOpsResult(false, emptyList()))
        assertEquals(before.instances, after.instances)
        assertEquals(before.selected, after.selected)
        assertTrue(after.failed)
        assertNull(after.logs)
    }
    @Test fun selectionMustExistAndClearsOldLogs() {
        val before = GameOpsState(listOf(stopped), logs = "old")
        assertEquals(before, before.select("missing"))
        assertEquals(stopped.id, before.select(stopped.id).selected)
        assertNull(before.select(stopped.id).logs)
    }
    @Test fun successClearsFailureAndKeepsReturnedLogs() {
        val state = GameOpsState(listOf(stopped), stopped.id, failed = true)
            .receive("logs", GameOpsResult(true, listOf(stopped), "server log"))
        assertFalse(state.failed)
        assertEquals("server log", state.logs)
    }
}
