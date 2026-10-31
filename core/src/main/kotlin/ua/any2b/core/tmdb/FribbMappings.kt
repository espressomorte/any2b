package ua.any2b.core.tmdb

import org.json.JSONArray

data class FribbTmdbId(val type: TmdbMediaType, val id: Int)
data class FribbMappingIndex(
    val byAniList: Map<Int, List<FribbTmdbId>>,
    val byMal: Map<Int, List<FribbTmdbId>>,
)

/** AniList → TMDB IDs from Fribb/anime-lists' anime-list-mini.json. */
object FribbMappings {
    fun parse(json: String): Map<Int, List<FribbTmdbId>> = parseIndex(json).byAniList

    fun parseIndex(json: String): FribbMappingIndex {
        val entries = JSONArray(json)
        val aniList = mutableMapOf<Int, MutableList<FribbTmdbId>>()
        val mal = mutableMapOf<Int, MutableList<FribbTmdbId>>()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val aniListId = entry.optInt("anilist_id")
            val malId = entry.optInt("mal_id")
            val tmdb = entry.optJSONObject("themoviedb_id") ?: continue
            if (aniListId <= 0 && malId <= 0) continue
            val ids = mutableListOf<FribbTmdbId>()
            tmdb.optInt("tv").takeIf { it > 0 }
                ?.let { ids.add(FribbTmdbId(TmdbMediaType.TV, it)) }
            tmdb.optJSONArray("movie")?.let { movies ->
                for (movieIndex in 0 until movies.length()) {
                    movies.optInt(movieIndex).takeIf { it > 0 }
                        ?.let { ids.add(FribbTmdbId(TmdbMediaType.MOVIE, it)) }
                }
            }
            if (ids.isEmpty()) continue
            if (aniListId > 0) aniList.getOrPut(aniListId) { mutableListOf() }.addAll(ids)
            if (malId > 0) mal.getOrPut(malId) { mutableListOf() }.addAll(ids)
        }
        return FribbMappingIndex(
            aniList.mapValues { (_, ids) -> ids.distinct() },
            mal.mapValues { (_, ids) -> ids.distinct() },
        )
    }

    fun preferred(ids: List<FribbTmdbId>, animeFormat: String?): FribbTmdbId? {
        val expected = if (animeFormat.equals("MOVIE", ignoreCase = true))
            TmdbMediaType.MOVIE else TmdbMediaType.TV
        return ids.firstOrNull { it.type == expected } ?: ids.firstOrNull()
    }

    fun compact(mappings: Map<Int, List<FribbTmdbId>>): String = JSONArray().apply {
        mappings.toSortedMap().forEach { (aniListId, ids) ->
            ids.forEach { id ->
                put(JSONArray().put(aniListId).put(id.type.path).put(id.id))
            }
        }
    }.toString()

    fun parseCompact(json: String): Map<Int, List<FribbTmdbId>> {
        val rows = JSONArray(json)
        val result = mutableMapOf<Int, MutableList<FribbTmdbId>>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONArray(index) ?: continue
            val aniListId = row.optInt(0)
            val type = TmdbMediaType.entries.firstOrNull { it.path == row.optString(1) }
            val tmdbId = row.optInt(2)
            if (aniListId > 0 && type != null && tmdbId > 0) {
                result.getOrPut(aniListId) { mutableListOf() }.add(FribbTmdbId(type, tmdbId))
            }
        }
        return result.mapValues { (_, ids) -> ids.distinct() }
    }

    fun compactIndex(index: FribbMappingIndex): String = JSONArray().apply {
        listOf("a" to index.byAniList, "m" to index.byMal).forEach { (source, mappings) ->
            mappings.toSortedMap().forEach { (sourceId, ids) ->
                ids.forEach { id ->
                    put(JSONArray().put(source).put(sourceId).put(id.type.path).put(id.id))
                }
            }
        }
    }.toString()

    fun parseCompactIndex(json: String): FribbMappingIndex {
        val aniList = mutableMapOf<Int, MutableList<FribbTmdbId>>()
        val mal = mutableMapOf<Int, MutableList<FribbTmdbId>>()
        val rows = JSONArray(json)
        for (index in 0 until rows.length()) {
            val row = rows.optJSONArray(index) ?: continue
            val target = when (row.optString(0)) {
                "a" -> aniList
                "m" -> mal
                else -> continue
            }
            val sourceId = row.optInt(1)
            val type = TmdbMediaType.entries.firstOrNull { it.path == row.optString(2) }
            val tmdbId = row.optInt(3)
            if (sourceId > 0 && type != null && tmdbId > 0) {
                target.getOrPut(sourceId) { mutableListOf() }.add(FribbTmdbId(type, tmdbId))
            }
        }
        return FribbMappingIndex(
            aniList.mapValues { (_, ids) -> ids.distinct() },
            mal.mapValues { (_, ids) -> ids.distinct() },
        )
    }
}
