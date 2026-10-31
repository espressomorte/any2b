package ua.any2b.tvplayer.data

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.any2b.core.config.EncryptedIntegrationConfig
import ua.any2b.core.config.IntegrationConfig

class EncryptedConfigSource(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "secrets.enc"))
    private val prefs = context.applicationContext.getSharedPreferences("encrypted_config_source", Context.MODE_PRIVATE)
    private val settings = SettingsManager(context)

    suspend fun load(password: CharArray): IntegrationConfig = withContext(Dispatchers.IO) {
        val url = settings.encryptedConfigUrl()
        val bytes = try { download(url) } catch (_: Exception) {
            if (file.baseFile.exists() && prefs.getString("url", null) == url)
                file.openRead().use { it.readBytes() }
            else throw IllegalStateException("Не вдалося завантажити конфіг. Перевірте мережу")
        }
        val config = EncryptedIntegrationConfig.decrypt(bytes, password)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
            prefs.edit().putString("url", url).apply()
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        config
    }

    private fun download(url: String): ByteArray {
        val parsed = URL(url)
        require(parsed.protocol == "https" && parsed.userInfo == null && parsed.ref == null) {
            "URL конфіга має бути HTTPS без облікових даних"
        }
        val connection = parsed.openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != 200) throw IllegalStateException("HTTP error")
            val encoded = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    output.write(chunk, 0, count)
                    require(output.size() <= MAX_BYTES) { "Конфіг завеликий" }
                }
                output.toByteArray()
            }
            require(encoded.size <= MAX_BYTES) { "Конфіг завеликий" }
            val text = String(encoded, Charsets.US_ASCII).filterNot(Char::isWhitespace)
            require(text.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) { "Конфіг не є Base64" }
            return Base64.decode(text, Base64.DEFAULT)
        } finally { connection.disconnect() }
    }

    private companion object {
        const val MAX_BYTES = 256 * 1024
    }
}
