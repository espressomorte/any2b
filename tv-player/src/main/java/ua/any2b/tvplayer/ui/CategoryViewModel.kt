package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.Title
import ua.any2b.tvplayer.data.FavoritesManager
import ua.any2b.tvplayer.data.WatchHistoryManager

data class CategoryState(
    val items: List<AnimeCard> = emptyList(),
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

    private val _state = MutableStateFlow(CategoryState())
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

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.hasMore) return
        _state.value = current.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val nextItems: List<AnimeCard>
                val hasMore: Boolean
                val relatedId = relatedAnimeId
                val recommendationsId = recommendationsAnimeId
                when {
                    isLocal -> {
                        nextItems = localItems.drop(current.items.size).take(PAGE_SIZE)
                        hasMore = current.items.size + nextItems.size < localItems.size
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
                        nextItems = client.categoryPage(categoryId, page + 1, PAGE_SIZE)
                        page++
                        hasMore = nextItems.size == PAGE_SIZE
                    }
                }
                val allItems = (current.items + nextItems).distinctBy { it.id }
                _state.value = CategoryState(
                    items = allItems,
                    hasMore = hasMore,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current.copy(error = e.message ?: "Не вдалося завантажити тайтли")
            }
        }
    }

    fun refreshLocal() {
        if (!isLocal) return
        localItems = readLocalItems()
        val visibleCount = maxOf(PAGE_SIZE, _state.value.items.size)
        val visible = localItems.take(visibleCount)
        _state.value = CategoryState(items = visible, hasMore = visible.size < localItems.size)
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
