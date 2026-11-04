package ua.any2b.cli

import ua.any2b.core.anilist.AniListAnimeLookup
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.matcher.SeriesTitleSearch
import ua.any2b.core.matcher.MetadataDistance

internal data class RankedAniListCandidate(
    val anime: AniListAnimeLookup,
    val titleSimilarity: Double,
    val score: Double,
)

/** Candidate ranking and metadata checks for the catalog audit command. */
internal object AniListAuditRanker {
    fun cleanAniTubeTitle(value: String): String = value
        .replace(Regex("(?i)\\s*[\\[(][^\\])]*(?:сезон|season|сез|ова|ova|фільм|movie|film)[^\\])]*[\\])]"), " ")
        .let(SeriesTitleSearch::clean).trim()

    fun rank(item: AnitubeSearchResult, anime: AniListAnimeLookup): RankedAniListCandidate {
        val names = listOf(anime.title.romaji, anime.title.english, anime.title.native) + anime.synonyms
        val similarity = names.filterNotNull().maxOfOrNull { stringSimilarity(item.title, it) } ?: 0.0
        var score = similarity * 4.0
        item.year?.let { sourceYear -> anime.seasonYear?.let { actualYear ->
            score += when (MetadataDistance.between(sourceYear, actualYear)) {
                0 -> 3.0; 1 -> 1.0; else -> -3.0
            }
        } }
        item.totalEpisodes?.let { sourceEpisodes -> anime.episodes?.let { actualEpisodes ->
            score += when (MetadataDistance.between(sourceEpisodes, actualEpisodes)) {
                0 -> 2.0; 1 -> 1.0; in 2..3 -> 0.0; else -> -2.0
            }
        } }
        val expectsMovie = Regex("(?i)\\b(?:фільм|movie|film)\\b").containsMatchIn(item.title)
        if (expectsMovie && anime.format == "MOVIE") score += 2.0
        if (!expectsMovie && anime.format in setOf("TV", "TV_SHORT")) score += 0.5
        return RankedAniListCandidate(anime, similarity, score)
    }

    fun stringSimilarity(first: String, second: String): Double {
        fun normalize(value: String) = value.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("[^a-z0-9а-яіїєґ ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        val a = normalize(first)
        val b = normalize(second)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a in b || b in a) return 0.85
        val at = a.split(' ').filter(String::isNotBlank).toSet()
        val bt = b.split(' ').filter(String::isNotBlank).toSet()
        return at.intersect(bt).size.toDouble() / at.union(bt).size
    }

    fun withinOrUnknown(first: Int?, second: Int?, tolerance: Int): Boolean =
        MetadataDistance.withinOrUnknown(first, second, tolerance)
}
