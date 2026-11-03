package com.leftovers.app.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** A file or reply is bigger than the app is willing to hold in memory. */
class TooLargeException(limit: Long) : IOException("That's larger than ${limit / (1024 * 1024)} MB, which Leftovers won't open")

/** The most a backup, statement or sync file may be. Photos make backups big, but nothing real needs more. */
const val MAX_FILE_BYTES = 128L * 1024 * 1024

/** The most an AI provider's reply may be. */
const val MAX_REPLY_BYTES = 8L * 1024 * 1024

/** Entries read from one zip; a real workbook or backup has a handful. */
const val MAX_ZIP_ENTRIES = 2_000

/**
 * Reads everything, but stops with [TooLargeException] as soon as more than [limit] bytes have come in. Plain
 * `readBytes()` on a hostile file (a zip that unpacks to gigabytes, a server that never stops sending) would
 * run the phone out of memory instead.
 */
fun InputStream.readBounded(limit: Long = MAX_FILE_BYTES): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) throw TooLargeException(limit)
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
