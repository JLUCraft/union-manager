package com.jlucraft.console.data.remote.libp2p

import android.util.Base64
import android.util.Log
import com.jlucraft.console.data.local.SettingsStore
import io.libp2p.core.Host
import io.libp2p.core.P2PChannel
import io.libp2p.core.PeerId
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.crypto.unmarshalPrivateKey
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multistream.ProtocolBinding
import io.libp2p.core.multistream.ProtocolDescriptor
import io.libp2p.core.mux.StreamMuxerProtocol
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Libp2pTransportImpl"

@Singleton
class Libp2pTransportImpl @Inject constructor(
    private val settingsStore: SettingsStore,
) : Libp2pTransport {

    private val _state = MutableStateFlow(TransportState.Stopped)
    override val state: Flow<TransportState> = _state.asStateFlow()

    @Volatile private var host: Host? = null
    @Volatile private var savedConfig: Libp2pConfig? = null


    private val peerRegistry = ConcurrentHashMap<String, PeerEntry>()


    private val startMutex = Mutex()



    override suspend fun start(config: Libp2pConfig): Result<Unit> {
        val cur = _state.value
        if (cur == TransportState.Connected || cur == TransportState.Starting) {
            return Result.success(Unit)
        }

        return startMutex.withLock {
            if (_state.value == TransportState.Connected) return@withLock Result.success(Unit)
            _state.value = TransportState.Starting

            runCatching {

                val bootstrapPeers = config.bootstrapPeers.ifEmpty {
                    settingsStore.getBootstrapPeers()
                }

                val privKey = loadOrGenerateKey(config)

                val newHost = host {
                    identity { factory = { privKey } }
                    transports { add(::TcpTransport) }
                    secureChannels { add(::NoiseXXSecureChannel) }
                    muxers { add(StreamMuxerProtocol.getYamux()) }
                    network {
                        config.listenAddresses.forEach { listen(it) }
                    }
                    protocols {
                        add(FramedBytesProtocol(config.protocolPrefix))
                    }
                    addressBook { memory() }
                }

                withContext(Dispatchers.IO) {
                    newHost.start().get(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
                }

                host = newHost
                savedConfig = config

                if (bootstrapPeers.isEmpty()) {
                    Log.w(TAG, "No bootstrap peers configured; transport will be disconnected.")
                    _state.value = TransportState.Disconnected
                    return@runCatching
                }

                var connected = 0
                for (addrStr in bootstrapPeers) {
                    try {
                        val (peerId, addr) = parseBootstrapMultiaddr(addrStr)
                        withContext(Dispatchers.IO) {
                            newHost.network.connect(peerId, addr)
                                .get(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
                        }
                        peerRegistry[peerId.toBase58()] = PeerEntry(peerId, addr)
                        connected++
                        Log.i(TAG, "Connected to bootstrap peer ${peerId.toBase58()}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to connect to $addrStr: ${e.message}")
                    }
                }

                _state.value = if (connected > 0) TransportState.Connected
                else TransportState.Disconnected
            }.onFailure { e ->
                Log.e(TAG, "Transport start failed", e)
                _state.value = TransportState.Error
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                host?.stop()?.get(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "Error during stop: ${e.message}")
            } finally {
                host = null
                peerRegistry.clear()
                _state.value = TransportState.Stopped
            }
        }
    }

    override suspend fun unaryCall(protocol: String, requestBytes: ByteArray): Result<ByteArray> {
        val h = host ?: return Result.failure(IllegalStateException("Transport not started"))
        val peer = firstPeer() ?: return Result.failure(IllegalStateException("No connected peers"))

        return runCatching {
            val controller = openFramedStream(h, protocol, peer)
            try {
                controller.writeFrame(requestBytes)
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(savedConfig?.requestTimeoutMs ?: DEFAULT_REQUEST_TIMEOUT_MS) {
                        controller.receiveOneFrame()
                    } ?: error("Unary request timed out on protocol $protocol")
                }
            } finally {
                controller.close()
            }
        }
    }

    override fun openBidiStream(protocol: String, requestBytes: ByteArray): Flow<ByteArray> = flow {
        val h = host ?: error("Transport not started")
        val peer = firstPeer() ?: error("No connected peers")

        val controller = openFramedStream(h, protocol, peer)
        try {
            controller.writeFrame(requestBytes)
            controller.frameFlow.collect { emit(it) }
        } finally {
            controller.close()
        }
    }

    override fun localPeerId(): String? = host?.peerId?.toBase58()



    private suspend fun loadOrGenerateKey(config: Libp2pConfig) = withContext(Dispatchers.IO) {
        when {
            config.peerIdentityProto != null -> unmarshalPrivateKey(config.peerIdentityProto)
            else -> {
                val saved = settingsStore.getLibp2pPrivateKey()
                if (saved != null) {
                    unmarshalPrivateKey(Base64.decode(saved, Base64.NO_WRAP))
                } else {
                    val (privKey, _) = generateKeyPair(KeyType.ED25519)
                    val encoded = Base64.encodeToString(privKey.bytes(), Base64.NO_WRAP)
                    settingsStore.setLibp2pPrivateKey(encoded)
                    privKey
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun openFramedStream(
        h: Host,
        protocol: String,
        peer: PeerEntry,
    ): FramedStreamController = withContext(Dispatchers.IO) {
        val promise = h.newStream<FramedStreamController>(listOf(protocol), peer.peerId, peer.addr)
        promise.controller.get(
            savedConfig?.connectTimeoutMs ?: DEFAULT_CONNECT_TIMEOUT_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun firstPeer(): PeerEntry? = peerRegistry.values.firstOrNull()


    private fun parseBootstrapMultiaddr(addrStr: String): Pair<PeerId, Multiaddr> {
        val p2pIdx = addrStr.lastIndexOf("/p2p/")
        require(p2pIdx >= 0) { "Bootstrap address missing /p2p/ component: $addrStr" }
        val peerIdStr = addrStr.substring(p2pIdx + 5)
        val addrOnly = addrStr.substring(0, p2pIdx)
        return PeerId.fromBase58(peerIdStr) to Multiaddr(addrOnly)
    }



    private data class PeerEntry(val peerId: PeerId, val addr: Multiaddr)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
        private const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
    }
}






 *
internal class FramedStreamController {

    internal val activeFuture = CompletableFuture<FramedStreamController>()

    private val frameChannel = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile private var nettyChannel: io.netty.channel.Channel? = null


    internal fun onChannelActive(ch: io.netty.channel.Channel) {
        nettyChannel = ch
        activeFuture.complete(this)
    }


    internal fun onFrameReceived(bytes: ByteArray) {
        frameChannel.trySend(bytes)
    }


    internal fun onChannelInactive() {
        frameChannel.close()
    }


    internal fun onError(cause: Throwable) {
        frameChannel.close(cause)
        if (!activeFuture.isDone) activeFuture.completeExceptionally(cause)
    }


    fun writeFrame(data: ByteArray) {
        val ch = nettyChannel ?: error("Channel not yet active")
        val varintBytes = encodeVarint(data.size)
        val buf = Unpooled.wrappedBuffer(varintBytes, data)
        ch.writeAndFlush(buf)
    }


    suspend fun receiveOneFrame(): ByteArray = frameChannel.receive()


    val frameFlow: Flow<ByteArray> = frameChannel.receiveAsFlow()


    fun close() {
        nettyChannel?.close()
    }

    private fun encodeVarint(value: Int): ByteArray {
        require(value >= 0) { "Frame length must be non-negative" }
        val out = mutableListOf<Byte>()
        var v = value
        while (v and 0x7F.inv() != 0) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }
}






 *
internal class FramedBytesProtocol(
    announce: String,
) : ProtocolBinding<FramedStreamController> {

    override val protocolDescriptor = ProtocolDescriptor(announce)

    override fun initChannel(
        ch: P2PChannel,
        selectedProtocol: String,
    ): CompletableFuture<out FramedStreamController> {
        val controller = FramedStreamController()

        ch.pushHandler("varint-frame-decoder", VarintFrameDecoder())
        ch.pushHandler("framed-stream-handler", object : SimpleChannelInboundHandler<ByteBuf>() {

            override fun channelActive(ctx: ChannelHandlerContext) {
                controller.onChannelActive(ctx.channel())
                ctx.fireChannelActive()
            }

            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                val bytes = ByteArray(msg.readableBytes())
                msg.readBytes(bytes)
                controller.onFrameReceived(bytes)
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                controller.onChannelInactive()
                ctx.fireChannelInactive()
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                controller.onError(cause)
                ctx.close()
            }
        })

        return controller.activeFuture
    }
}






 *
 *
internal class VarintFrameDecoder : ChannelInboundHandlerAdapter() {
    private val accumulator = java.io.ByteArrayOutputStream()

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is ByteBuf) { ctx.fireChannelRead(msg); return }
        try {
            val readableBytes = msg.readableBytes()
            val bytes = ByteArray(readableBytes)
            msg.readBytes(bytes)
            msg.release()
            accumulator.write(bytes)

            val data = accumulator.toByteArray()
            var pos = 0

            while (pos < data.size) {
                val frameStart = pos
                var len = 0L
                var shift = 0
                var complete = false
                while (pos < data.size) {
                    val b = data[pos++].toInt() and 0xFF
                    len = len or ((b and 0x7F).toLong() shl shift)
                    shift += 7
                    if (b and 0x80 == 0) { complete = true; break }
                    if (pos - frameStart >= 10) {
                        ctx.fireExceptionCaught(IllegalStateException("Varint exceeds 10 bytes (malformed LEB-128)"))
                        return
                    }
                }
                if (!complete) { pos = frameStart; break }
                if (len < 0 || len > MAX_FRAME_BYTES) {
                    ctx.fireExceptionCaught(IllegalStateException("Frame length out of bounds: $len"))
                    return
                }
                val frameLen = len.toInt()
                if (pos + frameLen > data.size) { pos = frameStart; break }
                ctx.fireChannelRead(Unpooled.wrappedBuffer(data, pos, frameLen).retain())
                pos += frameLen
            }

            accumulator.reset()
            if (pos < data.size) {
                accumulator.write(data, pos, data.size - pos)
            }
        } catch (e: Exception) {
            ctx.fireExceptionCaught(e)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        accumulator.reset()
        ctx.fireChannelInactive()
    }

    companion object {
        private const val MAX_FRAME_BYTES = 64L * 1024 * 1024
    }
}
