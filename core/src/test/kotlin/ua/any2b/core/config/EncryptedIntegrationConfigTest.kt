package ua.any2b.core.config

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptedIntegrationConfigTest {
    @Test
    fun decryptsNestedJsonWithExpectedWireFormat() {
        val payload = encrypt("""{"worker":{"url":"https://example.com","key":"worker-test"},
            "tmdb":{"token":"tmdb-test"},"fanart":{"key":"fanart-test"},
            "mal":{"clientid":"0123456789abcdef0123456789abcdef"}}""", "password")
        val config = EncryptedIntegrationConfig.decrypt(payload, "password".toCharArray())
        assertEquals("https://example.com", config.workerUrl)
        assertEquals("worker-test", config.workerKey)
        assertEquals("tmdb-test", config.tmdbToken)
        assertEquals("fanart-test", config.fanartKey)
        assertEquals("0123456789abcdef0123456789abcdef", config.malClientId)
    }

    @Test
    fun rejectsWrongPasswordAndTampering() {
        val payload = encrypt("""{"tmdbToken":"test"}""", "correct")
        assertFailsWith<IllegalArgumentException> {
            EncryptedIntegrationConfig.decrypt(payload, "wrong".toCharArray())
        }
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> {
            EncryptedIntegrationConfig.decrypt(payload, "correct".toCharArray())
        }
    }

    private fun encrypt(json: String, password: String): ByteArray {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return salt + iv + cipher.doFinal(json.toByteArray())
    }
}
