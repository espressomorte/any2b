package ua.any2b.core.tmdb

import kotlin.math.abs
import ua.any2b.core.anilist.AnimeDetails

data class TmdbMatch(val title: TmdbTitle, val score: Double, val titleSimilarity: Double)

/** Ranks candidates for review; a search result is never persisted without user confirmation. */
object AniListTmdbMatcher {
    fun rank(anime: AnimeDetails, results: List<TmdbTitle>): List<TmdbMatch> {
        val expectedType = if (anime.format.equals("MOVIE", ignoreCase = true))
            TmdbMediaType.MOVIE else TmdbMediaType.TV
        val names = (listOf(anime.title.english, anime.title.romaji, anime.title.native) +
            anime.synonyms).filterNotNull().map(::normalize).filter(String::isNotBlank)
        return results.asSequence()
            .filter { it.type == expectedType }
            .map { candidate ->
                val candidateNames = listOf(candidate.title, candidate.englishTitle,
                    candidate.ukrainianTitle, candidate.originalTitle,
                    candidate.matchedSeasonTitle).filterNotNull()
                val similarity = names.maxOfOrNull { source ->
                    candidateNames.maxOfOrNull { name -> similar(source, normalize(name)) } ?: 0.0
                } ?: 0.0
                var score = similarity * 4 + 2 // Matching TV/movie type.
                val effectiveYear = candidate.matchedSeasonYear ?: candidate.year
                if (anime.seasonYear != null && effectiveYear != null) {
                    score += when (abs(anime.seasonYear - effectiveYear)) {
                        0 -> 3.0
                        1 -> 1.0
                        else -> -3.0
                    }
                }
                if (candidate.genreIds.isNotEmpty()) {
                    score += if (16 in candidate.genreIds) 2.0 else -4.5
                }
                if (candidate.originalLanguage == "ja") score += 0.5
                if ("JP" in candidate.originCountries) score += 0.5
                TmdbMatch(candidate, score, similarity)
            }
            .filter { it.titleSimilarity >= 0.55 && it.score >= 7.0 }
            .sortedWith(compareByDescending<TmdbMatch> { it.score }.thenBy { it.title.id })
            .toList()
    }

    fun clearSuggestion(matches: List<TmdbMatch>): TmdbTitle? {
        val first = matches.firstOrNull() ?: return null
        if (first.score < 8.5 || first.titleSimilarity < 0.75) return null
        if (matches.getOrNull(1)?.let { first.score - it.score < 2 } == true) return null
        return first.title
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9а-яіїєґ ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun similar(first: String, second: String): Double {
        if (first.isBlank() || second.isBlank()) return 0.0
        if (first == second) return 1.0
        if (first in second || second in first) return 0.85
        val a = first.split(' ').toSet()
        val b = second.split(' ').toSet()
        return a.intersect(b).size.toDouble() / a.union(b).size
    }
}
