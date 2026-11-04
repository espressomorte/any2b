package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.tmdb.TmdbListSort
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.AniListTmdbCatalog
import ua.any2b.tvplayer.data.AniListLocalDataMigrator
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore

data class CategoryState(
    val items: List<TmdbTitle> = emptyList(),
    val sort: TmdbListSort = TmdbListSort.POPULARITY,
    val yearAscending: Boolean = false,
    val yearRange: IntRange? = null,
    val sortable: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class CategoryViewModel(
    application: Application,
    private val categoryId: String,
    private val client: AniListClient = AniListClient(),
) : AndroidViewModel(application) {
    private val localFavorites = TmdbFavoritesStore(application)
    private val localHistory = TmdbWatchHistoryStore(application)
    private val tmdbCatalog = AniListTmdbCatalog(application)
    private val localDataMigrator = AniListLocalDataMigrator(application)
    private var page = 0
    private var localItems: List<TmdbTitle> = emptyList()
    private var loadedRawItems: List<TmdbTitle> = emptyList()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(CategoryState(
        sort = if (categoryId == "top_score") TmdbListSort.RATING else TmdbListSort.POPULARITY,
        sortable = categoryId !in setOf("favorites", "history", "upcoming") &&
            !categoryId.startsWith("related:") &&
            !categoryId.startsWith("recommendations:")))
    val state: StateFlow<CategoryState> = _state

    init {
        if (isLocal) viewModelScope.launch {
            localDataMigrator.migrate()
            localItems = readLocalItems()
            loadMore()
        } else loadMore()
    }

    private val isLocal: Boolean get() = categoryId == "favorites" || categoryId == "history"
    private val relatedAnimeId: Int? get() = categoryId.removePrefix("related:")
        .takeIf { categoryId.startsWith("related:") }?.toIntOrNull()
    private val recommendationsAnimeId: Int? get() = categoryId.removePrefix("recommendations:")
        .takeIf { categoryId.startsWith("recommendations:") }?.toIntOrNull()

    fun changeSort(sort: TmdbListSort) {
        val current = _state.value
        if (!current.sortable || current.sort == sort && sort != TmdbListSort.YEAR) return
        reload(current.copy(sort = sort, yearAscending = sort == TmdbListSort.YEAR &&
            current.sort == sort && !current.yearAscending))
    }

    fun changeYearRange(range: IntRange?) {
        if (!_state.value.sortable) return
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (range != null && (range.first !in 1920..currentYear ||
                range.last !in range.first..currentYear)) return
        reload(_state.value.copy(sort = TmdbListSort.YEAR, yearAscending = false,
            yearRange = range))
    }

    private fun reload(state: CategoryState) {
        loadJob?.cancel()
        page = 0
        loadedRawItems = emptyList()
        _state.value = state.copy(items = emptyList(), loadingMore = false,
            hasMore = true, error = null)
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.hasMore) return
        _state.value = current.copy(loadingMore = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val nextItems: List<TmdbTitle>
                val hasMore: Boolean
                val relatedId = relatedAnimeId
                val recommendationsId = recommendationsAnimeId
                when {
                    isLocal -> {
                        nextItems = localItems.drop(loadedRawItems.size).take(PAGE_SIZE)
                        hasMore = loadedRawItems.size + nextItems.size < localItems.size
                    }
                    relatedId != null -> {
                        nextItems = tmdbCatalog.resolveCards(client.animeDetails(relatedId).related)
                        hasMore = false
                    }
                    recommendationsId != null -> {
                        val result = client.recommendationsPage(recommendationsId, page + 1, PAGE_SIZE)
                        page++
                        nextItems = tmdbCatalog.resolveCards(result.items)
                        hasMore = result.hasNextPage
                    }
                    else -> {
                        val cards = client.categoryPage(categoryId, page + 1, PAGE_SIZE,
                            current.sort, current.yearRange, current.yearAscending)
                        page++
                        nextItems = tmdbCatalog.resolveCards(cards)
                        hasMore = cards.size == PAGE_SIZE
                    }
                }
                loadedRawItems = (loadedRawItems + nextItems).distinctBy { it.type to it.id }
                val allItems = loadedRawItems
                _state.value = _state.value.copy(items = allItems,
                    loadingMore = false, hasMore = hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingMore = false,
                    error = e.message ?: "Не вдалося завантажити тайтли")
            }
        }
    }

    fun refreshLocal() {
        if (!isLocal) return
        viewModelScope.launch {
            localDataMigrator.migrate()
            localItems = readLocalItems()
            val visibleCount = maxOf(PAGE_SIZE, loadedRawItems.size)
            val visible = localItems.take(visibleCount)
            loadedRawItems = visible
            _state.value = _state.value.copy(items = visible,
                hasMore = visible.size < localItems.size)
        }
    }

    private fun readLocalItems(): List<TmdbTitle> = when (categoryId) {
        "favorites" -> localFavorites.all()
        "history" -> localHistory.all()
        else -> emptyList()
    }

    companion object { const val PAGE_SIZE = 20 }
}
