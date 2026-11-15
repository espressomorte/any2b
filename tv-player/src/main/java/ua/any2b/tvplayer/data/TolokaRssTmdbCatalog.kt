package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import java.io.File
import java.util.Locale

data class TolokaRssTmdbRows(
    val animated: List<TmdbTitle>,
    val general: List<TmdbTitle>,
) {
    val isEmpty: Boolean get() = animated.isEmpty() && general.isEmpty()
}

/** RSS supplies releases; TMDB supplies identities, ratings, and weekly trend signals. */
class TolokaRssTmdbCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val rss = TolokaRssCatalog(appContext)
    private val aniList = AniListClient()
    private val aniListCatalog = AniListTmdbCatalog(appContext)
    private val animeMappings = LocalAnimeMappingRepository.get(appContext)
    private val file = File(appContext.filesDir, "toloka-rss-tmdb-v2.json")
    private val trendsFile = File(appContext.filesDir, "toloka-rss-trends.json")

    fun cached(): TolokaRssTmdbRows = ordered(rss.cached(), readMappings(), readTrends())

    suspend fun refresh(): TolokaRssTmdbRows = lock.withLock {
        val releases = rss.refresh()
        val mappings = readMappings().toMutableMap()
        val connection = workerTmdbConnection(appContext)
        val now = System.currentTimeMillis()
        val candidates = releases.distinctBy(::key).filter { release ->
            val mapping = mappings[key(release)]
            val checkedAt = mapping?.checkedAt ?: 0L
            val needsSeasonIdentity = release.animated && hasSeasonMarker(release.title) &&
                mapping?.title?.matchedAniListId == null &&
                ((mapping?.seasonIdentityAttemptedAt ?: 0L) == 0L ||
                    now - (mapping?.seasonIdentityAttemptedAt ?: 0L) >= SEASON_IDENTITY_RETRY_MS)
            checkedAt == 0L || now - checkedAt >= CACHE_AGE_MS || needsSeasonIdentity
        }.take(MATCH_BATCH_SIZE)
        if (connection == null && ordered(releases, mappings, readTrends()).isEmpty)
            throw IllegalStateException("Налаштуйте TMDB для каталогу Toloka")
        val permits = Semaphore(4)
        if (connection != null) for (batch in candidates.chunked(20)) {
            val resolved = coroutineScope {
                batch.map { release ->
                    async {
                        permits.withPermit {
                            try {
                                val checkedAt = System.currentTimeMillis()
                                val mapping = findMatch(release) { query ->
                                    connection.client.search(query, connection.accessKey)
                                }
                                key(release) to Mapping(mapping, checkedAt,
                                    if (release.animated && hasSeasonMarker(release.title)) checkedAt
                                    else mappings[key(release)]?.seasonIdentityAttemptedAt ?: 0L)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            resolved.forEach { (key, mapping) -> mappings[key] = mapping }
            if (resolved.isNotEmpty()) withContext(Dispatchers.IO) { writeMappings(mappings) }
        }
        rss.attachMappings(releases.mapNotNull { release ->
            mappings[key(release)]?.title?.let { release.id to it }
        }.toMap())
        val current = rss.cached()
        val snapshot = if (connection == null) readTrends()
            else refreshTrends(connection)
        ordered(current, mappings, snapshot)
    }

    private suspend fun refreshTrends(connection: WorkerTmdbConnection): TrendSnapshot? {
        val cached = readTrends()
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt in
            0 until TREND_CACHE_AGE_MS) return cached
        return try {
            val trending = (1..2).flatMap {
                connection.client.trendingTv(connection.accessKey, page = it)
            }
            val snapshot = TrendSnapshot(System.currentTimeMillis(),
                trending.map(TmdbTitle::id).distinct())
            withContext(Dispatchers.IO) { writeTrends(snapshot) }
            snapshot
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            cached
        }
    }

    private fun readTrends(): TrendSnapshot? = runCatching {
        val json = JSONObject(trendsFile.readText())
        fun ids(key: String) = json.getJSONArray(key).let { rows ->
            (0 until rows.length()).map { rows.getInt(it) }
        }
        TrendSnapshot(json.getLong("fetchedAt"), ids("trending"))
    }.getOrNull()

    private fun writeTrends(snapshot: TrendSnapshot) {
        val temp = File(trendsFile.parentFile, "${trendsFile.name}.tmp")
        temp.writeText(JSONObject().put("fetchedAt", snapshot.fetchedAt)
            .put("trending", JSONArray(snapshot.trending)).toString())
        if (!temp.renameTo(trendsFile)) {
            trendsFile.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun ordered(releases: List<TolokaRssRelease>, mappings: Map<String, Mapping>,
        snapshot: TrendSnapshot?):
        TolokaRssTmdbRows {
        val trendingIds = snapshot?.trending.orEmpty().toSet()
        fun mapped(animated: Boolean) = releases.filter { it.animated == animated }
            .mapNotNull { release ->
                val title = mappings[key(release)]?.title ?: release.tmdbId?.let { id ->
                    val type = TmdbMediaType.entries.firstOrNull {
                        it.path == release.tmdbType
                    } ?: return@let null
                    TmdbTitle(id, type, release.tmdbName ?: release.title, null,
                        YEAR.find(release.title)?.value?.toIntOrNull(),
                        release.tmdbPoster ?: release.thumbnail, null, release.tmdbRating,
                        popularity = release.tmdbPopularity, airDate = release.tmdbAirDate)
                } ?: return@mapNotNull null
                if (animated && hasSeasonMarker(release.title) && title.matchedAniListId == null)
                    return@mapNotNull null
                title to release.publishedAt
            }.let { TolokaRssRanker.rank(it, trendingIds, System.currentTimeMillis()) }
        return TolokaRssTmdbRows(mapped(true), mapped(false))
    }

    private suspend fun findMatch(release: TolokaRssRelease,
        search: suspend (String) -> List<TmdbTitle>): TmdbTitle? {
        val names = names(release.title)
        val year = YEAR.find(release.title)?.value?.toIntOrNull()
        if (release.animated) {
            findAnimeMatch(release, names, year)?.let { return it }
        }
        val expectedType = when {
            release.category.contains("серіал", ignoreCase = true) -> TmdbMediaType.TV
            release.category.contains("фільм", ignoreCase = true) -> TmdbMediaType.MOVIE
            else -> null
        }
        for (query in names.take(2)) {
            val ranked = search(query).mapNotNull { candidate ->
                if (expectedType != null && candidate.type != expectedType) return@mapNotNull null
                if (candidate.genreIds.isNotEmpty() &&
                    (16 in candidate.genreIds) != release.animated) return@mapNotNull null
                val candidateNames = listOfNotNull(candidate.title, candidate.originalTitle,
                    candidate.ukrainianTitle, candidate.englishTitle).map(::normalize)
                val exact = names.any { normalize(it) in candidateNames }
                val similarity = names.maxOfOrNull { name ->
                    candidateNames.maxOfOrNull { candidateName ->
                        fuzzySimilarity(normalize(name), candidateName)
                    } ?: 0.0
                } ?: 0.0
                if (!exact && (year == null || similarity < 0.90)) return@mapNotNull null
                val candidateYear = candidate.year
                val yearDifference = if (year != null && candidateYear != null)
                    kotlin.math.abs(year - candidateYear) else null
                if (yearDifference != null && yearDifference > 1) return@mapNotNull null
                val score = (if (exact) 100 else (similarity * 40).toInt()) +
                    (if (yearDifference == 0) 20 else if (yearDifference == 1) 5 else 0) +
                    (if (candidate.posterUrl != null) 2 else 0) +
                    (if (candidate.genreIds.isNotEmpty()) 1 else 0)
                candidate to score
            }.sortedByDescending { it.second }
            val best = ranked.firstOrNull() ?: continue
            if (ranked.size > 1 && best.second == ranked[1].second &&
                best.first.id != ranked[1].first.id) continue
            return best.first.copy(posterUrl = best.first.posterUrl ?: release.thumbnail)
        }
        return if (release.animated) findAnimeMatch(release, names, year) else null
    }

    private suspend fun findAnimeMatch(release: TolokaRssRelease, names: List<String>,
        year: Int?): TmdbTitle? {
        val seasonNumber = seasonNumber(release.title)
        if (seasonNumber != null) {
            val ids = names.flatMap { query -> animeMappings.search(query, limit = 50) }
                .filter { it.seasonScope == seasonNumber }
                .flatMap { it.aniListIds }
                .distinct()
            if (ids.size == 1) {
                try {
                    aniListCatalog.resolveById(ids.single())?.let { return it }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the normal AniList name lookup as a fallback.
                }
            }
        }
        for (query in names.asReversed().take(2)) {
            val cards = try { aniList.search(query, perPage = 10) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { continue }
            val matches = cards.filter { card ->
                val cardYear = card.seasonYear
                val cardNames = listOfNotNull(card.title.romaji, card.title.english,
                    card.title.native).map(::normalize)
                names.any { normalize(it) in cardNames } &&
                    (year == null || cardYear == null ||
                        kotlin.math.abs(year - cardYear) <= 1)
            }
            val best = matches.sortedBy { card ->
                if (year != null && card.seasonYear == year) 0 else 1
            }.firstOrNull() ?: continue
            val title = try { aniListCatalog.resolveCards(listOf(best)).firstOrNull() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (title != null) return title.copy(posterUrl = title.posterUrl ?: release.thumbnail)
        }
        return null
    }

    private fun readMappings(): Map<String, Mapping> = runCatching {
        val rows = JSONObject(file.readText())
        rows.keys().asSequence().associateWith { key ->
            val row = rows.getJSONObject(key)
            Mapping(row.optJSONObject("title")?.let(::readTitle), row.optLong("checkedAt"),
                row.optLong("seasonIdentityAttemptedAt"))
        }
    }.getOrDefault(emptyMap())

    private fun writeMappings(mappings: Map<String, Mapping>) {
        val rows = JSONObject()
        mappings.forEach { (key, mapping) ->
            rows.put(key, JSONObject().put("checkedAt", mapping.checkedAt)
                .put("seasonIdentityAttemptedAt", mapping.seasonIdentityAttemptedAt)
                .put("title", mapping.title?.let(::writeTitle)))
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(rows.toString())
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun readTitle(row: JSONObject): TmdbTitle? {
        val type = TmdbMediaType.entries.firstOrNull { it.path == row.optString("type") }
            ?: return null
        val id = row.optInt("id").takeIf { it > 0 } ?: return null
        return TmdbTitle(id, type, row.optString("title"),
            row.optString("originalTitle").takeIf(String::isNotBlank),
            row.optInt("year").takeIf { it > 0 },
            row.optString("posterUrl").takeIf(String::isNotBlank),
            row.optString("overview").takeIf(String::isNotBlank),
            row.optDouble("rating", Double.NaN).takeIf(Double::isFinite),
            ukrainianTitle = row.optString("ukrainianTitle").takeIf(String::isNotBlank),
            englishTitle = row.optString("englishTitle").takeIf(String::isNotBlank),
            originalLanguage = row.optString("originalLanguage").takeIf(String::isNotBlank),
            genreIds = row.optJSONArray("genreIds")?.let { array ->
                (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }.toSet()
            }.orEmpty(),
            backdropUrl = row.optString("backdropUrl").takeIf(String::isNotBlank),
            popularity = row.optDouble("popularity", Double.NaN).takeIf(Double::isFinite),
            airDate = row.optString("airDate").takeIf(String::isNotBlank),
            matchedSeasonTitle = row.optString("matchedSeasonTitle").takeIf(String::isNotBlank),
            matchedSeasonYear = row.optInt("matchedSeasonYear").takeIf { it > 0 },
            matchedSeasonNumber = row.optInt("matchedSeasonNumber")
                .takeIf { row.has("matchedSeasonNumber") && !row.isNull("matchedSeasonNumber") && it >= 0 },
            matchedAniListId = row.optInt("matchedAniListId")
                .takeIf { row.has("matchedAniListId") && !row.isNull("matchedAniListId") && it > 0 })
    }

    private fun writeTitle(title: TmdbTitle) = JSONObject()
        .put("id", title.id).put("type", title.type.path).put("title", title.title)
        .put("originalTitle", title.originalTitle).put("year", title.year)
        .put("posterUrl", title.posterUrl).put("overview", title.overview)
        .put("rating", title.rating).put("ukrainianTitle", title.ukrainianTitle)
        .put("englishTitle", title.englishTitle).put("originalLanguage", title.originalLanguage)
        .put("genreIds", JSONArray(title.genreIds.toList()))
        .put("matchedSeasonTitle", title.matchedSeasonTitle)
        .put("matchedSeasonYear", title.matchedSeasonYear)
        .put("matchedSeasonNumber", title.matchedSeasonNumber)
        .put("matchedAniListId", title.matchedAniListId)
        .put("backdropUrl", title.backdropUrl)
        .put("popularity", title.popularity).put("airDate", title.airDate)

    private fun key(release: TolokaRssRelease): String {
        val year = YEAR.find(release.title)?.value.orEmpty()
        return "${release.animated}:$year:${names(release.title).firstOrNull()?.let(::normalize).orEmpty()}"
    }

    private fun names(title: String): List<String> {
        val year = YEAR.find(title)
        val prefix = if (year == null) title.substringBefore(" | ")
            else title.substring(0, year.range.first)
        return prefix.split(" / ").map { it.replace(BRACKETED, " ").trim() }
            .filter { it.length >= 2 }.distinctBy(::normalize)
    }

    private fun hasSeasonMarker(title: String): Boolean = SEASON_MARKER.containsMatchIn(title)

    private fun seasonNumber(title: String): Int? {
        val match = SEASON_NUMBER.find(title) ?: return null
        return match.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }
    }

    private fun normalize(name: String) = name.lowercase(Locale.ROOT)
        .replace(NON_WORD, " ").trim().replace(WHITESPACE, " ")

    private fun fuzzySimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.length < 5 || right.length < 5) return 0.0
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1)
            }
            current.copyInto(previous)
        }
        return 1.0 - previous[right.length].toDouble() / maxOf(left.length, right.length)
    }

    private data class Mapping(val title: TmdbTitle?, val checkedAt: Long,
        val seasonIdentityAttemptedAt: Long = 0L)
    private data class TrendSnapshot(val fetchedAt: Long, val trending: List<Int>)

    companion object {
        private val lock = Mutex()
        private const val CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val SEASON_IDENTITY_RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private const val MATCH_BATCH_SIZE = 120
        private const val TREND_CACHE_AGE_MS = 6L * 60 * 60 * 1000
        private val YEAR = Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)")
        private val BRACKETED = Regex("\\([^)]*\\)|\\[[^]]*]")
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val WHITESPACE = Regex("\\s+")
        private val SEASON_MARKER = Regex(
            "(?:\\bseason\\s*\\d+\\b|\\b\\d+\\s*(?:st|nd|rd|th)?\\s+season\\b|" +
                "\\bсезон\\s*\\d+\\b|\\b\\d+\\s*сезон\\b|\\bs\\s*\\d+\\b)",
            RegexOption.IGNORE_CASE,
        )
        private val SEASON_NUMBER = Regex(
            "\\bseason\\s*(\\d+)\\b|\\b(\\d+)\\s*(?:st|nd|rd|th)?\\s+season\\b|" +
                "\\bсезон\\s*(\\d+)\\b|\\b(\\d+)\\s*сезон\\b|\\bs\\s*(\\d+)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}
