package com.jlucraft.manager.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameOpsProtocolTest {
    private val target = GameOpsTarget(" address ", " peer ", " service ")

    @Test fun listHasNoAmbiguousOuterOrInnerInstance() {
        val input = gameOpsRequest(target, "list_instances", "ignored")
        assertEquals("address", input.getString("address"))
        assertEquals("peer", input.getString("peer"))
        assertEquals("service", input.getString("service"))
        assertFalse(input.has("grant"))
        val request = input.getJSONObject("request")
        assertEquals(setOf("op"), request.keys().asSequence().toSet())
        assertEquals("{\"op\":\"list_instances\"}", request.getJSONObject("op").toString())
    }
    @Test fun eachInstanceOperationUsesOnlyItsSelectedTarget() {
        for (operation in listOf("status", "start", "stop", "restart", "kill", "logs")) {
            val op = gameOpsRequest(target, operation, "survival")
                .getJSONObject("request").getJSONObject("op")
            assertEquals(setOf("op", "instance"), op.keys().asSequence().toSet())
            assertEquals(operation, op.getString("op"))
            assertEquals("survival", op.getString("instance"))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun missingInstanceCannotBeSubmitted() {
        gameOpsRequest(target, "kill", "")
    }
    @Test(expected = IllegalArgumentException::class) fun unknownOperationCannotBeSubmitted() {
        gameOpsRequest(target, "console", "survival")
    }
    @Test fun omittedOptionalResponseFieldsStayEmpty() {
        assertEquals(GameOpsResult(true, emptyList()), gameOpsResult(JSONObject("{\"ok\":true}")))
        assertFalse(gameOpsResult(JSONObject("{\"ok\":false,\"error\":\"denied\"}")).ok)
    }
    @Test(expected = IllegalArgumentException::class) fun negativeCountsAreRejected() {
        gameOpsResult(JSONObject("""{"ok":true,"instances":[{"id":"a","state":"running","version":"1.21","players_online":-1,"players_max":20}]}"""))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsAreRejected() {
        val instance = """{"id":"a","state":"stopped","version":"","players_online":0,"players_max":0}"""
        gameOpsResult(JSONObject("""{"ok":true,"instances":[$instance,$instance]}"""))
    }
}
