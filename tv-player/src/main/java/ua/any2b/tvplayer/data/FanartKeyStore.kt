package ua.any2b.tvplayer.data

import android.content.Context

/** Encrypted in noBackupFilesDir, like the Worker key. */
class FanartKeyStore(context: Context) {
    private val delegate = ProtectedValueStore(context, "fanart_key.enc", "animarium_fanart_key_v1")

    fun read(): String? = delegate.read()
    fun write(value: String) = delegate.write(value)
}
