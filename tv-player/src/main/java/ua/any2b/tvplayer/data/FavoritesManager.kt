package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title

class FavoritesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): List<AnimeCard> = try {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AnimeCard(
                id = item.getInt("id"),
                title = Title(item.nullableString("romaji"), item.nullableString("english"), item.nullableString("native")),
                cover = item.nullableString("cover"),
                score = item.nullableInt("score"),
                episodes = item.nullableInt("episodes"),
                format = item.nullableString("format"),
                status = item.nullableString("status"),
                seasonYear = item.nullableInt("seasonYear"),
                nextEpisode = null,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun isFavorite(animeId: Int): Boolean = getFavorites().any { it.id == animeId }

    /** Returns the new favorite state. */
    fun toggle(details: AnimeDetails): Boolean {
        val favorites = getFavorites().toMutableList()
        val existingIndex = favorites.indexOfFirst { it.id == details.id }
        val isFavorite = existingIndex < 0
        if (isFavorite) {
            favorites.add(0, AnimeCard(
                id = details.id,
                title = details.title,
                cover = details.cover,
                score = details.score,
                episodes = details.episodes,
                format = details.format,
                status = details.status,
                seasonYear = details.seasonYear,
                nextEpisode = null,
            ))
        } else {
            favorites.removeAt(existingIndex)
        }
        val array = JSONArray()
        favorites.forEach { card ->
            array.put(JSONObject().apply {
                put("id", card.id)
                put("romaji", card.title.romaji)
                put("english", card.title.english)
                put("native", card.title.native)
                put("cover", card.cover)
                put("score", card.score)
                put("episodes", card.episodes)
                put("format", card.format)
                put("status", card.status)
                put("seasonYear", card.seasonYear)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
        return isFavorite
    }

    private fun JSONObject.nullableString(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
}
