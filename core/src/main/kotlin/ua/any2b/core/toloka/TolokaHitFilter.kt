package ua.any2b.core.toloka

import ua.any2b.core.anilist.AnimeDetails
import kotlin.math.abs
import kotlin.math.ceil

/** Conservative filtering: keep several plausible releases, reject clear title/year/season conflicts. */
object TolokaHitFilter {
    private val yearPattern = Regex("""\b(?:19|20)\d{2}\b""")
    private val seasonPatterns = listOf(
        Regex("""\bseason\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+season\b""", RegexOption.IGNORE_CASE),
        Regex("""\bсезон\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,2})\s+сезон\b""", RegexOption.IGNORE_CASE),
        Regex("""\bs(\d{1,2})\b""", RegexOption.IGNORE_CASE),
    )

    fun filter(details: AnimeDetails, ukrainianTitle: String?, hits: List<TolokaHit>,
        additionalNames: List<String> = emptyList()): List<TolokaHit> {
        val names = (listOfNotNull(ukrainianTitle, details.title.english, details.title.romaji,
            details.title.native) + details.synonyms + additionalNames)
            .map(::normalize).filter { it.length >= 4 }.distinct()
        val expectedSeason = (listOfNotNull(ukrainianTitle, details.title.english, details.title.romaji,
            details.title.native) + details.synonyms).firstNotNullOfOrNull(::seasonNumber)
        return hits.filter { hit ->
            val normalized = normalize(hit.title)
            val titleMatches = names.any { name ->
                normalized.contains(name) ||
                    nameTokens(name).let { tokens ->
                        val candidateTokens = nameTokens(normalized)
                        tokens.size >= 2 && tokens.first() in candidateTokens &&
                            tokens.count { it in candidateTokens } >=
                            ceil(tokens.size * 0.75).toInt().coerceAtLeast(2)
                    }
            }
            val years = yearPattern.findAll(hit.title).mapNotNull { it.value.toIntOrNull() }.toList()
            val yearMatches = details.seasonYear == null || years.isEmpty() ||
                years.any { abs(it - details.seasonYear) <= 1 }
            val foundSeason = seasonNumber(hit.title)
            titleMatches && yearMatches &&
                (expectedSeason == null || foundSeason == null || foundSeason == expectedSeason)
        }
    }

    private fun seasonNumber(value: String): Int? = seasonPatterns.firstNotNullOfOrNull { pattern ->
        pattern.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private fun nameTokens(value: String): Set<String> = value.split(' ')
        .filter { it.length >= 3 }.toSet()
}
