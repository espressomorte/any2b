package ua.any2b.core.config

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

data class IntegrationConfig(
    val workerUrl: String? = null,
    val workerKey: String? = null,
    val torrServerUrl: String? = null,
    val torrServerUser: String? = null,
    val torrServerPassword: String? = null,
    val fanartKey: String? = null,
    val tmdbToken: String? = null,
    val tolokaUser: String? = null,
    val tolokaPassword: String? = null,
) {
    val hasValues: Boolean get() = listOf(workerUrl, workerKey, torrServerUrl,
        fanartKey, tmdbToken, tolokaUser).any { !it.isNullOrBlank() }
}

/** Wire format: 16-byte salt, 12-byte IV, AES-256-GCM ciphertext and tag. */
object EncryptedIntegrationConfig {
    fun decrypt(payload: ByteArray, password: CharArray): IntegrationConfig {
        require(payload.size >= 16 + 12 + 16 + 2) { "Пошкоджений зашифрований конфіг" }
        require(password.isNotEmpty()) { "Введіть пароль конфіга" }
        val salt = payload.copyOfRange(0, 16)
        val iv = payload.copyOfRange(16, 28)
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(payload, 28, payload.size - 28)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Невірний пароль або пошкоджений конфіг")
        } finally { key.fill(0) }
        return try { parse(JSONObject(String(plaintext, Charsets.UTF_8))) }
        catch (_: Exception) { throw IllegalArgumentException("Конфіг має невідомий формат JSON") }
        finally { plaintext.fill(0) }
    }

    private fun parse(json: JSONObject): IntegrationConfig {
        val flattened = mutableMapOf<String, String>()
        fun collect(node: JSONObject, prefix: String = "") {
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                when (val value = node.opt(key)) {
                    is JSONObject -> collect(value, path)
                    is String -> if (value.isNotBlank()) flattened[path.lowercase()
                        .replace(Regex("[^a-z0-9]"), "")] = value.trim()
                }
            }
        }
        collect(json)
        fun get(vararg aliases: String): String? = aliases.firstNotNullOfOrNull {
            flattened[it.lowercase().replace(Regex("[^a-z0-9]"), "")]
        }
        return IntegrationConfig(
            workerUrl = get("workerUrl", "worker.url", "tolokaWorkerUrl", "toloka.worker.url"),
            workerKey = get("workerKey", "worker.key", "workerToken", "workerApiKey",
                "tolokaWorkerKey", "toloka.worker.key"),
            torrServerUrl = get("torrServerUrl", "torrserver.url", "torrServerAddress"),
            torrServerUser = get("torrServerUser", "torrserver.username"),
            torrServerPassword = get("torrServerPassword", "torrserver.password"),
            fanartKey = get("fanartKey", "fanartApiKey", "fanart.key", "fanarttvKey",
                "fanartPersonalApiKey"),
            tmdbToken = get("tmdbToken", "tmdbApiReadAccessToken", "tmdbReadAccessToken",
                "tmdb.token", "TMDB_READ_TOKEN"),
            tolokaUser = get("tolokaUser", "tolokaUsername", "toloka.username",
                "tolokaLogin", "toloka.login"),
            tolokaPassword = get("tolokaPassword", "toloka.password"),
        ).also { require(it.hasValues) { "Конфіг не містить підтримуваних полів" } }
    }
}
