package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.AniListRomajiSelector

/** Reads AniList's canonical romaji for TMDB titles known to be anime. */
class AniListRomajiResolver(private val context: Context,
    private val aniList: AniListClient = AniListClient()) {
    suspend fun resolve(details: TmdbDetails): String? {
        if (16 !in details.title.genreIds) return null
        return try {
            withTimeoutOrNull(12000) {
                val bridge = try {
                    withTimeoutOrNull(3000) { AniBridgeMappingRepository.get(context) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                val bridgeIds = bridge?.anilistFor(details.title.type, details.title.id,
                    details.title.matchedSeasonNumber)
                    ?.map { it.aniListId }
                    .orEmpty().distinct()
                suspend fun selected(ids: List<Int>): String? {
                    val candidates = ids.distinct().take(12).map { id ->
                        async {
                            try { aniList.animeDetails(id) }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { null }
                        }
                    }.awaitAll().filterNotNull()
                    return AniListRomajiSelector.select(details, candidates)
                }
                selected(bridgeIds) ?: selected(FribbMappingRepository.get(context)
                    .anilistFor(details.title.type, details.title.id)
                    .filter { details.title.matchedSeasonNumber == null ||
                        it.tmdbSeason == details.title.matchedSeasonNumber }
                    .map { it.aniListId })
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
