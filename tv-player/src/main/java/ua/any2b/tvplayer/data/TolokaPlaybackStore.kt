package ua.any2b.tvplayer.data

import android.content.Context

/** Records files opened for playback, separately from AniTube watch progress. */
class TolokaPlaybackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "toloka_playback_prefs", Context.MODE_PRIVATE,
    )

    fun openedFileIds(animeId: Int, topicId: String): Set<Int> {
        if (animeId <= 0 || !topicId.matches(Regex("[0-9]+"))) return emptySet()
        return prefs.getStringSet(key(animeId, topicId), emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull).filter { it > 0 }.toSet()
    }

    fun hasOpenedFile(animeId: Int, topicId: String): Boolean =
        openedFileIds(animeId, topicId).isNotEmpty()

    fun markOpened(animeId: Int, topicId: String, fileId: Int) {
        require(fileId > 0) { "Некоректний ID файлу" }
        val current = prefs.getStringSet(key(animeId, topicId), emptySet()).orEmpty().toMutableSet()
        if (current.add(fileId.toString())) {
            prefs.edit().putStringSet(key(animeId, topicId), current).apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(animeId: Int, topicId: String): String {
        require(animeId > 0 && topicId.matches(Regex("[0-9]+"))) { "Некоректний ID тайтла або роздачі" }
        return "$animeId:$topicId"
    }
}
