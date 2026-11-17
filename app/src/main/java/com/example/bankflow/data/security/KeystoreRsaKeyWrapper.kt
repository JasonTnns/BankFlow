package com.example.bankflow.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class KeystoreRsaKeyWrapper(
    private val alias: String,
    private val authValiditySeconds: Int,
) : KeyWrapper {

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun wrap(key: ByteArray): ByteArray {
        val keyStore = keyStore()
        if (!keyStore.containsAlias(alias)) generateKeyPair()

        val publicKey = keyStore.getCertificate(alias)?.publicKey ?: throw KeyInvalidatedException()
        val unrestricted = KeyFactory.getInstance(publicKey.algorithm)
            .generatePublic(X509EncodedKeySpec(publicKey.encoded))

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, unrestricted, OAEP)
        return cipher.doFinal(key)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val privateKey = keyStore().getKey(alias, null) as? PrivateKey ?: throw KeyInvalidatedException()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP)
            return cipher.doFinal(wrapped)
        } catch (e: UserNotAuthenticatedException) {
            throw AuthenticationRequiredException()
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyInvalidatedException()
        } catch (e: InvalidKeyException) {
            if (e.cause is UserNotAuthenticatedException) throw AuthenticationRequiredException()
            throw e
        }
    }

    override fun destroy() {
        try {
            keyStore().deleteEntry(alias)
        } catch (e: Exception) {
        }
    }

    private fun generateKeyPair() {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        authValiditySeconds,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(authValiditySeconds)
                }
            }
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}