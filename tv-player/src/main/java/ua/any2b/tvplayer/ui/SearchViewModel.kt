package ua.any2b.tvplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anilist.AnimeCard
import ua.any2b.core.anilist.groupNumberedTvSeasons

class SearchViewModel(
    private val aniListClient: AniListClient = AniListClient()
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _searchResults = MutableStateFlow<List<AnimeCard>>(emptyList())
    val searchResults: StateFlow<List<AnimeCard>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        searchJob = viewModelScope.launch {
            delay(5000) // 5 seconds debounce as requested
            executeSearch(newQuery)
        }
    }

    fun searchNow(q: String = _query.value) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            executeSearch(q)
        }
    }

    private suspend fun executeSearch(q: String) {
        try {
            val results = withContext(Dispatchers.IO) { aniListClient.search(q) }
            _searchResults.value = groupNumberedTvSeasons(results)
        } catch (_: Exception) {
            _searchResults.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }
}
