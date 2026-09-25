package cn.jlucraft.manager.nativecore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class OfficeFileIOTest {
    @Test fun emptyAndMaximumSizeFilesArePreserved() {
        for (bytes in listOf(byteArrayOf(), ByteArray(OFFICE_FILE_LIMIT) { (it % 251).toByte() })) {
            val output = ByteArrayOutputStream()
            copyOfficeFile(ByteArrayInputStream(bytes), output)
            assertArrayEquals(bytes, output.toByteArray())
        }
    }
    @Test fun oversizedFileStopsBeforeWritingBeyondLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            copyOfficeFile(ByteArrayInputStream(ByteArray(OFFICE_FILE_LIMIT + 1)), output)
        }
        assertTrue(output.size() <= OFFICE_FILE_LIMIT)
    }
    @Test fun zeroLengthReadsMakeProgress() {
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = if (available() > 0) 0 else -1
        }
        val output = ByteArrayOutputStream()
        copyOfficeFile(input, output)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }
}
