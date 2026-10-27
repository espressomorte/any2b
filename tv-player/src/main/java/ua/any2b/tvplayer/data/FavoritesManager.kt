package ua.any2b.tvplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.FavoritesCodec

class FavoritesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)

    companion object {
        private val sharedFavoriteIds = MutableStateFlow<Set<Int>>(emptySet())
    }

    val favoriteIds: StateFlow<Set<Int>> = sharedFavoriteIds

    init {
        sharedFavoriteIds.value = getFavorites().mapTo(mutableSetOf()) { it.id }
    }

    fun getFavorites(): List<AnimeCard> = FavoritesCodec.decode(prefs.getString("items", "[]"))

    fun isFavorite(animeId: Int): Boolean = animeId in favoriteIds.value

    /** Returns the new favorite state. */
    fun toggle(details: AnimeDetails): Boolean = toggle(AnimeCard(
        id = details.id,
        title = details.title,
        cover = details.cover,
        score = details.score,
        episodes = details.episodes,
        format = details.format,
        status = details.status,
        seasonYear = details.seasonYear,
        nextEpisode = null,
    ))

    fun toggle(card: AnimeCard): Boolean {
        val favorites = getFavorites().toMutableList()
        val existingIndex = favorites.indexOfFirst { it.id == card.id }
        val isFavorite = existingIndex < 0
        if (isFavorite) {
            favorites.add(0, card)
        } else {
            favorites.removeAt(existingIndex)
        }
        prefs.edit().putString("items", FavoritesCodec.encode(favorites)).apply()
        sharedFavoriteIds.value = favorites.mapTo(mutableSetOf()) { it.id }
        return isFavorite
    }
}
