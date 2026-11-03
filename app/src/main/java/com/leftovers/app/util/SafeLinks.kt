package com.leftovers.app.util

import java.net.URI

/**
 * Links reach the app from places it doesn't control (a backup, a sync folder, a web reply). Before one is opened it
 * has to be a plain web address: https, a real host, no embedded login, and nothing that could start another app
 * (`intent:`, `tel:`, `sms:`, `file:`, `content:`...).
 */
object SafeLinks {
    private const val MAX_LENGTH = 2048
    private const val RELEASES_PREFIX = "/saisaharsh3/Leftovers/"

    /** [raw] if it's a safe https web address, else null. */
    fun web(raw: String?): String? {
        val text = raw?.trim() ?: return null
        if (text.isEmpty() || text.length > MAX_LENGTH || text.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val portOk = uri.port == -1 || uri.port == 443
        return text.takeIf {
            uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null && portOk && '.' in host && !host.endsWith(".")
        }
    }

    /** A page of Leftovers' own GitHub project; the only kind an "update available" notice may send people to. */
    fun releasePage(raw: String?): String? {
        val safe = web(raw) ?: return null
        val uri = URI(safe)
        return safe.takeIf { uri.host.equals("github.com", ignoreCase = true) && uri.path.orEmpty().startsWith(RELEASES_PREFIX) }
    }
}
