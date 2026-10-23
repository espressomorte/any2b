package ua.any2b.core.matcher

import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeSearchResult
import kotlin.math.max

data class MatchResult(
    val result: AnitubeSearchResult,
    val score: Double, // 0.0 .. 1.0
    val matchedField: String,
)

/**
 * Співставлення назви тайтла з AniList (Romaji / English) із результатами пошуку на AniTube.
 */
object TitleMatcher {

    /**
     * Ранжує знайдені релізи на AniTube за відповідністю до тайтла AniList.
     */
    fun match(
        title: Title,
        candidates: List<AnitubeSearchResult>,
        expectedSeason: Int? = null,
    ): List<MatchResult> {
        val searchNames = listOfNotNull(
            title.romaji?.let { "romaji" to it },
            title.english?.let { "english" to it },
        )

        return candidates.mapNotNull { candidate ->
            var bestScore = 0.0
            var bestField = "none"

            val normCandidate = normalize(candidate.title)
            val candidateSeason = extractSeasonNumber(candidate.title)

            for ((field, name) in searchNames) {
                val normQuery = normalize(name)
                var score = calculateSimilarity(normQuery, normCandidate)

                // Бонус або штраф за сезон
                if (expectedSeason != null && candidateSeason != null) {
                    if (expectedSeason == candidateSeason) {
                        score = (score + 0.2).coerceAtMost(1.0)
                    } else {
                        score = (score - 0.4).coerceAtLeast(0.0)
                    }
                }

                if (score > bestScore) {
                    bestScore = score
                    bestField = field
                }
            }

            if (bestScore >= 0.25) {
                MatchResult(result = candidate, score = bestScore, matchedField = bestField)
            } else null
        }.sortedByDescending { it.score }
    }

    private fun normalize(str: String): String =
        str.lowercase()
            .replace(Regex("""\(.*?\)"""), "") // видаляємо круглі дужки
            .replace(Regex("""\[.*?\]"""), "") // видаляємо квадратні дужки
            .replace(Regex("""[^a-z0-9а-яіїєґ ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun extractSeasonNumber(title: String): Int? {
        val match = Regex("""(\d+)\s*(?:сезон|season)""", RegexOption.IGNORE_CASE).find(title)
            ?: Regex("""(?:сезон|season)\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Схожість за коефіцієнтом перетину слів (Jaccard + підстрічковий пошук).
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0
        if (s1 in s2 || s2 in s1) return 0.85

        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return intersection.toDouble() / union.toDouble()
    }
}
