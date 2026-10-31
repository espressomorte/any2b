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
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title
import ua.any2b.core.anilist.groupNumberedTvSeasons
import ua.any2b.core.tmdb.TmdbListSort
import ua.any2b.tvplayer.data.FavoritesManager
import ua.any2b.tvplayer.data.WatchHistoryManager

data class CategoryState(
    val items: List<AnimeCard> = emptyList(),
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
    private val favoritesManager = FavoritesManager(application)
    private val historyManager = WatchHistoryManager(application)
    private var page = 0
    private var localItems: List<AnimeCard> = emptyList()
    private var loadedRawItems: List<AnimeCard> = emptyList()
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(CategoryState(
        sort = if (categoryId == "top_score") TmdbListSort.RATING else TmdbListSort.POPULARITY,
        sortable = categoryId !in setOf("favorites", "history", "upcoming") &&
            !categoryId.startsWith("related:") &&
            !categoryId.startsWith("recommendations:")))
    val state: StateFlow<CategoryState> = _state

    init {
        if (isLocal) localItems = readLocalItems()
        loadMore()
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
                val nextItems: List<AnimeCard>
                val hasMore: Boolean
                val relatedId = relatedAnimeId
                val recommendationsId = recommendationsAnimeId
                when {
                    isLocal -> {
                        nextItems = localItems.drop(loadedRawItems.size).take(PAGE_SIZE)
                        hasMore = loadedRawItems.size + nextItems.size < localItems.size
                    }
                    relatedId != null -> {
                        nextItems = client.animeDetails(relatedId).related
                        hasMore = false
                    }
                    recommendationsId != null -> {
                        val result = client.recommendationsPage(recommendationsId, page + 1, PAGE_SIZE)
                        page++
                        nextItems = result.items
                        hasMore = result.hasNextPage
                    }
                    else -> {
                        nextItems = client.categoryPage(categoryId, page + 1, PAGE_SIZE,
                            current.sort, current.yearRange, current.yearAscending)
                        page++
                        hasMore = nextItems.size == PAGE_SIZE
                    }
                }
                loadedRawItems = (loadedRawItems + nextItems).distinctBy { it.id }
                val allItems = groupNumberedTvSeasons(loadedRawItems)
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
        localItems = readLocalItems()
        val visibleCount = maxOf(PAGE_SIZE, loadedRawItems.size)
        val visible = localItems.take(visibleCount)
        loadedRawItems = visible
        _state.value = _state.value.copy(items = groupNumberedTvSeasons(visible),
            hasMore = visible.size < localItems.size)
    }

    private fun readLocalItems(): List<AnimeCard> = when (categoryId) {
        "favorites" -> favoritesManager.getFavorites()
        "history" -> historyManager.getHistory().map { entry ->
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
        else -> emptyList()
    }

    companion object { const val PAGE_SIZE = 20 }
}
