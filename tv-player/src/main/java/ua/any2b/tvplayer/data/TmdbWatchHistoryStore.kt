package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

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
