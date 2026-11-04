package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ua.any2b.core.anilist.AniListAnimeLookup
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeReleaseMetadata
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.core.matcher.TitleMatcher
import ua.any2b.core.tmdb.FribbAnimeEntry
import ua.any2b.core.tmdb.TmdbMediaType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private const val AUTO_SCORE = 3.0
private const val MIN_TITLE_SIMILARITY = 0.65
private const val MIN_SCORE_MARGIN = 1.5

enum class AnitubeReleaseKind { SEASON, FILM, OTHER }
enum class AniListAvailability { UNKNOWN, CHECKING, AVAILABLE, MISSING }

data class AnitubeCandidate(
    val release: AnitubeSearchResult,
    val score: Double,
    val aniListId: Int,
    val titleSimilarity: Double,
    val metadataMatches: Int,
    val conflictingMetadata: Boolean,
    val kind: AnitubeReleaseKind,
    val seasonNumber: Int? = null,
    val format: String? = null,
)

data class AnitubeSeasonGroup(
    val number: Int,
    val title: String,
    val aniListIds: List<Int>,
    val releases: List<AnitubeCandidate>,
)

data class AnitubeResolution(
    val seasons: List<AnitubeSeasonGroup>,
    val films: List<AnitubeCandidate>,
    val other: List<AnitubeCandidate>,
    val searchError: String? = null,
    val filmTitles: List<String> = emptyList(),
    val aniListAvailability: AniListAvailability = AniListAvailability.AVAILABLE,
    val preferredSeason: Int? = null,
    val preferFilm: Boolean = false,
) {
    val candidates: List<AnitubeCandidate>
        get() = (seasons.flatMap { it.releases } + films + other)
            .distinctBy { it.release.newsId }

    val confidentBest: AnitubeCandidate?
        get() {
            val preferred = when {
                preferredSeason != null -> candidates.filter {
                    it.kind == AnitubeReleaseKind.SEASON && it.seasonNumber == preferredSeason
                }
                preferFilm -> candidates.filter { it.kind == AnitubeReleaseKind.FILM }
                else -> seasons.firstOrNull { it.releases.isNotEmpty() }?.let { first ->
                    candidates.filter { it.kind == AnitubeReleaseKind.SEASON && it.seasonNumber == first.number }
                }.orEmpty()
            }
            val ranked = (preferred.takeIf { it.isNotEmpty() } ?: candidates)
                .sortedByDescending(AnitubeCandidate::score)
            return ranked.firstOrNull()?.takeIf { best ->
                val singleStrongResult = ranked.size == 1 && best.titleSimilarity >= 0.85 &&
                    best.metadataMatches >= 1
                (best.kind != AnitubeReleaseKind.OTHER || singleStrongResult) &&
                    (best.score >= AUTO_SCORE || singleStrongResult) &&
                    !best.conflictingMetadata &&
                    (best.titleSimilarity >= MIN_TITLE_SIMILARITY || best.metadataMatches >= 2) &&
                    ranked.getOrNull(1)?.let { best.score - it.score >= MIN_SCORE_MARGIN } != false
            }
        }
}

