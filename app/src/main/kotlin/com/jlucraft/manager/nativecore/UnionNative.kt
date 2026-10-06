package com.jlucraft.manager.nativecore

object UnionNative {
    init { System.loadLibrary("jlucraft_manager") }
    external fun initialize(context: android.content.Context)
    external fun management(request: String): String
    external fun signIdentityOffline(secret: ByteArray, request: String): String
    external fun generateSecret(): ByteArray
    external fun peerId(secret: ByteArray): String
    external fun publicKey(secret: ByteArray): String
    external fun reviewIdentityHistory(input: String): String
    external fun executeIdentity(secret: ByteArray, request: String): String
    external fun executeOps(secret: ByteArray, request: String): String
    external fun readOfficeFile(snapshot: String, cid: String): ByteArray
    external fun executePush(secret: ByteArray, request: String): String
    external fun executeOffice(secret: ByteArray, request: String): String
    external fun execute(secret: ByteArray, request: String): String
}

class NativeFailure(val code: String, message: String) : IllegalStateException(message)
