package ua.any2b.tvplayer.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ua.any2b.core.mal.MalAnime
import ua.any2b.core.mal.MalClient
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.BuildConfig

data class MalTmdbPage(val titles: List<TmdbTitle>, val hasMore: Boolean)

/** MAL supplies ranking and card metadata; TMDB supplies the detail screen. */
class MalTmdbCatalog(private val context: Context) {
    private val client = MalClient()

    suspend fun page(page: Int, perPage: Int = 25,
        rankingType: String = "all"): MalTmdbPage {
        val clientId = clientId()
        val key = Triple(page, perPage, rankingType)
        synchronized(cache) {
            cache[key]?.takeIf { SystemClock.elapsedRealtime() - it.first < CACHE_AGE_MS }
                ?.second
        }?.let { return it }
        val ranking = client.topAnime(clientId, page, perPage, rankingType)
        val titles = mapTitles(ranking.items)
        if (ranking.items.isNotEmpty() && titles.isEmpty()) {
            throw IllegalStateException("Не вдалося знайти відповідники MAL у TMDB")
        }
        return MalTmdbPage(titles, ranking.hasMore).also { result ->
            synchronized(cache) { cache[key] = SystemClock.elapsedRealtime() to result }
        }
    }

    suspend fun filteredPage(page: Int, yearRange: IntRange?, ascending: Boolean,
        sortByYear: Boolean,
        rankingType: String = "all", perPage: Int = 25): MalTmdbPage {
        require(page > 0)
        val clientId = clientId()
        val cacheKey = rankingType
        val ranked = synchronized(yearCache) {
            yearCache[cacheKey]?.takeIf { SystemClock.elapsedRealtime() - it.first < CACHE_AGE_MS }
                ?.second
        } ?: coroutineScope {
            (1..5).map { index ->
                async { client.topAnime(clientId, index, 100, rankingType).items }
            }.awaitAll().flatten().also { items ->
                synchronized(yearCache) { yearCache[cacheKey] = SystemClock.elapsedRealtime() to items }
            }
        }
        val now = java.util.Calendar.getInstance()
        val currentYear = now.get(java.util.Calendar.YEAR)
        val today = "%04d-%02d-%02d".format(java.util.Locale.ROOT, currentYear,
            now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH))
        val filtered = ranked.filter { anime ->
            val year = anime.year ?: return@filter false
            val startDate = anime.startDate
            year in (yearRange ?: 1920..currentYear) && year <= currentYear &&
                (year != currentYear || startDate == null ||
                    startDate.length < 10 || startDate <= today)
        }.let { items ->
            if (!sortByYear) items else items.sortedWith(if (ascending)
                compareBy<MalAnime> { it.startDate ?: it.year.toString() }
            else compareByDescending { it.startDate ?: it.year.toString() })
        }
        val all = mapTitles(filtered)
        val start = (page - 1) * perPage
        return MalTmdbPage(all.drop(start).take(perPage), start + perPage < all.size)
    }

    private fun clientId(): String =
        (MalClientIdStore(context).read() ?: BuildConfig.MAL_CLIENT_ID)
            .takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Налаштуйте MyAnimeList Client ID у розділі «Інтеграції»")

    private suspend fun mapTitles(items: List<MalAnime>): List<TmdbTitle> {
        val mappings = FribbMappingRepository.get(context).byMal
        return items.mapNotNull { anime ->
            val mapping = FribbMappings.preferred(mappings[anime.id].orEmpty(), anime.mediaType)
                ?: return@mapNotNull null
            TmdbTitle(
                id = mapping.id, type = mapping.type,
                title = anime.englishTitle ?: anime.title,
                originalTitle = anime.title,
                year = anime.year,
                posterUrl = anime.posterUrl,
                overview = null,
                rating = anime.score,
                englishTitle = anime.englishTitle,
            )
        }.distinctBy { it.type to it.id }
    }

    private companion object {
        const val CACHE_AGE_MS = 10 * 60 * 1000L
        val cache = mutableMapOf<Triple<Int, Int, String>, Pair<Long, MalTmdbPage>>()
        val yearCache = mutableMapOf<String, Pair<Long, List<MalAnime>>>()
    }
}
