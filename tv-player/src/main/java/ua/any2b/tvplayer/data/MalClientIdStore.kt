package ua.any2b.tvplayer.data

import android.content.Context

class MalClientIdStore(context: Context) {
    private val delegate = ProtectedValueStore(context, "mal_client_id.enc", "animarium_mal_client_id_v1")
    fun read(): String? = delegate.read()
    fun write(value: String) {
        val cleaned = value.trim()
        require(cleaned.isEmpty() || cleaned.matches(Regex("[0-9a-fA-F]{32}"))) {
            "MyAnimeList Client ID має містити 32 шістнадцяткові символи"
        }
        delegate.write(cleaned)
    }
}
