package com.jlucraft.console.data.remote

import jlucraft.control.v1.Control.ControlRequest
import jlucraft.control.v1.Control.GetNodeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedProtoRoundtripTest {
    @Test
    fun `shared control request serializes and deserializes`() {
        val request = ControlRequest.newBuilder()
            .setRequestId("request-1")
            .setGetNode(GetNodeRequest.newBuilder().build())
            .build()

        val decoded = ControlRequest.parseFrom(request.toByteArray())

        assertEquals(request.requestId, decoded.requestId)
        assertTrue(decoded.hasGetNode())
    }
}
