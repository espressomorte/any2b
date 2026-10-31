package ua.any2b.tvplayer.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.tmdb.AniListTmdbMatcher
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.TmdbSeason
import ua.any2b.core.tmdb.TmdbSeasonMatcher
import ua.any2b.core.tmdb.TmdbTitle

/** AniList supplies the ranking; Fribb resolves IDs for the ordinary TMDB detail screen. */
class AniListTmdbCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val aniList = AniListClient()
    private val mappings = TmdbMappingStore.getInstance(appContext)

    suspend fun page(page: Int, perPage: Int = 25): AniListTmdbPage = coroutineScope {
        val cacheKey = page to perPage
        synchronized(cache) {
            cache[cacheKey]?.takeIf { SystemClock.elapsedRealtime() - it.first < CACHE_AGE_MS }
                ?.second
        }?.let { return@coroutineScope it }
        val cards = aniList.categoryPage("top_score", page, perPage)
        val saved = try {
            mappings.all()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
        val index = try {
            FribbMappingRepository.get(appContext)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val titles = if (index != null) {
            cards.mapNotNull { card ->
                val mapping = saved[card.id]
                    ?.let { it.type to it.id }
                    ?: FribbMappings.preferred(index.byAniList[card.id].orEmpty(), card.format)
                        ?.let { it.type to it.id }
                mapping?.let { (type, id) -> card.tmdbTitle(type, id) }
            }
        } else fallbackSearch(cards, saved)
        val distinct = titles.distinctBy { it.type to it.id }
        if (cards.isNotEmpty() && distinct.isEmpty()) {
            throw IllegalStateException("Не вдалося знайти відповідники AniList у TMDB")
        }
        AniListTmdbPage(distinct, hasMore = cards.size == perPage).also { result ->
            synchronized(cache) { cache[cacheKey] = SystemClock.elapsedRealtime() to result }
        }
    }

    private suspend fun fallbackSearch(cards: List<AnimeCard>,
        saved: Map<Int, TmdbMapping>): List<TmdbTitle> = coroutineScope {
        val connection = workerTmdbConnection(appContext)
            ?: throw IllegalStateException("Налаштуйте URL і ключ Worker для TMDB")
        val permits = Semaphore(4)
        cards.map { card ->
            async {
                permits.withPermit {
                    try {
                        val mapping = saved[card.id]
                        if (mapping != null) {
                            return@withPermit card.tmdbTitle(mapping.type, mapping.id)
                        }
                        match(card,
                            search = { query -> connection.client.search(query, connection.accessKey) },
                            seasons = { candidate ->
                                connection.client.details(candidate.type, candidate.id,
                                    connection.accessKey).seasonEntries
                            })
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private companion object {
        const val CACHE_AGE_MS = 10 * 60 * 1000L
        val cache = mutableMapOf<Pair<Int, Int>, Pair<Long, AniListTmdbPage>>()
    }
}

data class AniListTmdbPage(val titles: List<TmdbTitle>, val hasMore: Boolean)

private fun AnimeCard.tmdbTitle(type: ua.any2b.core.tmdb.TmdbMediaType,
    tmdbId: Int): TmdbTitle = TmdbTitle(
    id = tmdbId, type = type, title = title.display,
    originalTitle = title.romaji ?: title.native,
    year = seasonYear, posterUrl = cover, overview = null,
    rating = score?.div(10.0), englishTitle = title.english,
)

internal suspend fun match(card: AnimeCard,
    search: suspend (String) -> List<TmdbTitle>,
    seasons: suspend (TmdbTitle) -> List<TmdbSeason> = { emptyList() }): TmdbTitle? {
    val anime = AnimeDetails(
        id = card.id, title = card.title, cover = card.cover, banner = null,
        description = null, score = card.score, episodes = card.episodes,
        durationMinutes = null, format = card.format, status = card.status,
        seasonYear = card.seasonYear, genres = emptyList(), studios = emptyList(),
    )
    val queries = listOfNotNull(card.title.english, card.title.romaji)
        .map(String::trim).filter(String::isNotBlank).distinct()
    for (query in queries) {
        var lookups = 0
        val searchResults = try {
            search(query)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            continue
        }
        val results = searchResults.map { candidate ->
            if (!TmdbSeasonMatcher.shouldInspect(anime, candidate) || lookups >= 2) candidate
            else {
                lookups++
                try {
                    TmdbSeasonMatcher.enrich(anime, candidate, seasons(candidate))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    candidate
                }
            }
        }
        val candidates = AniListTmdbMatcher.rank(anime, results)
        AniListTmdbMatcher.clearSuggestion(candidates)?.let { return it }
    }
    return null
}
