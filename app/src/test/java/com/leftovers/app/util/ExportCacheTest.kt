package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExportCacheTest {
    private fun dir() = kotlin.io.path.createTempDirectory("exports").toFile()

    @Test
    fun removesOnlyFilesOlderThanTheLimit() {
        val d = dir()
        val now = 10_000_000L
        val old = File(d, "old.csv").apply { writeText("x"); setLastModified(now - 90_000) }
        val fresh = File(d, "fresh.csv").apply { writeText("x"); setLastModified(now - 1_000) }
        assertEquals(1, ExportCache.purge(d, now, 60_000))
        assertTrue(!old.exists() && fresh.exists())
        d.deleteRecursively()
    }

    @Test
    fun aMissingFolderIsFine() {
        assertEquals(0, ExportCache.purge(File("/no/such/exports-folder"), 1L, 1L))
    }
}
