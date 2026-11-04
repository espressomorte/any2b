package ua.any2b.tvplayer.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
    if (seeds.isEmpty()) {
        Log.i(TAG, "Recommendations skipped reason=no_seeds")
        return emptyList()
    }
    val connection = workerTmdbConnection(context) ?: run {
        Log.w(TAG, "Recommendations skipped reason=tmdb_connection_unavailable")
        return emptyList()
    }
    val seedKeys = seeds.map { it.type to it.id }.toSet()
    return coroutineScope {
        seeds.map { seed ->
            async {
                val startedAt = SystemClock.elapsedRealtime()
                Log.d(TAG, "Recommendation request started type=${seed.type.path} id=${seed.id}")
                try {
                    connection.client.related(seed.type, seed.id, connection.accessKey).also { titles ->
                        Log.d(TAG, "Recommendation request succeeded type=${seed.type.path} " +
                            "id=${seed.id} count=${titles.size} " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "Recommendation request failed type=${seed.type.path} id=${seed.id} " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                        "errorType=${error.javaClass.name} message=${error.message}", error)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }.filter { 16 in it.genreIds && (it.type to it.id) !in seedKeys }
        .distinctBy { it.type to it.id }
        .take(40)
        .also { Log.i(TAG, "Recommendations resolved seedCount=${seeds.size} resultCount=${it.size}") }
}

private const val TAG = "HomeCatalog"
