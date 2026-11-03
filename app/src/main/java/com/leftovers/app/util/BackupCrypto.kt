package com.leftovers.app.util

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup is password protected and no password, or the wrong one, was given. */
class BackupPasswordException(message: String) : Exception(message)

/**
 * Password protection for backup files: AES-256-GCM with a key stretched from the password by
 * PBKDF2. The file stays JSON, so it is still recognisable as a Leftovers backup.
 */
object BackupCrypto {
    /** For new files: OWASP's current guidance for PBKDF2-HMAC-SHA256 is 600,000 rounds. */
    private const val ITERATIONS = 600_000

    /** What files made before the count was raised were stretched with; a file that doesn't say is read with it. */
    private const val LEGACY_ITERATIONS = 150_000

    /**
     * A file's own settings are untrusted input (a backup can arrive through a sync folder or a download). A count far
     * above this would freeze the phone for hours before a single password guess is checked, so it's refused instead.
     */
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val IV_BYTES = 12
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun isEncrypted(json: JSONObject) = json.optBoolean("encrypted", false)

    fun encrypt(plain: String, password: String): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("app", "leftovers")
            .put("encrypted", true)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", b64.encodeToString(salt))
            .put("iv", b64.encodeToString(iv))
            .put("data", b64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8))))
    }

    fun decrypt(wrapper: JSONObject, password: String): String {
        val b64 = Base64.getDecoder()
        val salt = b64.decode(wrapper.getString("salt"))
        val iv = b64.decode(wrapper.getString("iv"))
        val iterations = wrapper.optInt("iterations", LEGACY_ITERATIONS)
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS && salt.size in 8..64 && iv.size == IV_BYTES) {
            "This backup uses protection settings Leftovers won't run"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        return try {
            String(cipher.doFinal(b64.decode(wrapper.getString("data"))), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw BackupPasswordException("Wrong password")
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
