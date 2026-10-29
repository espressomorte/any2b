package ua.any2b.core.matcher

import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeSearchResult
import kotlin.math.abs
import kotlin.math.max

data class MatchResult(
    val result: AnitubeSearchResult,
    val score: Double, // weighted points, not a probability
    val matchedField: String,
    val titleSimilarity: Double,
    val metadataMatches: Int,
    val conflictingMetadata: Boolean,
)

/** Ranks AniTube releases using names and available AniList/AniTube metadata. */
object TitleMatcher {
    private const val MIN_AUTO_SCORE = 3.0
    private const val MIN_AUTO_MARGIN = 1.5

    fun match(details: AnimeDetails, candidates: List<AnitubeSearchResult>,
        additionalNames: List<String> = emptyList()): List<MatchResult> =
        rank(details.title, details.synonyms + additionalNames, details.seasonYear, details.episodes,
            details.durationMinutes, details.nextEpisode, candidates)

    /** Title-only matching is also used by the CLI when AniList details are unavailable. */
    fun match(title: Title, candidates: List<AnitubeSearchResult>): List<MatchResult> =
        rank(title, emptyList(), null, null, null, null, candidates)

    fun confidentBest(matches: List<MatchResult>): AnitubeSearchResult? {
        val best = matches.firstOrNull() ?: return null
        if (best.score < MIN_AUTO_SCORE || best.conflictingMetadata) return null
        if (best.titleSimilarity < 0.65 && best.metadataMatches < 2) return null
        if (matches.getOrNull(1)?.let { best.score - it.score < MIN_AUTO_MARGIN } == true) return null
        return best.result
    }

    private fun rank(
        title: Title,
        synonyms: List<String>,
        year: Int?,
        totalEpisodes: Int?,
        durationMinutes: Int?,
        nextEpisode: Int?,
        candidates: List<AnitubeSearchResult>,
    ): List<MatchResult> {
        val searchNames = buildList {
            title.romaji?.let { add("romaji" to it) }
            title.english?.let { add("english" to it) }
            title.native?.let { add("native" to it) }
            synonyms.forEach { add("synonym" to it) }
        }.filter { it.second.isNotBlank() }

        return candidates.map { candidate ->
            val normalizedCandidate = normalize(candidate.title)
            val bestTitle = searchNames.maxByOrNull { (_, name) ->
                calculateSimilarity(normalize(name), normalizedCandidate)
            }
            val titleSimilarity = bestTitle?.let { calculateSimilarity(normalize(it.second), normalizedCandidate) } ?: 0.0
            var score = titleSimilarity * 4.0
            var metadataMatches = 0
            var conflictingMetadata = false

            if (year != null && candidate.year != null) {
                when (abs(year - candidate.year)) {
                    0 -> { score += 3.0; metadataMatches++ }
                    1 -> { score += 1.0; metadataMatches++ }
                    else -> conflictingMetadata = true
                }
            }

            if (durationMinutes != null && candidate.durationMinutes != null) {
                val difference = abs(durationMinutes - candidate.durationMinutes)
                when {
                    difference <= 5 -> { score += 2.0; metadataMatches++ }
                    difference >= max(10, durationMinutes / 2) -> {
                        score -= 3.0
                        conflictingMetadata = true
                    }
                }
            }

            if (totalEpisodes != null) {
                if (candidate.totalEpisodes == totalEpisodes) {
                    score += 2.0
                    metadataMatches++
                }
                if (candidate.airedEpisodes != null && candidate.airedEpisodes > totalEpisodes) {
                    score -= 3.0
                    conflictingMetadata = true
                }
            } else if (nextEpisode != null && candidate.airedEpisodes != null) {
                val airedOnAniList = nextEpisode - 1
                when {
                    candidate.airedEpisodes > airedOnAniList -> {
                        score -= 3.0
                        conflictingMetadata = true
                    }
                    candidate.airedEpisodes == airedOnAniList -> {
                        score += 1.0
                        metadataMatches++
                    }
                }
            }

            MatchResult(candidate, score, bestTitle?.first ?: "none",
                titleSimilarity, metadataMatches, conflictingMetadata)
        }.sortedWith(compareByDescending<MatchResult> { it.score }.thenBy { it.result.newsId })
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""\(.*?\)"""), " ")
            .replace(Regex("""\[.*?\]"""), " ")
            .replace(Regex("""[^a-z0-9а-яіїєґ ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun calculateSimilarity(first: String, second: String): Double {
        if (first.isEmpty() || second.isEmpty()) return 0.0
        if (first == second) return 1.0
        if (first in second || second in first) return 0.85

        val firstTokens = first.split(" ").filter { it.isNotBlank() }.toSet()
        val secondTokens = second.split(" ").filter { it.isNotBlank() }.toSet()
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0.0
        return firstTokens.intersect(secondTokens).size.toDouble() / firstTokens.union(secondTokens).size
    }
}
