package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BoundedReadTest {
    private fun tooLarge(block: () -> Unit) = try { block(); false } catch (e: TooLargeException) { true }

    @Test
    fun readsAFileThatIsUnderTheLimit() {
        val data = ByteArray(10_000) { it.toByte() }
        assertEquals(data.toList(), ByteArrayInputStream(data).readBounded(20_000).toList())
    }

    @Test
    fun aFileExactlyAtTheLimitIsFine() {
        assertEquals(1_000, ByteArrayInputStream(ByteArray(1_000)).readBounded(1_000).size)
    }

    @Test
    fun refusesAFileOverTheLimit() {
        assertTrue(tooLarge { ByteArrayInputStream(ByteArray(1_001)).readBounded(1_000) })
    }

    @Test
    fun stopsAnEndlessStreamInsteadOfFillingMemory() {
        val endless = object : InputStream() { override fun read() = 0 }
        assertTrue(tooLarge { endless.readBounded(1_000_000) })
    }

    @Test
    fun aZipBombIsRefusedAtTheCapNotUnpackedInFull() {
        // ~300 MB of zeros squeezes into a few hundred KB, the classic bomb.
        val bomb = ByteArrayOutputStream().also { sink ->
            ZipOutputStream(sink).use { z ->
                z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                val chunk = ByteArray(1024 * 1024)
                repeat(300) { z.write(chunk) }
                z.closeEntry()
            }
        }.toByteArray()
        assertTrue("the archive itself is small", bomb.size < 2_000_000)
        ZipInputStream(ByteArrayInputStream(bomb)).use { z ->
            z.nextEntry
            assertTrue(tooLarge { z.readBounded(5 * 1024 * 1024) })
        }
    }
}
