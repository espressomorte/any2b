package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import ua.any2b.core.anilist.AniListAnimeLookup
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeReleaseMetadata
import ua.any2b.core.anitube.AnitubeSearchResult
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
    val episodeMappings: List<EpisodeMapping> = emptyList(),
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
    private val mappings: AnimeMappingRepository = LocalAnimeMappingRepository.get(context),
) {
    private val appContext = context.applicationContext
    private val releaseMatcher = AnitubeReleaseMatcher()

    suspend fun savedRelease(newsId: Int): AnitubeSearchResult? =
        mappings.findByAniTube(newsId)?.takeIf { it.pageUrl.isNotBlank() }?.let { entry ->
            AnitubeSearchResult(newsId = entry.newsId,
                title = entry.titleUa.ifBlank { entry.hikkaTitleUa },
                pageUrl = entry.pageUrl)
        }

    suspend fun find(
        type: TmdbMediaType,
        tmdbId: Int,
        tmdbSeason: Int?,
        tmdbNames: List<String>,
        aniListId: Int? = null,
        onAniListAvailability: (AniListAvailability) -> Unit = {},
    ): AnitubeResolution {
        aniListId?.takeIf { it > 0 }?.let { id ->
            resolveFromAniListIndex(id, tmdbSeason)?.let { return it }
        }
        resolveFromLocalIndex(type, tmdbId, tmdbSeason)?.let { return it }
        val unscopedNewsIds = mappings.findUnscopedNewsIds(type, tmdbId)

        val bridge = try {
            withTimeoutOrNull(8000) { AniBridgeMappingRepository.get(appContext) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val bridgeMatches = bridge?.anilistFor(type, tmdbId, tmdbSeason).orEmpty()
        val mapped = if (bridgeMatches.isNotEmpty()) bridgeMatches.map {
            FribbAnimeEntry(it.aniListId, it.season)
        } else {
            FribbMappingRepository.get(appContext).anilistFor(type, tmdbId)
        }
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
        val searchNames = releaseMatcher.prioritizedSearchNames(preferredNames, allNames, tmdbNames)
        if (searchNames.isEmpty()) return AnitubeResolution(emptyList(), emptyList(), emptyList())

        val responses = searchAniTube(searchNames)
        val failures = responses.filter { it.isFailure }
        val releases = responses.mapNotNull { it.getOrNull() }.flatten()
            .distinctBy(AnitubeSearchResult::newsId)
            .filterNot { it.newsId in unscopedNewsIds }
        if (releases.isEmpty() && failures.size == responses.size) {
            return AnitubeResolution(emptyList(), emptyList(), emptyList(),
                failures.first().exceptionOrNull()?.message ?: "Не вдалося зв’язатися з AniTube")
        }

        val seasonEntries = universe.values.filter { it.format in SERIES_FORMATS }
        val orderedSeasons = releaseMatcher.orderSeasonChain(seasonEntries, universe)
        val mappedSeasonNumbers = selectedEntries.associate { it.aniListId to it.tmdbSeason }
        val seasonNumbers = releaseMatcher.assignSeasonNumbers(orderedSeasons, mappedSeasonNumbers)
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
            release.newsId to releaseMatcher.bestMatch(release, universe.values, tmdbNames)
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
            val explicitSeason = releaseMatcher.explicitSeason(release.title)
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

    /** Uses exact-season identities and their direct AniTube page URLs from the packaged index. */
    private suspend fun resolveFromLocalIndex(
        type: TmdbMediaType,
        tmdbId: Int,
        tmdbSeason: Int?,
    ): AnitubeResolution? {
        val entries = mappings.findByTmdb(type, tmdbId, tmdbSeason)
        if (entries.isEmpty()) return null
        val hitByRelease = entries.flatMap { entry ->
            entry.tmdbMappings.filter { it.type == type && it.tmdbId == tmdbId &&
                (tmdbSeason == null || it.season == tmdbSeason) }
                .map { mapping -> entry to mapping }
        }.distinctBy { (entry, mapping) -> Triple(entry.newsId, mapping.season, entry.malId) }
        val candidates = hitByRelease.mapNotNull { (entry, mapping) ->
            if (entry.pageUrl.isBlank()) return@mapNotNull null
            val release = AnitubeSearchResult(
                newsId = entry.newsId,
                title = entry.titleUa.ifBlank { entry.hikkaTitleUa },
                pageUrl = entry.pageUrl,
            )
            AnitubeCandidate(
                release = release,
                score = AUTO_SCORE + 3.0,
                aniListId = entry.aniListIds.firstOrNull() ?: 0,
                titleSimilarity = 1.0,
                metadataMatches = 3,
                conflictingMetadata = false,
                kind = if (type == TmdbMediaType.MOVIE) AnitubeReleaseKind.FILM
                    else AnitubeReleaseKind.SEASON,
                seasonNumber = mapping.season,
                format = if (type == TmdbMediaType.MOVIE) "MOVIE" else "TV",
                episodeMappings = mapping.episodeMappings,
            )
        }.distinctBy { Triple(it.release.newsId, it.seasonNumber, it.kind) }
        if (candidates.isEmpty()) return null
        val seasonGroups = candidates.filter { it.kind == AnitubeReleaseKind.SEASON }
            .groupBy { it.seasonNumber ?: 1 }.toSortedMap().map { (number, seasonCandidates) ->
                val title = hitByRelease.firstOrNull { it.second.season == number }
                    ?.first?.titleEnglish?.firstOrNull()
                    ?: hitByRelease.firstOrNull { it.second.season == number }
                        ?.first?.titleRomaji?.firstOrNull()
                    ?: "Сезон $number"
                AnitubeSeasonGroup(number, title,
                    hitByRelease.filter { it.second.season == number }
                        .flatMap { it.first.aniListIds }.distinct(),
                    seasonCandidates.sortedByDescending(AnitubeCandidate::score))
            }
        return AnitubeResolution(
            seasons = seasonGroups,
            films = candidates.filter { it.kind == AnitubeReleaseKind.FILM },
            other = emptyList(),
            aniListAvailability = AniListAvailability.AVAILABLE,
            preferredSeason = tmdbSeason,
            preferFilm = type == TmdbMediaType.MOVIE,
        )
    }

    /** An AniList-origin card carries the season identity even when AniBridge has no TMDB target. */
    private suspend fun resolveFromAniListIndex(
        aniListId: Int,
        requestedSeason: Int?,
    ): AnitubeResolution? {
        val entries = mappings.findByAniList(aniListId)
            .filter { it.pageUrl.isNotBlank() }
        if (entries.isEmpty()) return null
        val candidates = entries.map { entry ->
            val mapping = entry.tmdbMappings.firstOrNull()
            AnitubeCandidate(
                release = AnitubeSearchResult(entry.newsId,
                    entry.titleUa.ifBlank { entry.hikkaTitleUa }, entry.pageUrl),
                score = AUTO_SCORE + 3.0,
                aniListId = aniListId,
                titleSimilarity = 1.0,
                metadataMatches = 3,
                conflictingMetadata = false,
                kind = if (mapping?.type == TmdbMediaType.MOVIE) AnitubeReleaseKind.FILM
                    else AnitubeReleaseKind.SEASON,
                seasonNumber = entry.seasonScope ?: mapping?.season ?: requestedSeason ?: 1,
                format = if (mapping?.type == TmdbMediaType.MOVIE) "MOVIE" else "TV",
                episodeMappings = mapping?.episodeMappings.orEmpty(),
            )
        }.distinctBy { Triple(it.release.newsId, it.seasonNumber, it.kind) }
        val seasons = candidates.filter { it.kind == AnitubeReleaseKind.SEASON }
            .groupBy { it.seasonNumber ?: 1 }.toSortedMap().map { (number, items) ->
                val title = entries.firstOrNull { it.seasonScope == number }
                    ?.titleEnglish?.firstOrNull()
                    ?: entries.firstOrNull { it.seasonScope == number }
                        ?.titleRomaji?.firstOrNull()
                    ?: "Сезон $number"
                AnitubeSeasonGroup(number, title, listOf(aniListId),
                    items.sortedByDescending(AnitubeCandidate::score))
            }
        val identitySeason = entries.mapNotNull(AniTubeEntry::seasonScope).distinct()
            .singleOrNull()
        return AnitubeResolution(
            seasons = seasons,
            films = candidates.filter { it.kind == AnitubeReleaseKind.FILM },
            other = emptyList(),
            aniListAvailability = AniListAvailability.AVAILABLE,
            // An AniList ID identifies a season-specific anime entry. Prefer that
            // franchise season over TMDB's local season index when they disagree.
            preferredSeason = identitySeason ?: requestedSeason,
            preferFilm = candidates.any { it.kind == AnitubeReleaseKind.FILM },
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
                    anime.relations.filter { it.type in ANITUBE_CHAIN_RELATIONS }
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

    private fun isUnmarked(title: String) = releaseMatcher.explicitSeason(title) == null &&
        markerKind(title) == MarkerKind.NONE

    private enum class MarkerKind { NONE, FILM, OTHER }

    private companion object {
        const val MAX_PARALLEL_SEARCHES = 4
        const val LOOKUP_BATCH_SIZE = 40
        const val MAX_RELATION_DEPTH = 8
        const val MAX_RELATION_RECORDS = 120
        val SERIES_FORMATS = setOf("TV", "TV_SHORT")
        val releaseMetadataCache = ConcurrentHashMap<Int, AnitubeReleaseMetadata>()
    }
}
