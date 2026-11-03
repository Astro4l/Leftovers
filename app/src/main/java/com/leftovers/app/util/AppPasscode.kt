package com.leftovers.app.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Leftovers' own PIN or password for app lock, instead of the phone's screen lock. Only a salted PBKDF2 hash is
 * kept, never the code itself, and it's never written into a backup. Wrong tries make the next one wait longer.
 */
class AppPasscode(context: Context) {
    enum class Kind { PIN, PASSWORD }

    private val appContext = context.applicationContext

    /** One try at a time, so several at once can't all pass the wait check before any of them is counted. */
    private val gate = Mutex()

    private val prefs: SharedPreferences = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private val _kind = MutableStateFlow(readKind())
    /** The kind set, or null when app lock uses the phone's screen lock. */
    val kind: StateFlow<Kind?> = _kind.asStateFlow()

    private fun readKind(): Kind? = prefs.getString(KIND, null)?.let { k -> Kind.entries.firstOrNull { it.name == k } }

    suspend fun set(code: String, kind: Kind) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KIND, kind.name)
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .putInt(FAILURES, 0)
            .remove(WAIT_UNTIL).remove(WAIT_LEN).remove(WAIT_BOOT)
            .apply()
        _kind.value = kind
    }

    fun clear() {
        prefs.edit().clear().apply()
        _kind.value = null
    }

    /** Milliseconds to wait before the next try is allowed; 0 when it can be tried now. */
    fun waitMs(): Long {
        val until = prefs.getLong(WAIT_UNTIL, 0)
        if (until == 0L) return 0
        val len = prefs.getLong(WAIT_LEN, MAX_WAIT_MS)
        // The monotonic clock can't be changed from Settings, but it starts again from zero when the phone restarts. After a
        // restart the wait starts over (never longer than it was), instead of counting down from a stale reading.
        val rebooted = prefs.getInt(WAIT_BOOT, -1) != bootCount()
        val now = SystemClock.elapsedRealtime()
        if (rebooted) prefs.edit().putLong(WAIT_UNTIL, now + len).putInt(WAIT_BOOT, bootCount()).apply()
        return remainingWait(until, len, now, rebooted)
    }

    /** How many times the phone has started; -1 when it can't be read. */
    private fun bootCount(): Int =
        runCatching { android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    /** Checks [code]; a wrong one counts towards the wait. Always false while a wait is running. */
    suspend fun check(code: String): Boolean = gate.withLock { withContext(Dispatchers.Default) {
        if (waitMs() > 0) return@withContext false
        val salt = prefs.getString(SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext false
        val stored = prefs.getString(HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return@withContext false
        val ok = MessageDigest.isEqual(stored, hash(code, salt))
        if (ok) {
            prefs.edit().putInt(FAILURES, 0).remove(WAIT_UNTIL).remove(WAIT_LEN).remove(WAIT_BOOT).apply()
        } else {
            val failures = prefs.getInt(FAILURES, 0) + 1
            val wait = waitAfter(failures)
            prefs.edit().putInt(FAILURES, failures).apply {
                if (wait > 0) putLong(WAIT_UNTIL, SystemClock.elapsedRealtime() + wait).putLong(WAIT_LEN, wait).putInt(WAIT_BOOT, bootCount())
            }.apply()
        }
        ok
    } }

    /** A hash slow enough to make guessing a PIN costly, quick enough not to be noticed. */
    private fun hash(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256))
            .encoded

    companion object {
        private const val KIND = "kind"
        private const val SALT = "salt"
        private const val HASH = "hash"
        private const val FAILURES = "failures"
        private const val WAIT_UNTIL = "wait_until"
        private const val WAIT_LEN = "wait_len"
        private const val WAIT_BOOT = "wait_boot"
        private const val MAX_WAIT_MS = 15 * 60_000L
        private const val ITERATIONS = 60_000

        const val MIN_PIN = 4
        const val MAX_PIN = 8
        const val MIN_PASSWORD = 6

        /** No wait for the first four wrong tries, then 30 seconds, doubling each time up to 15 minutes. */
        fun waitAfter(failures: Int): Long = if (failures < 5) 0 else (30_000L shl (failures - 5).coerceAtMost(5)).coerceAtMost(MAX_WAIT_MS)

        /** Time left on a wait of [len] ms that ends at [until] (monotonic clock); never more than [len], whatever was stored. */
        internal fun remainingWait(until: Long, len: Long, now: Long, rebooted: Boolean): Long =
            (if (rebooted) len else until - now).coerceIn(0, len)
    }
}
