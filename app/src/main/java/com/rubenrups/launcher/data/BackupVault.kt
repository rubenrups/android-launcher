package com.rubenrups.launcher.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for the logins inside a backup file. The key comes from the user's password (PBKDF2 with a
 * random salt), the data is sealed with AES-256-GCM, so a wrong password or a changed file simply fails to open.
 * The Android Keystore key that protects the logins on the phone cannot be exported, which is why a backup needs this.
 */
object BackupVault {
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /** A JSON string holding the salt, nonce and ciphertext of [plain]. */
    fun seal(plain: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray())
        return JSONObject()
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    /** The plain text, or null if the password is wrong or the data was changed. */
    fun open(sealed: String, password: String): String? = runCatching {
        val json = JSONObject(sealed)
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(data))
    }.getOrNull()
}
