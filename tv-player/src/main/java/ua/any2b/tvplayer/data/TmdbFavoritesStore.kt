package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

/** Local TMDB favorites, independent of AniList favorites. */
class TmdbFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "tmdb_favorites", Context.MODE_PRIVATE)
    private val lock = Any()

    fun all(): List<TmdbTitle> = synchronized(lock) { read() }

    fun contains(type: TmdbMediaType, id: Int): Boolean = synchronized(lock) {
        read().any { it.type == type && it.id == id }
    }

    fun toggle(title: TmdbTitle): Boolean = synchronized(lock) {
        val existing = read()
        val wasFavorite = existing.any { it.type == title.type && it.id == title.id }
        val updated = if (wasFavorite) existing.filterNot {
            it.type == title.type && it.id == title.id
        } else listOf(title) + existing
        val array = JSONArray().apply {
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
        }
        prefs.edit().putString("items", array.toString()).apply()
        !wasFavorite
    }

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
}
