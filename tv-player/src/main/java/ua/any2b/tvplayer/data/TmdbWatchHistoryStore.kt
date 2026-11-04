package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

data class TmdbEpisodeProgress(
    val voiceIndex: Int,
    val episodeIndex: Int,
    val episodeNumber: Int,
    val progress: Float,
)

/** Titles whose TorrServer stream actually started in the built-in player. */
class TmdbWatchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "tmdb_watch_history", Context.MODE_PRIVATE)

    fun all(): List<TmdbTitle> = synchronized(lock) { read() }

    fun record(title: TmdbTitle) = synchronized(lock) {
        val updated = (listOf(title) + read().filterNot {
            it.type == title.type && it.id == title.id
        }).take(100)
        prefs.edit().putString("items", JSONArray().apply {
            updated.forEach { item ->
                put(JSONObject().apply {
                    put("type", item.type.path)
                    put("id", item.id)
                    put("title", item.title)
                    put("originalTitle", item.originalTitle)
                    put("year", item.year)
                    put("posterUrl", item.posterUrl)
                    put("rating", item.rating)
                })
            }
        }.toString()).apply()
    }

    fun saveEpisodeProgress(
        title: TmdbTitle,
        voiceIndex: Int,
        episodeIndex: Int,
        episodeNumber: Int,
        progress: Float,
    ) = synchronized(lock) {
        record(title)
        val key = TitleKey.Tmdb(title.type, title.id).storageKey
        val values = try { JSONObject(prefs.getString("episode_progress", "{}").orEmpty()) }
            catch (_: Exception) { JSONObject() }
        values.put(key, JSONObject().apply {
            put("voiceIndex", voiceIndex)
            put("episodeIndex", episodeIndex)
            put("episodeNumber", episodeNumber)
            put("progress", progress.coerceIn(0f, 1f).toDouble())
            put("updatedAt", System.currentTimeMillis())
        })
        prefs.edit().putString("episode_progress", values.toString()).apply()
    }

    fun episodeProgress(type: TmdbMediaType, id: Int): TmdbEpisodeProgress? = synchronized(lock) {
        val key = TitleKey.Tmdb(type, id).storageKey
        try {
            val data = JSONObject(prefs.getString("episode_progress", "{}").orEmpty())
                .optJSONObject(key) ?: return@synchronized null
            TmdbEpisodeProgress(
                voiceIndex = data.optInt("voiceIndex", 0),
                episodeIndex = data.optInt("episodeIndex", 0),
                episodeNumber = data.optInt("episodeNumber", 1),
                progress = data.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            )
        } catch (_: Exception) { null }
    }

    fun clear() = synchronized(lock) { prefs.edit().clear().apply() }

    private fun read(): List<TmdbTitle> = try {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val type = TmdbMediaType.entries.firstOrNull {
                it.path == item.optString("type")
            } ?: return@mapNotNull null
            val id = item.optInt("id")
            val title = item.optString("title")
            if (id <= 0 || title.isBlank()) return@mapNotNull null
            TmdbTitle(id, type, title,
                item.optString("originalTitle").takeUnless { it.isBlank() || it == "null" },
                item.optInt("year").takeIf { it > 0 },
                item.optString("posterUrl").takeUnless { it.isBlank() || it == "null" },
                null,
                item.optDouble("rating", Double.NaN).takeIf(Double::isFinite))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private companion object { val lock = Any() }
}
