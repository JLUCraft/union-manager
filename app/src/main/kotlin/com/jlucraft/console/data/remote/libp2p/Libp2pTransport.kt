package com.jlucraft.console.data.remote.libp2p

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the libp2p transport layer.
 *
 * Implementations handle peer identity, connection management, stream
 * multiplexing, and protocol negotiation. The [Libp2pClient] layer only
 * deals with serialized protobuf frames — it does not know about the
 * underlying transport (TCP, QUIC, WebRTC, etc.).
 *
 * A transport that cannot establish connectivity must fail fast with a
 * descriptive error; no silent fallback to REST or other protocols.
 */
interface Libp2pTransport {

    /** Current connectivity state. */
    val state: Flow<TransportState>

    /**
     * Start the transport: create the libp2p host, connect to bootstrap
     * peers, and begin protocol negotiation. Idempotent — subsequent calls
     * are no-ops if already started.
     */
    suspend fun start(config: Libp2pConfig): Result<Unit>

    /**
     * Stop the transport gracefully: close all streams, disconnect from
     * peers, and release the host.
     */
    suspend fun stop()

    /**
     * Open a new unary stream on [protocol], send [requestBytes] (a
     * serialized protobuf message), and return the serialized response.
     *
     * The stream is closed after the response is received (request/response
     * pattern — equivalent to a unary gRPC call over libp2p).
     *
     * @param protocol the libp2p protocol string for this stream
     * @param requestBytes serialized protobuf ControlRequest
     * @return serialized protobuf ControlResponse bytes, or failure
     */
    suspend fun unaryCall(protocol: String, requestBytes: ByteArray): Result<ByteArray>

    /**
     * Open a persistent bidirectional stream on [protocol], send
     * [requestBytes] once, then yield response frames as a [Flow].
     *
     * The stream remains open until the flow is cancelled or the remote
     * peer closes it. Used for event subscriptions (server-streaming).
     *
     * @param protocol the libp2p protocol string for this stream
     * @param requestBytes serialized protobuf SubscribeEventsRequest
     * @return flow of serialized protobuf EventEnvelope frames
     */
    fun openBidiStream(protocol: String, requestBytes: ByteArray): Flow<ByteArray>

    /**
     * The local peer's libp2p PeerId as a multihash-encoded string.
     * Returns null before [start] completes successfully.
     */
    fun localPeerId(): String?
}

enum class TransportState {
    /** Transport is not running. */
    Stopped,
    /** Starting up: creating host, dialing bootstrap peers. */
    Starting,
    /** Connected to at least one peer and ready for streams. */
    Connected,
    /** No connected peers; may be reconnecting. */
    Disconnected,
    /** Transport encountered a non-recoverable error. */
    Error,
}
