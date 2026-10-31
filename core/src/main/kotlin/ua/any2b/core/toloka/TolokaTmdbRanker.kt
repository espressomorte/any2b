package ua.any2b.core.toloka

import java.text.Normalizer
import kotlin.math.abs
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType

data class RankedTolokaHits(
    val visible: List<TolokaHit>,
    val weak: List<TolokaHit>,
)

/** Keeps video releases, ranks title variants, and leaves uncertain matches for manual review. */
object TolokaTmdbRanker {
    private val yearRe = Regex("""\b(?:19|20)\d{2}\b""")
    private val nonVideoRe = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:pdf|epub|fb2|mobi|azw3?|djvu|docx?|audiobook|audio[ -]?book|аудіокнига|аудиокнига|саундтрек|soundtrack|ost|mp3|flac|m4b|ebook|електронна[ -]?книга)(?![\p{L}\p{N}])"""
    )
    private val bookRe = Regex("""(?i)(?<![\p{L}\p{N}])(?:книга|книжка|книги|books?)(?![\p{L}\p{N}])""")
    private val videoRe = Regex("""(?i)\b(?:web[ -]?dl|webrip|bdrip|bdrmux|bluray|hdtv|dvdrip|1080p|2160p|720p|mkv|mp4|avi|h\.?264|h\.?265)\b""")
    private val stopWords = setOf("the", "and", "for", "with", "from", "year", "movie", "film",
        "серія", "фільм", "сезон", "частина")

    fun isVideoCandidate(hit: TolokaHit): Boolean {
        val title = hit.title
        if (nonVideoRe.containsMatchIn(title)) return false
        if (bookRe.containsMatchIn(title) && !videoRe.containsMatchIn(title)) return false
        return true
    }

    fun rank(details: TmdbDetails, hits: List<TolokaHit>): RankedTolokaHits {
        val title = details.title
        val ukrainian = listOfNotNull(title.ukrainianTitle, title.title.takeIf(::hasCyrillic))
            .distinctBy(::normalize)
        val english = listOfNotNull(title.englishTitle,
            title.title.takeIf { !hasCyrillic(it) && title.originalLanguage == "en" },
            title.originalTitle.takeIf { title.originalLanguage == "en" })
            .distinctBy(::normalize)
        val other = (listOfNotNull(title.originalTitle) + details.alternativeTitles)
            .filterNot { name -> name in ukrainian || name in english }
            .distinctBy(::normalize)
        val expectedYear = title.year
        val ranked = hits.asSequence().filter(::isVideoCandidate).map { hit ->
            val normalized = normalize(hit.title)
            val ua = bestNameScore(normalized, ukrainian)
            val en = bestNameScore(normalized, english)
            val alt = bestNameScore(normalized, other)
            val years = yearRe.findAll(hit.title).mapNotNull { it.value.toIntOrNull() }
                .distinct().toList()
            val matchingYear = expectedYear != null && years.any { abs(it - expectedYear) <= 1 }
            val wrongYear = expectedYear != null && years.isNotEmpty() && !matchingYear
            val collection = details.title.type == TmdbMediaType.MOVIE && years.size > 1
            val score = ua + en + alt + (if (matchingYear) 4 else 0) -
                (if (wrongYear) 8 else 0) - (if (collection) 4 else 0)
            val weak = wrongYear || collection || maxOf(ua, en, alt) < 5 ||
                (ua > 0 && english.isNotEmpty() && en == 0 && alt == 0)
            Triple(hit, score, weak)
        }.toList()
        val order = compareByDescending<Triple<TolokaHit, Int, Boolean>> { it.second }
            .thenBy { it.first.title.lowercase() }
        return RankedTolokaHits(
            visible = ranked.filterNot { it.third }.sortedWith(order).map { it.first },
            weak = ranked.filter { it.third }.sortedWith(order).map { it.first },
        )
    }

    private fun bestNameScore(candidate: String, names: List<String>): Int = names.maxOfOrNull { name ->
        val normalized = normalize(name)
        val tokens = tokens(normalized)
        if (tokens.isEmpty()) return@maxOfOrNull 0
        val candidateTokens = tokens(candidate)
        if (normalized.length >= 4 && candidateTokens.containsAll(tokens) &&
            (tokens.size > 1 || candidateTokens.contains(normalized))) return@maxOfOrNull 6 + tokens.size
        val overlap = tokens.count { it in candidateTokens }
        if (tokens.size == 1 && overlap == 1) 5
        else if (tokens.size >= 2 && overlap >= 2 && overlap * 4 >= tokens.size * 3) 3 + overlap
        else 0
    } ?: 0

    private fun tokens(value: String): Set<String> = value.split(' ')
        .filter { it.length >= 3 && it !in stopWords }.toSet()

    private fun hasCyrillic(value: String): Boolean = value.any { it in '\u0400'..'\u04FF' }

    private fun normalize(value: String): String = Normalizer.normalize(
        value.replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&amp;", "&").replace("&laquo;", "«").replace("&raquo;", "»"),
        Normalizer.Form.NFKD,
    ).lowercase().replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ").trim()
}
