package ua.any2b.tvplayer.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The encrypted value stays outside Android Auto Backup. API 21–22 keep it for this process only. */
class WorkerKeyStore(context: Context) {
    private val delegate = ProtectedValueStore(context, "worker_key.enc", "animarium_worker_key_v1")

    fun read(): String? = delegate.read()
    fun write(value: String) = delegate.write(value)
}

internal class ProtectedValueStore(context: Context, fileName: String, private val alias: String) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, fileName))

    @Synchronized
    fun read(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return synchronized(legacyValues) { legacyValues[alias] }
        if (!file.baseFile.exists()) return null
        return try {
            val data = file.openRead().use { it.readBytes() }
            if (data.size < 14 || data[0].toInt() != 1) return null
            val ivSize = data[1].toInt() and 0xff
            if (ivSize != 12 || data.size <= 2 + ivSize) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(2, 2 + ivSize)))
            String(cipher.doFinal(data.copyOfRange(2 + ivSize, data.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun write(value: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            synchronized(legacyValues) {
                if (value.isBlank()) legacyValues.remove(alias) else legacyValues[alias] = value
            }
            return
        }
        if (value.isBlank()) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv +
            cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        val legacyValues = mutableMapOf<String, String>()
    }
}
