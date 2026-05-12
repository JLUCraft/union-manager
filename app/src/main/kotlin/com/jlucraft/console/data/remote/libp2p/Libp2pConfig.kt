package com.jlucraft.console.data.remote.libp2p


 *
 *
 *
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
