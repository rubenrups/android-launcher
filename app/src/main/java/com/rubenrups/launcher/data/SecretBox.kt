package com.rubenrups.launcher.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the few credentials the launcher keeps (GitHub token, LibreLinkUp login) with an AES-256-GCM key that
 * lives in the Android Keystore and never leaves it. A copy of the preferences file (root, a debuggable build,
 * a forgotten backup) then holds only ciphertext that is useless on any other device.
 *
 * Fails closed: if the key is gone or the value is damaged, [decrypt] returns null and the user simply types the
 * credential again. Nothing here ever falls back to storing plain text.
 */
object SecretBox {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "rups_launcher_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Marks a stored value as ciphertext, so older plain-text values can be told apart and upgraded. */
    const val PREFIX = "v1:"

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /** Returns the stored form of [plain], or null if the Keystore is unavailable. Blank stays blank. */
    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Returns the original text, or null if [stored] is not ciphertext of ours or cannot be opened any more. */
    fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return null
        return runCatching {
            val sealed = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
