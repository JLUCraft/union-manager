package com.jlucraft.console.data.remote.libp2p

/**
 * Configuration for the union-manager libp2p peer.
 *
 * The union-manager participates in the federated cluster as a management peer:
 * it connects to bootstrap nodes, establishes a libp2p host identity, and
 * communicates via protobuf over libp2p streams — not via REST.
 *
 * All fields must be explicitly provided; no defaults or fallbacks are allowed.
 *
 * @property listenAddresses multiaddr strings this peer listens on (e.g. "/ip4/0.0.0.0/tcp/0")
 * @property bootstrapPeers multiaddr strings of initial peers to connect to
 * @property peerIdentityProto serialized protobuf identity (private key) for this peer,
 *           or null to generate a new identity on first start
 * @property protocolPrefix the libp2p protocol string for /control/v1 (e.g. "/jlucraft/control/1.0.0")
 * @property connectTimeoutMs max time to wait for a connection before failing
 * @property requestTimeoutMs max time to wait for a unary response
 * @property streamTimeoutMs max idle time for event streams before reconnect
 */
data class Libp2pConfig(
    val listenAddresses: List<String>,
    val bootstrapPeers: List<String>,
    val peerIdentityProto: ByteArray?,
    val protocolPrefix: String,
    val connectTimeoutMs: Long = 15_000L,
    val requestTimeoutMs: Long = 30_000L,
    val streamTimeoutMs: Long = 300_000L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Libp2pConfig) return false
        return listenAddresses == other.listenAddresses &&
            bootstrapPeers == other.bootstrapPeers &&
            peerIdentityProto.contentEquals(other.peerIdentityProto) &&
            protocolPrefix == other.protocolPrefix &&
            connectTimeoutMs == other.connectTimeoutMs &&
            requestTimeoutMs == other.requestTimeoutMs &&
            streamTimeoutMs == other.streamTimeoutMs
    }

    override fun hashCode(): Int {
        var result = listenAddresses.hashCode()
        result = 31 * result + bootstrapPeers.hashCode()
        result = 31 * result + (peerIdentityProto?.contentHashCode() ?: 0)
        result = 31 * result + protocolPrefix.hashCode()
        result = 31 * result + connectTimeoutMs.hashCode()
        result = 31 * result + requestTimeoutMs.hashCode()
        result = 31 * result + streamTimeoutMs.hashCode()
        return result
    }
}
