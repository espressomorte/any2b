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
import ua.any2b.tvplayer.data.MalTmdbCatalog
import ua.any2b.tvplayer.data.tmdbHomeRecommendations
import ua.any2b.tvplayer.data.workerTmdbConnection

data class TmdbListState(
    val category: String = "favorites",
    val sort: TmdbListSort = TmdbListSort.POPULARITY,
    val yearAscending: Boolean = false,
    val yearRange: IntRange? = null,
    val titles: List<TmdbTitle> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val error: String? = null,
)

class TmdbListViewModel(application: Application) : AndroidViewModel(application) {
    private val malTmdbCatalog = MalTmdbCatalog(application)
    private val history = TmdbWatchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val _state = MutableStateFlow(TmdbListState())
    val state: StateFlow<TmdbListState> = _state
    private var loadJob: Job? = null

    fun open(category: String) {
        val normalized = category.takeIf {
            it == "tv" || it == "movie" || it == "history" ||
                it == "animation_tv" || it == "animation_movie" ||
                it == "mal_top" || it == "recommendations"
        } ?: "favorites"
        if (_state.value.category == normalized && (_state.value.page > 0 || _state.value.loading)) return
        loadJob?.cancel()
        _state.value = TmdbListState(category = normalized,
            sort = if (normalized == "mal_top") TmdbListSort.RATING else TmdbListSort.POPULARITY)
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
        if (_state.value.category in SPECIAL_LISTS) return
        val current = _state.value
        if (current.sort == sort && sort != TmdbListSort.YEAR) return
        loadJob?.cancel()
        _state.value = current.copy(sort = sort,
            yearAscending = sort == TmdbListSort.YEAR && current.sort == sort &&
                !current.yearAscending,
            titles = emptyList(), page = 0,
            hasMore = false, loading = false, error = null)
        loadMore()
    }

    fun changeYearRange(range: IntRange?) {
        if (_state.value.category in SPECIAL_LISTS) return
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (range != null && (range.first !in 1920..currentYear ||
                range.last !in range.first..currentYear)) return
        loadJob?.cancel()
        _state.value = _state.value.copy(sort = TmdbListSort.YEAR,
            yearAscending = false, yearRange = range, titles = emptyList(), page = 0,
            hasMore = false, loading = false, error = null)
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.category == "favorites" || current.category == "history" ||
            current.loading || current.page > 0 && !current.hasMore) return
        val type = if (current.category == "tv" || current.category == "animation_tv")
            TmdbMediaType.TV else TmdbMediaType.MOVIE
        val nextPage = current.page + 1
        _state.value = current.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                if (current.category == "mal_top") {
                    val rankingType = if (current.sort == TmdbListSort.POPULARITY)
                        "bypopularity" else "all"
                    val result = if (current.sort == TmdbListSort.YEAR || current.yearRange != null)
                        malTmdbCatalog.filteredPage(nextPage, current.yearRange,
                            current.yearAscending, current.sort == TmdbListSort.YEAR, rankingType)
                    else malTmdbCatalog.page(nextPage, rankingType = rankingType)
                    val latest = _state.value
                    val titles = (latest.titles + result.titles).distinctBy { it.type to it.id }
                    _state.value = latest.copy(titles = titles, page = nextPage,
                        hasMore = result.hasMore && titles.size > latest.titles.size,
                        loading = false)
                    return@launch
                }
                if (current.category == "recommendations") {
                    val titles = tmdbHomeRecommendations(getApplication())
                    _state.value = _state.value.copy(titles = titles, page = 1,
                        hasMore = false, loading = false)
                    return@launch
                }
                val connection = workerTmdbConnection(getApplication())
                    ?: throw IllegalStateException("Налаштуйте URL і ключ Worker")
                val result = connection.client.listPage(type, connection.accessKey, current.sort,
                    nextPage, genreId = if (current.category.startsWith("animation_")) 16 else null,
                    yearRange = current.yearRange, yearAscending = current.yearAscending)
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

    private companion object {
        val SPECIAL_LISTS = setOf("favorites", "history", "recommendations", "upcoming")
    }
}
