package ua.any2b.core.toloka

import ua.any2b.core.anilist.AnimeDetails
import kotlin.math.abs
import kotlin.math.ceil

/** Conservative filtering: keep several plausible releases, reject clear title/year/season conflicts. */
object TolokaHitFilter {
    fun filter(details: AnimeDetails, ukrainianTitle: String?, hits: List<TolokaHit>,
        additionalNames: List<String> = emptyList(), learnedTitles: Set<String> = emptySet(),
        ignoredTitles: Set<String> = emptySet()): List<TolokaHit> {
        val names = (listOfNotNull(ukrainianTitle, details.title.english, details.title.romaji,
            details.title.native) + details.synonyms + additionalNames)
            .flatMap { TolokaReleaseMetadataParser.parse(it).titleCandidates }
            .map(::normalize).filter { it.length >= 4 }.distinct()
        val expectedSeason = (listOfNotNull(ukrainianTitle, details.title.english, details.title.romaji,
            details.title.native) + details.synonyms).firstNotNullOfOrNull(::seasonNumber)
        val learned = learnedTitles.map(::normalize).filter(String::isNotBlank)
        val ignored = ignoredTitles.map(::normalize).filter(String::isNotBlank).toSet()
        return hits.filter { hit ->
            val normalized = normalize(hit.title)
            if (normalized in ignored) return@filter false
            val release = TolokaReleaseMetadataParser.parse(hit.title)
            val candidateTitle = normalize(release.cleanTitle)
            val titleMatches = names.any { name ->
                candidateTitle.contains(name) ||
                    nameTokens(name).let { tokens ->
                        val candidateTokens = nameTokens(candidateTitle)
                        tokens.size >= 2 && tokens.first() in candidateTokens &&
                            tokens.count { it in candidateTokens } >=
                            ceil(tokens.size * 0.75).toInt().coerceAtLeast(2)
                    }
            }
            val years = release.yearRanges.flatMap { listOf(it.first, it.last) }.distinct()
            val yearMatches = details.seasonYear == null || years.isEmpty() ||
                years.any { abs(it - details.seasonYear) <= 1 }
            val foundSeason = release.season
            val learnedMatch = learned.any { approved ->
                if (approved == normalized) true else {
                    val approvedTokens = nameTokens(approved)
                    val candidateTokens = nameTokens(normalized)
                    approvedTokens.size >= 2 && candidateTokens.isNotEmpty() &&
                        approvedTokens.intersect(candidateTokens).size.toDouble() /
                        minOf(approvedTokens.size, candidateTokens.size) >= 0.75
                }
            }
            (learnedMatch || (TolokaTmdbRanker.isVideoCandidate(hit) && titleMatches && yearMatches &&
                (expectedSeason == null || foundSeason == null || expectedSeason in foundSeason..(release.seasonEnd ?: foundSeason))))
        }.sortedBy { hit ->
            val release = TolokaReleaseMetadataParser.parse(hit.title)
            when {
                expectedSeason == null -> 1
                release.season == expectedSeason -> 0
                release.isAbsoluteEpisodeRange -> 1
                release.season == null -> 2
                else -> 3 // Explicit wrong seasons are filtered above; retain stable ordering.
            }
        }
    }

    private fun seasonNumber(value: String): Int? = TolokaReleaseMetadataParser.parse(value).season

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private fun nameTokens(value: String): Set<String> = value.split(' ')
        .filter { it.length >= 3 }.toSet()
}
