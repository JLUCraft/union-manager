package com.jlucraft.manager.nativecore

import java.io.InputStream
import java.io.OutputStream

internal const val OFFICE_FILE_LIMIT = 2 * 1024 * 1024

internal fun copyOfficeFile(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        var size = input.read(buffer)
        if (size < 0) return
        if (size == 0) {
            val byte = input.read()
            if (byte < 0) return
            buffer[0] = byte.toByte(); size = 1
        }
        total += size
        require(total <= OFFICE_FILE_LIMIT)
        output.write(buffer, 0, size)
    }
}
