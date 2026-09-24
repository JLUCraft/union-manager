package cn.jlucraft.manager.nativecore

object UnionNative {
    init { System.loadLibrary("jlucraft_manager") }
    external fun generateSecret(): ByteArray
    external fun peerId(secret: ByteArray): String
    external fun execute(secret: ByteArray, request: String): String
}
