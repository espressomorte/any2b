package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ua.any2b.core.tmdb.TmdbTitle

/** Animation recommendations based on the user's recent watches and favorites. */
suspend fun tmdbHomeRecommendations(context: Context): List<TmdbTitle> {
    val seeds = (TmdbWatchHistoryStore(context).all() + TmdbFavoritesStore(context).all())
        .distinctBy { it.type to it.id }
        .take(4)
    if (seeds.isEmpty()) return emptyList()
    val connection = workerTmdbConnection(context) ?: return emptyList()
    val seedKeys = seeds.map { it.type to it.id }.toSet()
    return coroutineScope {
        seeds.map { seed ->
            async {
                try {
                    connection.client.related(seed.type, seed.id, connection.accessKey)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }.filter { 16 in it.genreIds && (it.type to it.id) !in seedKeys }
        .distinctBy { it.type to it.id }
        .take(40)
}
