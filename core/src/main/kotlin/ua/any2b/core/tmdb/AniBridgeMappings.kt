package ua.any2b.core.tmdb

import org.json.JSONObject

data class AniBridgeEpisodeRange(val source: String, val target: String)
data class AniBridgeTmdbTarget(
    val type: TmdbMediaType,
    val id: Int,
    val season: Int?,
    val episodes: List<AniBridgeEpisodeRange>,
)
data class AniBridgeAniListTarget(
    val aniListId: Int,
    val season: Int?,
    val episodes: List<AniBridgeEpisodeRange>,
)
data class AniBridgeMappingIndex(
    val version: String,
    val byAniList: Map<Int, List<AniBridgeTmdbTarget>>,
    val byMal: Map<Int, List<AniBridgeTmdbTarget>>,
    val byTmdb: Map<Pair<TmdbMediaType, Int>, List<AniBridgeAniListTarget>>,
) {
    fun anilistFor(type: TmdbMediaType, id: Int, season: Int? = null): List<AniBridgeAniListTarget> {
        val targets = byTmdb[type to id].orEmpty()
        return if (season == null) targets else targets.filter { it.season == season }
    }

    fun preferredFor(aniListId: Int, animeFormat: String?): AniBridgeTmdbTarget? =
        preferred(byAniList[aniListId].orEmpty(), animeFormat)

    fun preferredForMal(malId: Int, animeFormat: String?): AniBridgeTmdbTarget? =
        preferred(byMal[malId].orEmpty(), animeFormat)

    private fun preferred(candidates: List<AniBridgeTmdbTarget>, animeFormat: String?): AniBridgeTmdbTarget? {
        val preferredType = if (animeFormat.equals("MOVIE", ignoreCase = true))
            TmdbMediaType.MOVIE else TmdbMediaType.TV
        val preferred = candidates.filter { it.type == preferredType }
        val pool = preferred.ifEmpty { candidates }
        // A single AniList entry can cover several TMDB seasons. Equal coverage is ambiguous.
        val ranked = pool.sortedWith(compareByDescending<AniBridgeTmdbTarget> {
            it.episodes.sumOf { range -> episodeCount(range.source) }
        }.thenBy { it.season ?: 0 }.thenBy { it.id })
        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        if (second != null && first.id != second.id &&
            first.episodes.sumOf { episodeCount(it.source) } ==
            second.episodes.sumOf { episodeCount(it.source) }) return null
        return first
    }
}

/** Reads explicit AniList/MAL → TMDB and TMDB → AniList links from AniBridge. */
object AniBridgeMappings {
    private val aniList = Regex("^anilist:(\\d+)$")
    private val mal = Regex("^mal:(\\d+)$")
    private val tmdbShow = Regex("^tmdb_show:(\\d+):s(\\d+)$")
    private val tmdbMovie = Regex("^tmdb_movie:(\\d+)$")

    fun parse(json: String): AniBridgeMappingIndex {
        val root = JSONObject(json)
        val version = root.optJSONObject("\$meta")?.optString("schema_version").orEmpty()
        require(version.startsWith("3.")) { "Непідтримувана схема AniBridge: $version" }
        val byAniList = mutableMapOf<Int, MutableList<AniBridgeTmdbTarget>>()
        val byMal = mutableMapOf<Int, MutableList<AniBridgeTmdbTarget>>()
        val byTmdb = mutableMapOf<Pair<TmdbMediaType, Int>, MutableList<AniBridgeAniListTarget>>()
        val sources = root.keys()
        while (sources.hasNext()) {
            val source = sources.next()
            val targets = root.optJSONObject(source) ?: continue
            val sourceAniListId = aniList.matchEntire(source)?.groupValues?.get(1)?.toIntOrNull()
            val sourceMalId = mal.matchEntire(source)?.groupValues?.get(1)?.toIntOrNull()
            val sourceTmdb = tmdbDescriptor(source)
            if (sourceAniListId == null && sourceMalId == null && sourceTmdb == null) continue
            val names = targets.keys()
            while (names.hasNext()) {
                val target = names.next()
                val ranges = targets.optJSONObject(target) ?: continue
                val episodes = buildList {
                    val keys = ranges.keys()
                    while (keys.hasNext()) {
                        val from = keys.next()
                        val to = ranges.optString(from).takeIf(String::isNotBlank) ?: continue
                        add(AniBridgeEpisodeRange(from, to))
                    }
                }
                if (episodes.isEmpty()) continue
                if (sourceAniListId != null || sourceMalId != null) {
                    val tmdb = tmdbDescriptor(target) ?: continue
                    val mapped = AniBridgeTmdbTarget(tmdb.first, tmdb.second, tmdb.third, episodes)
                    if (sourceAniListId != null)
                        byAniList.getOrPut(sourceAniListId, ::mutableListOf) += mapped
                    if (sourceMalId != null)
                        byMal.getOrPut(sourceMalId, ::mutableListOf) += mapped
                } else if (sourceTmdb != null) {
                    val aniListId = aniList.matchEntire(target)?.groupValues?.get(1)?.toIntOrNull()
                        ?: continue
                    byTmdb.getOrPut(sourceTmdb.first to sourceTmdb.second, ::mutableListOf) +=
                        AniBridgeAniListTarget(aniListId, sourceTmdb.third, episodes)
                }
            }
        }
        return AniBridgeMappingIndex(version,
            byAniList.mapValues { (_, values) -> values.distinctBy { Triple(it.type, it.id, it.season) } },
            byMal.mapValues { (_, values) -> values.distinctBy { Triple(it.type, it.id, it.season) } },
            byTmdb.mapValues { (_, values) -> values.distinctBy { it.aniListId to it.season } })
    }

    private fun tmdbDescriptor(value: String): Triple<TmdbMediaType, Int, Int?>? {
        tmdbShow.matchEntire(value)?.let { match ->
            val id = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val season = match.groupValues[2].toIntOrNull() ?: return null
            return Triple(TmdbMediaType.TV, id, season)
        }
        tmdbMovie.matchEntire(value)?.let { match ->
            val id = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return Triple(TmdbMediaType.MOVIE, id, null)
        }
        return null
    }
}

private fun episodeCount(range: String): Int {
    val ends = range.split('-', limit = 2)
    val first = ends.firstOrNull()?.toIntOrNull() ?: return 0
    val last = ends.getOrNull(1)?.toIntOrNull() ?: first
    return (last - first + 1).coerceIn(1, 1000)
}
