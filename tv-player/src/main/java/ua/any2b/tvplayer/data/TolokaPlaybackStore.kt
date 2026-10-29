package ua.any2b.tvplayer.data

import android.content.Context

/** Records files opened for playback, separately from AniTube watch progress. */
class TolokaPlaybackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "toloka_playback_prefs", Context.MODE_PRIVATE,
    )

    fun openedFileIds(animeId: Int, topicId: String): Set<Int> {
        if (animeId <= 0 || !topicId.matches(Regex("[0-9]+"))) return emptySet()
        return read(key(animeId, topicId))
    }

    fun openedTmdbFileIds(type: String, tmdbId: Int, topicId: String): Set<Int> =
        tmdbKey(type, tmdbId, topicId)?.let(::read).orEmpty()

    fun hasOpenedFile(animeId: Int, topicId: String): Boolean =
        openedFileIds(animeId, topicId).isNotEmpty()

    fun markOpened(animeId: Int, topicId: String, fileId: Int) {
        require(fileId > 0) { "Некоректний ID файлу" }
        mark(key(animeId, topicId), fileId)
    }

    fun markTmdbOpened(type: String, tmdbId: Int, topicId: String, fileId: Int) {
        require(fileId > 0) { "Некоректний ID файлу" }
        mark(requireNotNull(tmdbKey(type, tmdbId, topicId)) { "Некоректний TMDB ID або роздача" }, fileId)
    }

    private fun read(key: String): Set<Int> =
        prefs.getStringSet(key, emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull).filter { it > 0 }.toSet()

    private fun mark(key: String, fileId: Int) {
        val current = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (current.add(fileId.toString())) {
            prefs.edit().putStringSet(key, current).apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(animeId: Int, topicId: String): String {
        require(animeId > 0 && topicId.matches(Regex("[0-9]+"))) { "Некоректний ID тайтла або роздачі" }
        return "$animeId:$topicId"
    }

    private fun tmdbKey(type: String, tmdbId: Int, topicId: String): String? =
        if (type in setOf("tv", "movie") && tmdbId > 0 && topicId.matches(Regex("[0-9]+")))
            "tmdb:$type:$tmdbId:$topicId" else null
}