/** Builds a franchise catalog from Fribb/AniList, then classifies AniTube search results. */
class AnitubeResolver(
    context: Context,
    private val anilist: AniListClient = AniListClient(),
    private val anitube: AnitubeClient = AnitubeClient(),
) {
    private val appContext = context.applicationContext

    suspend fun find(
        type: TmdbMediaType,
        tmdbId: Int,
        tmdbSeason: Int?,
        tmdbNames: List<String>,
        onAniListAvailability: (AniListAvailability) -> Unit = {},
    ): AnitubeResolution {
        val index = FribbMappingRepository.get(appContext)
        val mapped = index.anilistFor(type, tmdbId)
        if (mapped.isEmpty()) {
            onAniListAvailability(AniListAvailability.MISSING)
            return AnitubeResolution(emptyList(), emptyList(), emptyList(),
                aniListAvailability = AniListAvailability.MISSING)
        }
        val seasonMatches = tmdbSeason?.let { season -> mapped.filter { it.tmdbSeason == season } }.orEmpty()
        val selectedEntries = if (seasonMatches.isNotEmpty()) seasonMatches else mapped
        val universe = loadRelationUniverse(selectedEntries.map(FribbAnimeEntry::aniListId))
        if (universe.isEmpty()) {
            onAniListAvailability(AniListAvailability.MISSING)
            return AnitubeResolution(emptyList(), emptyList(), emptyList(),
                aniListAvailability = AniListAvailability.MISSING)
        }
        onAniListAvailability(AniListAvailability.AVAILABLE)

        val allNames = (universe.values.flatMap { anime ->
            listOfNotNull(anime.title.romaji, anime.title.english, anime.title.native) + anime.synonyms
        } + tmdbNames).map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase)
        val preferredNames = selectedEntries.flatMap { entry ->
            val anime = universe[entry.aniListId] ?: return@flatMap emptyList()
            listOfNotNull(anime.title.romaji, anime.title.english, anime.title.native) + anime.synonyms
        }
        val searchNames = prioritizedSearchNames(preferredNames, allNames, tmdbNames)
        if (searchNames.isEmpty()) return AnitubeResolution(emptyList(), emptyList(), emptyList())

        val responses = searchAniTube(searchNames)
        val failures = responses.filter { it.isFailure }
        val releases = responses.mapNotNull { it.getOrNull() }.flatten()
            .distinctBy(AnitubeSearchResult::newsId)
        if (releases.isEmpty() && failures.size == responses.size) {
            return AnitubeResolution(emptyList(), emptyList(), emptyList(),
                failures.first().exceptionOrNull()?.message ?: "Не вдалося зв’язатися з AniTube")
        }

        val seasonEntries = universe.values.filter { it.format in SERIES_FORMATS }
        val orderedSeasons = orderSeasonChain(seasonEntries, universe)
        val mappedSeasonNumbers = selectedEntries.associate { it.aniListId to it.tmdbSeason }
        val seasonNumbers = assignSeasonNumbers(orderedSeasons, mappedSeasonNumbers)
        val seriesGroups = orderedSeasons.mapIndexed { index, anime ->
            val number = seasonNumbers[anime.id] ?: (index + 1)
            number to anime
        }.groupBy({ it.first }, { it.second })
        val seasonTitles = seriesGroups.mapValues { (_, entries) ->
            entries.firstNotNullOfOrNull { it.title.english ?: it.title.romaji ?: it.title.native }
                ?: "Сезон"
        }

        val filmsInUniverse = universe.values.filter { it.format == "MOVIE" }
        val scores = releases.associate { release ->
            release.newsId to bestMatch(release, universe.values, tmdbNames)
        }
        val ambiguous = releases.filter { release ->
            isUnmarked(release.title) && filmsInUniverse.isNotEmpty() &&
                scores[release.newsId]?.titleSimilarity?.let { it >= MIN_TITLE_SIMILARITY } == true
        }
        val releaseMetadata = loadAmbiguousMetadata(ambiguous)
        val seasonOneExists = seriesGroups.containsKey(1)
        val filmYears = filmsInUniverse.mapNotNull { it.seasonYear }.toSet()
        val seasonOneYears = seriesGroups[1].orEmpty().mapNotNull { it.seasonYear }.toSet()

        val classified = releases.mapNotNull { release ->
            val match = scores[release.newsId] ?: return@mapNotNull null
            if (match.score <= 0.0 || match.titleSimilarity < MIN_TITLE_SIMILARITY) return@mapNotNull null
            val explicitSeason = explicitSeason(release.title)
            val markerKind = markerKind(release.title)
            val (kind, seasonNumber) = when {
                markerKind == MarkerKind.OTHER -> AnitubeReleaseKind.OTHER to null
                explicitSeason != null -> if (seriesGroups.containsKey(explicitSeason))
                    AnitubeReleaseKind.SEASON to explicitSeason
                else AnitubeReleaseKind.OTHER to null
                markerKind == MarkerKind.FILM -> if (filmsInUniverse.isNotEmpty())
                    AnitubeReleaseKind.FILM to null else AnitubeReleaseKind.OTHER to null
                filmsInUniverse.isNotEmpty() && match.format == "MOVIE" &&
                    match.titleSimilarity >= 0.65 -> AnitubeReleaseKind.FILM to null
                filmsInUniverse.isNotEmpty() && match.format in SERIES_FORMATS &&
                    seasonOneExists -> AnitubeReleaseKind.SEASON to 1
                filmsInUniverse.isEmpty() && seasonOneExists -> AnitubeReleaseKind.SEASON to 1
                filmsInUniverse.isNotEmpty() -> classifyAmbiguous(releaseMetadata[release.newsId],
                    seasonOneExists, filmYears, seasonOneYears)
                else -> AnitubeReleaseKind.OTHER to null
            }
            AnitubeCandidate(release, match.score, match.aniListId, match.titleSimilarity,
                match.metadataMatches, match.conflictingMetadata, kind, seasonNumber, match.format)
        }

        val seasonGroups = seriesGroups.toSortedMap().map { (number, entries) ->
            AnitubeSeasonGroup(number, seasonTitles[number] ?: "Сезон $number",
                entries.map(AniListAnimeLookup::id),
                classified.filter { it.kind == AnitubeReleaseKind.SEASON && it.seasonNumber == number }
                    .sortedByDescending(AnitubeCandidate::score))
        }
        return AnitubeResolution(
            seasons = seasonGroups,
            films = classified.filter { it.kind == AnitubeReleaseKind.FILM }
                .sortedByDescending(AnitubeCandidate::score),
            other = classified.filter { it.kind == AnitubeReleaseKind.OTHER }
                .sortedByDescending(AnitubeCandidate::score),
            filmTitles = filmsInUniverse.mapNotNull {
                it.title.english ?: it.title.romaji ?: it.title.native
            }.distinct(),
            preferredSeason = tmdbSeason,
            preferFilm = type == TmdbMediaType.MOVIE,
        )
    }

    private suspend fun loadRelationUniverse(rootIds: List<Int>): Map<Int, AniListAnimeLookup> {
        val records = linkedMapOf<Int, AniListAnimeLookup>()
        var frontier = rootIds.filter { it > 0 }.distinct()
        var depth = 0
        while (frontier.isNotEmpty() && depth <= MAX_RELATION_DEPTH && records.size < MAX_RELATION_RECORDS) {
            val next = mutableListOf<Int>()
            frontier.chunked(LOOKUP_BATCH_SIZE).forEach { batch ->
                anilist.animeLookups(batch).forEach { anime ->
                    records[anime.id] = anime
                    anime.relations.filter { it.type in CHAIN_RELATIONS }
                        .map { it.anime.id }.filter { it !in records }
                        .forEach(next::add)
                }
            }
            frontier = next.distinct().filter { it !in records }
                .take((MAX_RELATION_RECORDS - records.size).coerceAtLeast(0))
            depth++
        }
        return records
    }

    private suspend fun searchAniTube(names: List<String>): List<Result<List<AnitubeSearchResult>>> {
        val permits = Semaphore(MAX_PARALLEL_SEARCHES)
        return coroutineScope {
            names.map { name -> async {
                permits.withPermit {
                    try { Result.success(anitube.search(name)) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Result.failure<List<AnitubeSearchResult>>(error) }
                }
            } }.awaitAll()
        }
    }

    private fun prioritizedSearchNames(
        preferredNames: List<String>, allNames: List<String>, tmdbNames: List<String>,
    ): List<String> {
        val prioritized = buildList {
            // AniList's canonical title and synonyms drive search. TMDB localized names
            // follow as aliases; remove media labels like "movie/кіно" that are not part
            // of AniTube release titles.
            (preferredNames + allNames + tmdbNames).forEach { name ->
                add(cleanSearchName(name))
                val base = stripSeasonSuffix(cleanSearchName(name))
                if (base != cleanSearchName(name)) add(base)
            }
        }.map(String::trim).filter(String::isNotBlank)
            .distinctBy(String::lowercase)
        return prioritized.take(MAX_SEARCH_NAMES)
    }

    private fun cleanSearchName(value: String): String = value
        .replace(Regex("(?i)(?<![\\p{L}\\p{N}])(?:movie|film|фільм|фильм|кіно)(?![\\p{L}\\p{N}])"), " ")
        .replace(Regex("\\s+"), " ").trim().trim(':', '-', '–', '—', '.', ' ')

    private suspend fun loadAmbiguousMetadata(
        releases: List<AnitubeSearchResult>,
    ): Map<Int, AnitubeReleaseMetadata> {
        val cached = releases.mapNotNull { release ->
            releaseMetadataCache[release.newsId]?.let { release.newsId to it }
        }.toMap()
        val pending = releases.filterNot { it.newsId in cached }
        val fetched = coroutineScope {
            pending.map { release -> async {
                try {
                    release.newsId to anitube.releaseMetadata(release.pageUrl).also {
                        releaseMetadataCache[release.newsId] = it
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            } }.awaitAll().filterNotNull().toMap()
        }
        return cached + fetched
    }

    private fun bestMatch(
        release: AnitubeSearchResult,
        anime: Collection<AniListAnimeLookup>,
        tmdbNames: List<String>,
    ): RankedCandidate? = anime.mapNotNull { entry ->
        val details = entry.asDetails()
        TitleMatcher.match(details, listOf(release), tmdbNames).firstOrNull()?.let { match ->
            RankedCandidate(entry.id, match.score, match.titleSimilarity, match.metadataMatches,
                match.conflictingMetadata, entry.format)
        }
    }.maxByOrNull(RankedCandidate::score)

    private fun orderSeasonChain(
        entries: List<AniListAnimeLookup>,
        all: Map<Int, AniListAnimeLookup>,
    ): List<AniListAnimeLookup> {
        val ids = entries.map(AniListAnimeLookup::id).toSet()
        val outgoing = ids.associateWith { mutableSetOf<Int>() }.toMutableMap()
        val indegree = ids.associateWith { 0 }.toMutableMap()
        all.values.forEach { current ->
            current.relations.filter { it.type in CHAIN_RELATIONS }.forEach { relation ->
                val relatedId = relation.anime.id
                if (current.id !in ids || relatedId !in ids) return@forEach
                val (before, after) = when (relation.type) {
                    "PREQUEL" -> relatedId to current.id
                    else -> current.id to relatedId
                }
                if (outgoing.getValue(before).add(after)) indegree[after] = indegree.getValue(after) + 1
            }
        }
        val byId = entries.associateBy(AniListAnimeLookup::id)
        val ready = entries.filter { indegree[it.id] == 0 }.toMutableList()
        val ordered = mutableListOf<AniListAnimeLookup>()
        fun dateKey(anime: AniListAnimeLookup) = anime.startDate ?: anime.seasonYear?.toString().orEmpty()
        while (ready.isNotEmpty()) {
            ready.sortWith(compareBy<AniListAnimeLookup>({ dateKey(it) }, { it.id }))
            val current = ready.removeAt(0)
            ordered += current
            outgoing.getValue(current.id).forEach { next ->
                indegree[next] = indegree.getValue(next) - 1
                if (indegree[next] == 0) ready += byId.getValue(next)
            }
        }
        if (ordered.size < entries.size) {
            ordered += (entries - ordered.toSet()).sortedWith(compareBy({ dateKey(it) }, { it.id }))
        }
        return ordered
    }

    private fun assignSeasonNumbers(
        ordered: List<AniListAnimeLookup>,
        mappedSeasonNumbers: Map<Int, Int?>,
    ): Map<Int, Int> {
        val anchors = ordered.mapIndexedNotNull { index, anime ->
            (mappedSeasonNumbers[anime.id]?.takeIf { it > 0 }
                ?: explicitSeason(anime.title, anime.synonyms))?.let { index to it }
        }
        val yearNumbers = ordered.mapNotNull(AniListAnimeLookup::seasonYear).distinct().sorted()
            .mapIndexed { index, year -> year to index + 1 }.toMap()
        return ordered.mapIndexed { index, anime ->
            val sameYearAnchor = anime.seasonYear?.let { year ->
                anchors.filter { (anchorIndex, _) -> ordered[anchorIndex].seasonYear == year }
                    .minByOrNull { abs(it.first - index) }
            }
            val prior = anchors.lastOrNull { it.first <= index }
            val following = anchors.firstOrNull { it.first > index }
            val number = when {
                sameYearAnchor != null -> sameYearAnchor.second
                prior != null -> prior.second + index - prior.first
                following != null -> following.second - (following.first - index)
                else -> anime.seasonYear?.let(yearNumbers::get) ?: (index + 1)
            }.coerceAtLeast(1)
            anime.id to number
        }.toMap()
    }

    private fun classifyAmbiguous(
        metadata: AnitubeReleaseMetadata?,
        seasonOneExists: Boolean,
        filmYears: Set<Int>,
        seasonOneYears: Set<Int>,
    ): Pair<AnitubeReleaseKind, Int?> {
        if (metadata?.episodeCount == 1 && filmYears.isNotEmpty()) return AnitubeReleaseKind.FILM to null
        if ((metadata?.episodeCount ?: 0) > 1 && seasonOneExists) return AnitubeReleaseKind.SEASON to 1
        val year = metadata?.year
        if (year != null && filmYears.isNotEmpty() && seasonOneExists) {
            val movieDistance = filmYears.minOf { abs(it - year) }
            val seasonDistance = seasonOneYears.minOfOrNull { abs(it - year) }
            if (seasonDistance != null && movieDistance != seasonDistance) {
                return if (movieDistance < seasonDistance) AnitubeReleaseKind.FILM to null
                else AnitubeReleaseKind.SEASON to 1
            }
        }
        return AnitubeReleaseKind.OTHER to null
    }

    private fun markerKind(title: String): MarkerKind {
        val value = title.lowercase()
        return when {
            Regex("\\b(ova|ona|special|спешл|спецвипуск|спецвипуск)\\b").containsMatchIn(value) -> MarkerKind.OTHER
            Regex("\\b(фільм|фильм|movie|мультфільм)\\b").containsMatchIn(value) -> MarkerKind.FILM
            else -> MarkerKind.NONE
        }
    }

    private fun isUnmarked(title: String) = explicitSeason(title) == null && markerKind(title) == MarkerKind.NONE

    private fun explicitSeason(title: String, synonyms: List<String> = emptyList()): Int? =
        (listOf(title) + synonyms).firstNotNullOfOrNull { name ->
            Regex("(?i)(?:\\bseason\\s*|\\bсезон\\s*|\\bs)(\\d{1,2})\\b|\\b(\\d{1,2})(?:st|nd|rd|th)\\s+season")
                .find(name)?.groupValues?.drop(1)?.firstNotNullOfOrNull(String::toIntOrNull)
        }

    private fun explicitSeason(title: Title, synonyms: List<String>): Int? =
        explicitSeason(listOfNotNull(title.english, title.romaji, title.native).firstOrNull().orEmpty(),
            synonyms + listOfNotNull(title.english, title.romaji, title.native))

    private fun stripSeasonSuffix(name: String) = name.replace(
        Regex("(?i)[\\s:,-]*(?:season\\s*\\d+|сезон\\s*\\d+|s\\d+|\\d+(?:st|nd|rd|th)\\s+season).*$"), ""
    ).trim()

    private fun AniListAnimeLookup.asDetails() = AnimeDetails(
        id = id, title = title, cover = null, banner = null, description = null,
        score = null, episodes = episodes, durationMinutes = durationMinutes,
        format = format, status = status, seasonYear = seasonYear, genres = emptyList(),
        studios = emptyList(), synonyms = synonyms,
    )

    private data class RankedCandidate(
        val aniListId: Int,
        val score: Double,
        val titleSimilarity: Double,
        val metadataMatches: Int,
        val conflictingMetadata: Boolean,
        val format: String?,
    )

    private enum class MarkerKind { NONE, FILM, OTHER }

    private companion object {
        const val MAX_SEARCH_NAMES = 16
        const val MAX_PARALLEL_SEARCHES = 4
        const val LOOKUP_BATCH_SIZE = 40
        const val MAX_RELATION_DEPTH = 8
        const val MAX_RELATION_RECORDS = 120
        val SERIES_FORMATS = setOf("TV", "TV_SHORT")
        val CHAIN_RELATIONS = setOf("PREQUEL", "SEQUEL")
        val releaseMetadataCache = ConcurrentHashMap<Int, AnitubeReleaseMetadata>()
    }
}
