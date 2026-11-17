package com.example.bankflow.data.security

import com.example.bankflow.data.local.session.SecretCipher
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultCipher(
    private val keys: VaultKeys,
    private val random: SecureRandom = SecureRandom(),
) : SecretCipher {

    override fun encrypt(plainText: String): String {
        val key = keys.currentKey() ?: throw VaultLockedException()
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun decrypt(encoded: String): String? {
        val key = keys.currentKey() ?: return null
        return try {
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.size <= IV_BYTES) return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)),
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}