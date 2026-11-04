package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import ua.any2b.core.tmdb.TmdbMediaType

/** Learns titles the user manually restored from the discarded Toloka results. */
class TolokaRelevanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "toloka_relevance_prefs", Context.MODE_PRIVATE)

    @Synchronized
    fun approvedTitles(type: TmdbMediaType, tmdbId: Int): Set<String> =
        read(type, tmdbId, "approved").toSet()

    @Synchronized
    fun rejectedTitles(type: TmdbMediaType, tmdbId: Int): Set<String> =
        read(type, tmdbId, "rejected").toSet()

    @Synchronized
    fun approve(type: TmdbMediaType, tmdbId: Int, title: String) {
        val normalized = normalize(title)
        if (tmdbId <= 0 || normalized.isBlank()) return
        val titles = (read(type, tmdbId, "approved").filterNot { it == normalized } + normalized).takeLast(100)
        val rejected = read(type, tmdbId, "rejected").filterNot { it == normalized }
        prefs.edit().putString(key(type, tmdbId, "approved"), JSONArray(titles).toString())
            .putString(key(type, tmdbId, "rejected"), JSONArray(rejected).toString()).apply()
    }

    @Synchronized
    fun reject(type: TmdbMediaType, tmdbId: Int, title: String) {
        val normalized = normalize(title)
        if (tmdbId <= 0 || normalized.isBlank()) return
        val titles = (read(type, tmdbId, "rejected").filterNot { it == normalized } + normalized).takeLast(100)
        val approved = read(type, tmdbId, "approved").filterNot { it == normalized }
        prefs.edit().putString(key(type, tmdbId, "rejected"), JSONArray(titles).toString())
            .putString(key(type, tmdbId, "approved"), JSONArray(approved).toString()).apply()
    }

    private fun read(type: TmdbMediaType, tmdbId: Int, category: String): List<String> = try {
        val array = JSONArray(prefs.getString(key(type, tmdbId, category), "[]"))
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun key(type: TmdbMediaType, tmdbId: Int, category: String) =
        "${type.path}:$tmdbId:$category"

    private fun normalize(value: String) = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ").trim()
}
