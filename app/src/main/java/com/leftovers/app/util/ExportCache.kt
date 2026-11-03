package com.leftovers.app.util

import android.content.Context
import java.io.File

/**
 * CSV exports, statements and recaps are written to the cache so they can be shared, and they hold real figures.
 * Nothing used to remove them, so they sat there in plain text. Anything older than a day is deleted when the app starts.
 */
object ExportCache {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun purge(context: Context) {
        runCatching { purge(File(context.cacheDir, "exports"), System.currentTimeMillis(), DAY_MS) }
    }

    /** Deletes files in [dir] last changed more than [maxAgeMs] before [now]; returns how many went. */
    internal fun purge(dir: File, now: Long, maxAgeMs: Long): Int {
        if (!dir.isDirectory) return 0
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && now - file.lastModified() > maxAgeMs && file.delete()) removed++
        }
        return removed
    }
}
