package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title

/** Moves legacy AniList favorites and playback progress into the shared TMDB stores. */
class AniListLocalDataMigrator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun migrate(): Boolean = migrationMutex.withLock {
        val favoriteStore = FavoritesManager(appContext)
        val historyStore = WatchHistoryManager(appContext)
        val legacyFavorites = favoriteStore.getFavorites()
        val legacyHistory = historyStore.getHistory()
        if (legacyFavorites.isEmpty() && legacyHistory.isEmpty()) return@withLock false

        val catalog = AniListTmdbCatalog(appContext)
        val favorites = TmdbFavoritesStore(appContext)
        val watchHistory = TmdbWatchHistoryStore(appContext)
        val favoriteIds = mutableSetOf<Int>()
        val historyIds = mutableSetOf<Int>()
        val favoriteTitles = mutableListOf<ua.any2b.core.tmdb.TmdbTitle>()

        legacyFavorites.forEach { card ->
            val title = try { catalog.resolveCards(listOf(card)).firstOrNull() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (title != null) {
                favoriteTitles += title
                favoriteIds += card.id
            }
        }
        favorites.addAll(favoriteTitles)

        legacyHistory.forEach { entry ->
            val card = AnimeCard(entry.animeId,
                Title(entry.romaji, entry.english, entry.native), entry.cover,
                entry.score, entry.episodes, entry.format, entry.status, entry.seasonYear, null)
            val title = try { catalog.resolveCards(listOf(card)).firstOrNull() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (title != null) {
                watchHistory.saveEpisodeProgress(title, entry.voiceIndex, entry.episodeIndex,
                    entry.episodeNumber, entry.progress)
                historyIds += entry.animeId
            }
        }

        favoriteStore.remove(favoriteIds)
        historyStore.removeEntries(historyIds)
        favoriteIds.isNotEmpty() || historyIds.isNotEmpty()
    }

    private companion object {
        val migrationMutex = Mutex()
    }
}
