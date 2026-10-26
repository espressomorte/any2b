package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.HomeRow
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title
import ua.any2b.core.anilist.HOME_FETCH_SIZE
import ua.any2b.core.anilist.rowsWithLocal
import ua.any2b.tvplayer.data.FavoritesManager
import ua.any2b.tvplayer.data.WatchHistoryManager

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val rows: List<HomeRow>, val catalogError: String? = null) : HomeState
    data class Error(val message: String) : HomeState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val client = AniListClient()
    private val favoritesManager = FavoritesManager(application)
    private val historyManager = WatchHistoryManager(application)

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            _state.value = try {
                HomeState.Ready(rowsWithLocal(client.homeRows(perRow = HOME_FETCH_SIZE)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeState.Ready(rowsWithLocal(emptyList()), e.message ?: "Не вдалося завантажити каталог")
            }
        }
    }

    fun refreshLocalRows() {
        val current = _state.value as? HomeState.Ready ?: return
        val catalogRows = current.rows.filterNot { it.id == "favorites" || it.id == "history" }
        _state.value = current.copy(rows = rowsWithLocal(catalogRows))
    }

    private fun rowsWithLocal(catalogRows: List<HomeRow>): List<HomeRow> {
        val history = historyManager.getHistory().map { entry ->
            AnimeCard(
                id = entry.animeId,
                title = Title(entry.romaji, entry.english, entry.native),
                cover = entry.cover,
                score = entry.score,
                episodes = entry.episodes,
                format = entry.format,
                status = entry.status,
                seasonYear = entry.seasonYear,
                nextEpisode = null,
            )
        }
        return rowsWithLocal(catalogRows, history, favoritesManager.getFavorites())
    }
}
