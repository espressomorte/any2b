package ua.any2b.tvplayer.data

import android.content.Context

class TmdbKeyStore(context: Context) {
    private val delegate = ProtectedValueStore(context, "tmdb_key.enc", "animarium_tmdb_key_v1")
    fun read(): String? = delegate.read()
    fun write(value: String) = delegate.write(value)
}
