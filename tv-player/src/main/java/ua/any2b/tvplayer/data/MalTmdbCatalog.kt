package ua.any2b.tvplayer.data

import android.content.Context
import android.util.Log
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
        }?.let {
            Log.d(TAG, "MAL catalog page cache hit page=$page type=$rankingType " +
                "mappedTitles=${it.titles.size}")
            return it
        }
        val ranking = try {
            client.topAnime(clientId, page, perPage, rankingType)
        } catch (error: Exception) {
            Log.e(TAG, "MAL ranking request failed page=$page type=$rankingType", error)
            throw error
        }
        Log.d(TAG, "MAL ranking page=$page size=${ranking.items.size} hasMore=${ranking.hasMore} type=$rankingType")
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
        val cachedRanking = synchronized(yearCache) {
            yearCache[cacheKey]?.takeIf { SystemClock.elapsedRealtime() - it.first < CACHE_AGE_MS }
                ?.second
        }
        Log.d(TAG, "Year filter ranking cache=${if (cachedRanking == null) "miss" else "hit"} type=$rankingType")
        val ranked = cachedRanking ?: coroutineScope {
            (1..5).map { index ->
                async {
                    val result = try {
                        client.topAnime(clientId, index, 100, rankingType)
                    } catch (error: Exception) {
                        Log.e(TAG, "MAL bulk ranking request failed page=$index type=$rankingType", error)
                        throw error
                    }
                    Log.d(TAG, "MAL bulk ranking page=$index size=${result.items.size} type=$rankingType")
                    result.items
                }
            }.awaitAll().flatten().also { items ->
                synchronized(yearCache) { yearCache[cacheKey] = SystemClock.elapsedRealtime() to items }
                Log.d(TAG, "MAL bulk ranking collected=${items.size} type=$rankingType")
            }
        }
        val now = java.util.Calendar.getInstance()
        val currentYear = now.get(java.util.Calendar.YEAR)
        val today = "%04d-%02d-%02d".format(java.util.Locale.ROOT, currentYear,
            now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH))
        val range = yearRange ?: 1920..currentYear
        val filtered = ranked.filter { anime ->
            val year = anime.year
            val startDate = anime.startDate
            val accepted = year != null && year in range && year <= currentYear &&
                (year != currentYear || startDate == null ||
                    startDate.length < 10 || startDate <= today)
            Log.d(TAG, "MAL year filter id=${anime.id} title=${anime.title} year=$year " +
                "date=${startDate ?: "—"} range=$range accepted=$accepted")
            accepted
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
        val index = try {
            FribbMappingRepository.get(context)
        } catch (error: Exception) {
            Log.e(TAG, "Fribb index load failed for MAL batch size=${items.size}", error)
            throw error
        }
        val mappings = index.byMal
        val mapped = items.mapNotNull { anime ->
            val candidates = mappings[anime.id].orEmpty()
            val preferredType = if (anime.mediaType.equals("movie", ignoreCase = true)) "movie" else "tv"
            val mapping = FribbMappings.preferred(candidates, anime.mediaType)
            Log.d(TAG, "MAL source id=${anime.id} title=${anime.title} mediaType=${anime.mediaType} " +
                "year=${anime.year}; Fribb candidates=${candidates.joinToString { "${it.type.path}:${it.id}" }}")
            if (mapping == null) {
                Log.w(TAG, "MAL mapping missing id=${anime.id} title=${anime.title} " +
                    "expectedType=$preferredType; no Fribb byMal entry")
                return@mapNotNull null
            }
            Log.d(TAG, "MAL mapping selected malId=${anime.id} -> ${mapping.type.path}:${mapping.id} " +
                "candidates=${candidates.size}")
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
        }
        val selectedByTmdb = mutableMapOf<Pair<String, Int>, TmdbTitle>()
        mapped.forEach { title ->
            val key = title.type.path to title.id
            val previous = selectedByTmdb[key]
            if (previous != null) {
                Log.w(TAG, "MAL mapping collapsed duplicate TMDB=${title.type.path}:${title.id}; " +
                    "kept '${previous.originalTitle}', dropped '${title.originalTitle}'")
            } else {
                selectedByTmdb[key] = title
            }
        }
        Log.d(TAG, "MAL mapping batch input=${items.size} mapped=${mapped.size} " +
            "unique=${selectedByTmdb.size}")
        return selectedByTmdb.values.toList()
    }

    private companion object {
        const val TAG = "MalTmdbMapping"
        const val CACHE_AGE_MS = 10 * 60 * 1000L
        val cache = mutableMapOf<Triple<Int, Int, String>, Pair<Long, MalTmdbPage>>()
        val yearCache = mutableMapOf<String, Pair<Long, List<MalAnime>>>()
    }
}
