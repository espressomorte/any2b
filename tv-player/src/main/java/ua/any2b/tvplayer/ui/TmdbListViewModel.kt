package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbListSort
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.workerTmdbConnection

data class TmdbListState(
    val category: String = "favorites",
    val sort: TmdbListSort = TmdbListSort.POPULARITY,
    val titles: List<TmdbTitle> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val error: String? = null,
)

class TmdbListViewModel(application: Application) : AndroidViewModel(application) {
    private val history = TmdbWatchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val _state = MutableStateFlow(TmdbListState())
    val state: StateFlow<TmdbListState> = _state
    private var loadJob: Job? = null

    fun open(category: String) {
        val normalized = category.takeIf {
            it == "tv" || it == "movie" || it == "history"
        } ?: "favorites"
        if (_state.value.category == normalized && (_state.value.page > 0 || _state.value.loading)) return
        loadJob?.cancel()
        _state.value = TmdbListState(category = normalized)
        if (normalized == "favorites" || normalized == "history") refreshPersonalList() else loadMore()
    }

    fun refreshPersonalList() {
        val titles = when (_state.value.category) {
            "favorites" -> favorites.all()
            "history" -> history.all()
            else -> return
        }
        _state.value = _state.value.copy(titles = titles, page = 1, hasMore = false)
    }

    fun changeSort(sort: TmdbListSort) {
        if (_state.value.category == "favorites" || _state.value.category == "history" ||
            _state.value.sort == sort) return
        loadJob?.cancel()
        _state.value = _state.value.copy(sort = sort, titles = emptyList(), page = 0,
            hasMore = false, loading = false, error = null)
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.category == "favorites" || current.category == "history" ||
            current.loading || current.page > 0 && !current.hasMore) return
        val type = if (current.category == "tv") TmdbMediaType.TV else TmdbMediaType.MOVIE
        val nextPage = current.page + 1
        _state.value = current.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: throw IllegalStateException("Налаштуйте URL і ключ Worker")
                val result = connection.client.listPage(type, connection.accessKey, current.sort, nextPage)
                val latest = _state.value
                _state.value = latest.copy(titles = (latest.titles + result.titles)
                    .distinctBy { it.type to it.id }, page = result.page,
                    hasMore = result.hasMore, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if (error is TmdbFailure.Http && error.status == 404)
                    "Проксі Worker ще не підтримує цей список або сортування"
                else error.message ?: "Не вдалося завантажити тайтли"
                _state.value = _state.value.copy(loading = false, error = message)
            }
        }
    }
}
