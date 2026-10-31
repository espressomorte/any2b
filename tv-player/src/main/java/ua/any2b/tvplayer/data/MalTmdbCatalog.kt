package ua.any2b.tvplayer.data

import android.content.Context
import android.os.SystemClock
import ua.any2b.core.mal.MalClient
import ua.any2b.core.tmdb.FribbMappings
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.BuildConfig

data class MalTmdbPage(val titles: List<TmdbTitle>, val hasMore: Boolean)

/** MAL supplies ranking and card metadata; TMDB supplies the detail screen. */
class MalTmdbCatalog(private val context: Context) {
    private val client = MalClient()

    suspend fun page(page: Int, perPage: Int = 25): MalTmdbPage {
        val clientId = (MalClientIdStore(context).read() ?: BuildConfig.MAL_CLIENT_ID)
            .takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Налаштуйте MyAnimeList Client ID у розділі «Інтеграції»")
        val key = page to perPage
        synchronized(cache) {
            cache[key]?.takeIf { SystemClock.elapsedRealtime() - it.first < CACHE_AGE_MS }
                ?.second
        }?.let { return it }
        val ranking = client.topAnime(clientId, page, perPage)
        val mappings = FribbMappingRepository.get(context).byMal
        val titles = ranking.items.mapNotNull { anime ->
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
        if (ranking.items.isNotEmpty() && titles.isEmpty()) {
            throw IllegalStateException("Не вдалося знайти відповідники MAL у TMDB")
        }
        return MalTmdbPage(titles, ranking.hasMore).also { result ->
            synchronized(cache) { cache[key] = SystemClock.elapsedRealtime() to result }
        }
    }

    private companion object {
        const val CACHE_AGE_MS = 10 * 60 * 1000L
        val cache = mutableMapOf<Pair<Int, Int>, Pair<Long, MalTmdbPage>>()
    }
}
