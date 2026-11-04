package ua.any2b.core.toloka

import java.text.Normalizer
import kotlin.math.abs
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType

data class RankedTolokaHits(
    val visible: List<TolokaHit>,
    val weak: List<TolokaHit>,
    val rejected: List<TolokaHit> = emptyList(),
)

/** Keeps video releases, ranks title variants, and leaves uncertain matches for manual review. */
object TolokaTmdbRanker {
    private val yearRe = Regex("""\b(?:19|20)\d{2}\b""")
    private val nonVideoRe = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:pdf|epub|fb2|mobi|azw3?|djvu|docx?|audiobook|audio[ -]?book|аудіокнига|аудиокнига|саундтрек|soundtrack|ost|mp3|flac|m4b|ebook|електронна[ -]?книга|amv|music[ ._-]*video|music[ ._-]*clip|музичн(?:е|ий)[ ._-]*(?:відео|кліп)|видеоклип|кліп)(?![\p{L}\p{N}])"""
    )
    private val bookRe = Regex("""(?i)(?<![\p{L}\p{N}])(?:книга|книжка|книги|books?)(?![\p{L}\p{N}])""")
    private val videoRe = Regex("""(?i)\b(?:web[ -]?dl|webrip|bdrip|bdrmux|bluray|hdtv|dvdrip|1080p|2160p|720p|mkv|mp4|avi|h\.?264|h\.?265)\b""")
    private val stopWords = setOf("the", "and", "for", "with", "from", "year", "movie", "film",
        "серія", "фільм", "сезон", "частина", "1080p", "720p", "2160p", "480p", "web",
        "webrip", "webdl", "bdrip", "bluray", "hdtv", "x264", "x265", "h264", "h265", "mkv",
        "season", "episodes", "episode", "eps", "серії", "сезон", "укр", "ukr", "jap")

    fun isVideoCandidate(hit: TolokaHit): Boolean {
        val title = hit.title
        if (nonVideoRe.containsMatchIn(title)) return false
        if (bookRe.containsMatchIn(title) && !videoRe.containsMatchIn(title)) return false
        return true
    }

    fun rank(details: TmdbDetails, hits: List<TolokaHit>, learnedTitles: Set<String> = emptySet(),
        ignoredTitles: Set<String> = emptySet()): RankedTolokaHits {
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
        val expectedSeason = expectedSeason(details)
        val expectedYear = title.matchedSeasonYear ?: title.year
        val expectedEpisodeCount = expectedSeason?.episodeCount
        val normalizedLearned = learnedTitles.map(::normalize).filter(String::isNotBlank)
        val normalizedIgnored = ignoredTitles.map(::normalize).filter(String::isNotBlank).toSet()
        fun wasManuallyApproved(candidate: String): Boolean {
            val candidateNormalized = normalize(candidate)
            val candidateTokens = tokens(candidateNormalized)
            return normalizedLearned.any { approved ->
                val approvedTokens = tokens(approved)
                if (candidateNormalized == approved) true
                else if (approvedTokens.size < 2 || candidateTokens.isEmpty()) false
                else approvedTokens.intersect(candidateTokens).size.toDouble() /
                    minOf(approvedTokens.size, candidateTokens.size) >= 0.75
            }
        }
        val rejected = hits.filter { hit -> normalize(hit.title) !in normalizedIgnored &&
            !isVideoCandidate(hit) && !wasManuallyApproved(hit.title)
        }
        val ranked = hits.asSequence().filter { hit -> normalize(hit.title) !in normalizedIgnored &&
            (isVideoCandidate(hit) || wasManuallyApproved(hit.title))
        }.map { hit ->
            val learned = wasManuallyApproved(hit.title)
            val release = TolokaReleaseMetadataParser.parse(hit.title)
            val normalized = normalize(release.cleanTitle)
            val ua = bestNameScore(normalized, ukrainian)
            val en = bestNameScore(normalized, english)
            val alt = bestNameScore(normalized, other)
            val years = yearRe.findAll(hit.title).mapNotNull { it.value.toIntOrNull() }
                .distinct().toList()
            val absoluteSeasonRanges = TolokaReleaseMetadataParser.mapAbsoluteEpisodes(
                release, details.seasonEntries)
            val releaseSeason = release.season?.takeIf { release.seasonEnd == null || release.seasonEnd == it }?.let { number ->
                details.seasonEntries.firstOrNull { it.number == number && it.number > 0 }
            }
            val candidateYear = when {
                release.isAbsoluteEpisodeRange -> null
                expectedSeason == null && release.season != null -> releaseSeason?.year ?: expectedYear
                else -> expectedYear
            }
            val matchingYear = candidateYear != null && years.any { abs(it - candidateYear) <= 1 }
            val wrongYear = candidateYear != null && years.isNotEmpty() && !matchingYear
            val collection = details.title.type == TmdbMediaType.MOVIE && years.size > 1
            val explicitWrongSeason = expectedSeason?.let { expected ->
                when {
                    // An absolute episode pack without a known season map is still a
                    // usable release. Reject it only when mapping proves it excludes Sx.
                    release.isAbsoluteEpisodeRange -> absoluteSeasonRanges?.none {
                        it.seasonNumber == expected.number
                    } == true
                    release.season != null -> expected.number !in release.season..
                        (release.seasonEnd ?: release.season)
                    else -> false
                }
            } == true
            val yearSeasonMismatch = expectedSeason != null && release.season == null &&
                !release.isAbsoluteEpisodeRange &&
                years.any { releaseYear ->
                    details.seasonEntries.any { it.number > 0 && it.number != expectedSeason.number &&
                        it.year == releaseYear }
                }
            val wrongSeason = explicitWrongSeason || yearSeasonMismatch
            val episodeOverflow = !release.isAbsoluteEpisodeRange && expectedEpisodeCount != null && release.episodeEnd != null &&
                release.episodeEnd > expectedEpisodeCount
            val seasonMatch = expectedSeason != null && when {
                release.isAbsoluteEpisodeRange -> absoluteSeasonRanges?.any {
                    it.seasonNumber == expectedSeason.number
                } == true
                else -> release.season == expectedSeason.number
            }
            val qualityAndAvailability = release.qualityScore + (hit.seeders?.coerceAtMost(20)?.div(5) ?: 0)
            val score = ua + en + alt + (if (matchingYear) 4 else 0) +
                (if (seasonMatch) 4 else 0) +
                qualityAndAvailability + (if (learned) 12 else 0) - (if (wrongYear) 8 else 0) -
                (if (wrongSeason) 12 else 0) - (if (episodeOverflow) 7 else 0) -
                (if (collection) 4 else 0)
            val weak = !learned && (wrongYear || wrongSeason || episodeOverflow || collection ||
                maxOf(ua, en, alt) < 5 ||
                (ua > 0 && english.isNotEmpty() && en == 0 && alt == 0)
            )
            Triple(hit, score, weak)
        }.toList()
        val order = compareByDescending<Triple<TolokaHit, Int, Boolean>> { it.second }
            .thenBy { it.first.title.lowercase() }
        return RankedTolokaHits(
            visible = ranked.filterNot { it.third }.sortedWith(order).map { it.first },
            weak = ranked.filter { it.third }.sortedWith(order).map { it.first },
            rejected = rejected,
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
        else fuzzyNameScore(tokens, candidateTokens)
    } ?: 0

    private fun fuzzyNameScore(nameTokens: Set<String>, candidateTokens: Set<String>): Int {
        if (nameTokens.isEmpty() || candidateTokens.isEmpty()) return 0
        val matched = nameTokens.count { expected ->
            if (expected.length < 5) false else candidateTokens.any { candidate ->
                candidate == expected || tokenSimilarity(expected, candidate) >= 0.78
            }
        }
        val ratio = matched.toDouble() / nameTokens.size
        return when {
            ratio >= 0.85 && matched >= 2 -> 4 + matched
            ratio >= 0.75 && nameTokens.size >= 3 && matched >= 2 -> 3 + matched
            ratio == 1.0 && nameTokens.size == 1 -> 4
            else -> 0
        }
    }

    private fun tokenSimilarity(first: String, second: String): Double {
        if (first == second) return 1.0
        if (minOf(first.length, second.length) < 5) return 0.0
        val shorter = if (first.length <= second.length) first else second
        val longer = if (first.length <= second.length) second else first
        var previous = IntArray(shorter.length + 1) { it }
        for (row in longer.indices) {
            val current = IntArray(shorter.length + 1)
            current[0] = row + 1
            for (column in shorter.indices) {
                val substitution = if (longer[row] == shorter[column]) 0 else 1
                current[column + 1] = minOf(
                    current[column] + 1,
                    previous[column + 1] + 1,
                    previous[column] + substitution,
                )
            }
            previous = current
        }
        return 1.0 - previous[shorter.length].toDouble() / maxOf(first.length, second.length)
    }

    private fun expectedSeason(details: TmdbDetails): ua.any2b.core.tmdb.TmdbSeason? {
        val seasons = details.seasonEntries.filter { it.number > 0 }
        if (seasons.isEmpty()) return null
        val titleNames = listOfNotNull(details.title.matchedSeasonTitle, details.title.title,
            details.title.englishTitle, details.title.originalTitle) + details.alternativeTitles
        val explicit = titleNames.firstNotNullOfOrNull { name ->
            TolokaReleaseMetadataParser.parse(name).season
        }
        if (explicit != null) seasons.firstOrNull { it.number == explicit }?.let { return it }
        if (details.title.matchedSeasonTitle != null) {
            val targetYear = details.title.matchedSeasonYear ?: details.title.year
            val yearMatches = targetYear?.let { year -> seasons.filter { it.year == year } }.orEmpty()
            if (yearMatches.size == 1) return yearMatches.single()
            val matchedName = normalize(details.title.matchedSeasonTitle)
            seasons.firstOrNull { season ->
                val seasonName = season.name?.let(::normalize).orEmpty()
                seasonName.isNotBlank() && (matchedName.contains(seasonName) ||
                    seasonName.contains(matchedName))
            }?.let { return it }
        }
        return seasons.singleOrNull()
    }

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
