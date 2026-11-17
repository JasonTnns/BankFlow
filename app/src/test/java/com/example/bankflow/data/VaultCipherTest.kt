package com.example.bankflow.data

import com.example.bankflow.data.security.VaultCipher
import com.example.bankflow.data.security.VaultLockedException
import com.example.bankflow.domain.security.VaultState
import com.example.bankflow.testutil.FakeVaultKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VaultCipherTest {

    private val keys = FakeVaultKeys()
    private val cipher = VaultCipher(keys)

    @Test
    fun `text survives the round trip, including unicode and the empty string`() {
        listOf("access-token", "Åsa — 銀行 🏦", "").forEach { text ->
            assertEquals(text, cipher.decrypt(cipher.encrypt(text)))
        }
    }

    @Test
    fun `the same text encrypts differently each time`() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun `the output does not contain the text`() {
        assertFalse(cipher.encrypt("Everyday Checking").contains("Everyday"))
    }

    @Test
    fun `tampering is detected`() {
        val bytes = Base64.getDecoder().decode(cipher.encrypt("balance 100000"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()

        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun `a different key cannot read it`() {
        val encrypted = cipher.encrypt("secret")

        keys.key = ByteArray(32) { (it + 100).toByte() }

        assertNull(cipher.decrypt(encrypted))
    }

    @Test
    fun `garbage counts as unreadable`() {
        listOf("", "!!!not base64!!!", "AAAA", Base64.getEncoder().encodeToString(ByteArray(40))).forEach {
            assertNull("'$it'", cipher.decrypt(it))
        }
    }

    @Test
    fun `without the key nothing can be written or read`() {
        val encrypted = cipher.encrypt("secret")
        keys.key = null
        keys.stateFlow.value = VaultState.LOCKED

        assertNull(cipher.decrypt(encrypted))
        assertTrue(runCatching { cipher.encrypt("x") }.exceptionOrNull() is VaultLockedException)
    }
}