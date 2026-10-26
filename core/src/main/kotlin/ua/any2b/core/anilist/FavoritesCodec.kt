package ua.any2b.core.anilist

import org.json.JSONArray
import org.json.JSONObject

object FavoritesCodec {
    fun decode(raw: String?): List<AnimeCard> = try {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AnimeCard(
                id = item.getInt("id"),
                title = Title(
                    item.nullableString("romaji"),
                    item.nullableString("english"),
                    item.nullableString("native"),
                ),
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

    fun encode(cards: List<AnimeCard>): String = JSONArray().apply {
        cards.forEach { card ->
            put(JSONObject().apply {
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
    }.toString()

    private fun JSONObject.nullableString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
}
