package com.jlucraft.console.data.repository

import com.jlucraft.console.data.model.OracleProof
import com.jlucraft.console.data.model.OracleScore
import com.jlucraft.console.data.model.OracleVerificationResult
import com.jlucraft.console.data.remote.libp2p.Libp2pClient
import java.security.MessageDigest

/**
 * Repository for oracle score retrieval and local proof verification.
 *
 * The oracle subsystem publishes Merkle roots to the audit chain periodically.
 * Each player can fetch their individual proof and verify locally without
 * trusting the server — the proof is cryptographically verifiable against
 * the published root.
 */
class OracleRepository(
    private val client: Libp2pClient
) {
    suspend fun getOracleScore(playerId: String): Result<OracleScore> =
        client.getOracleScore(playerId)

    fun verifyProofLocally(proof: OracleProof): OracleVerificationResult {
        val messages = mutableListOf<String>()

        if (proof.proofNodes.isEmpty() && proof.root != proof.leafHash) {
            messages.add("Empty proof but leaf hash does not match root")
        }

        val computedRoot = try {
            computeMerkleRoot(proof.leafHash, proof.proofNodes, proof.leafIndex, proof.algorithm)
        } catch (e: Exception) {
            messages.add("Hash computation failed: ${e.message}")
            ""
        }

        val locallyVerified = computedRoot.isNotEmpty() && computedRoot == proof.root
        if (!locallyVerified && computedRoot.isNotEmpty()) {
            messages.add("Root mismatch: computed=$computedRoot, expected=${proof.root}")
        }

        return OracleVerificationResult(
            locallyVerified = locallyVerified,
            serverVerified = proof.root.isNotEmpty(),
            computedRoot = computedRoot,
            messages = messages
        )
    }

    private fun computeMerkleRoot(
        leafHash: String,
        proofNodes: List<String>,
        leafIndex: Int,
        algorithm: String
    ): String {
        val md = MessageDigest.getInstance(algorithm)
        var currentHash = hexToBytes(leafHash)
        var index = leafIndex

        for (siblingHex in proofNodes) {
            val sibling = hexToBytes(siblingHex)
            currentHash = if (index % 2 == 0) {
                md.digest(currentHash + sibling)
            } else {
                md.digest(sibling + currentHash)
            }
            index /= 2
        }

        return bytesToHex(currentHash)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
