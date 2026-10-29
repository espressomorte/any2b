package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.workerTmdbConnection

sealed interface TmdbPopularState {
    data object Loading : TmdbPopularState
    data class Results(val titles: List<TmdbTitle>) : TmdbPopularState
    data class Error(val message: String) : TmdbPopularState
}

data class TmdbLibraryState(
    val history: List<TmdbTitle> = emptyList(),
    val favorites: List<TmdbTitle> = emptyList(),
    val tv: TmdbPopularState = TmdbPopularState.Loading,
    val movies: TmdbPopularState = TmdbPopularState.Loading,
)

class TmdbLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val history = TmdbWatchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val _state = MutableStateFlow(TmdbLibraryState(
        history = history.all(), favorites = favorites.all()))
    val state: StateFlow<TmdbLibraryState> = _state

    init {
        refreshPopular(TmdbMediaType.TV)
        refreshPopular(TmdbMediaType.MOVIE)
    }

    fun refreshPersonalLists() {
        _state.value = _state.value.copy(history = history.all(), favorites = favorites.all())
    }

    fun refreshPopular(type: TmdbMediaType) {
        setRow(type, TmdbPopularState.Loading)
        viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: throw IllegalStateException("Налаштуйте URL і ключ Worker")
                val titles = connection.client.popular(type, connection.accessKey)
                setRow(type, TmdbPopularState.Results(titles))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if (error is TmdbFailure.Http && error.status == 404)
                    "Проксі Worker ще не підтримує популярні тайтли"
                else error.message ?: "Не вдалося завантажити популярні тайтли"
                setRow(type, TmdbPopularState.Error(message))
            }
        }
    }

    private fun setRow(type: TmdbMediaType, row: TmdbPopularState) {
        _state.value = if (type == TmdbMediaType.TV) _state.value.copy(tv = row)
            else _state.value.copy(movies = row)
    }
}
