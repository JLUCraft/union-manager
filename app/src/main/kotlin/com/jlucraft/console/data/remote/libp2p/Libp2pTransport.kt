package com.jlucraft.console.data.remote.libp2p

import kotlinx.coroutines.flow.Flow


 *
 *
interface Libp2pTransport {


    val state: Flow<TransportState>


    suspend fun start(config: Libp2pConfig): Result<Unit>


    suspend fun stop()


     *
     *
    suspend fun unaryCall(protocol: String, requestBytes: ByteArray): Result<ByteArray>


     *
     *
    fun openBidiStream(protocol: String, requestBytes: ByteArray): Flow<ByteArray>


    fun localPeerId(): String?
}

enum class TransportState {

    Stopped,

    Starting,

    Connected,

    Disconnected,

    Error,
}
