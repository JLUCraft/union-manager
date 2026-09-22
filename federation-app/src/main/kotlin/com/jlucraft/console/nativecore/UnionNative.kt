package com.jlucraft.console.nativecore

object UnionNative {
    init { System.loadLibrary("union_manager") }
    external fun generateSecret(): ByteArray
    external fun peerId(secret: ByteArray): String
    external fun execute(secret: ByteArray, request: String): String
}
