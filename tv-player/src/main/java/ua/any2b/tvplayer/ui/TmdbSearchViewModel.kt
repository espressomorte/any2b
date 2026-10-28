package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbClient
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.BuildConfig
import ua.any2b.tvplayer.data.TmdbSearchHistoryStore

data class TmdbSearchState(
    val query: String = "",
    val results: List<TmdbTitle> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val recentTitles: List<String> = emptyList(),
    val showRecentTitles: Boolean = true,
)

class TmdbSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val client = TmdbClient()
    private val history = TmdbSearchHistoryStore(application)
    private val _state = MutableStateFlow(TmdbSearchState(recentTitles = history.recentTitles()))
    val state: StateFlow<TmdbSearchState> = _state
    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        _state.value = _state.value.copy(query = query, loading = query.isNotBlank(),
            results = emptyList(), searched = false, error = null, showRecentTitles = true)
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(5000) // Same delayed search as the AniList search screen.
                executeSearch(query.trim())
            }
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        _state.value = _state.value.copy(loading = true, error = null, showRecentTitles = false)
        searchJob = viewModelScope.launch { executeSearch(query) }
    }

    fun selectRecentTitle(title: String) {
        searchJob?.cancel()
        _state.value = _state.value.copy(query = title, loading = false,
            results = emptyList(), searched = false, error = null, showRecentTitles = true)
        searchNow()
    }

    fun refreshRecentTitles() {
        _state.value = _state.value.copy(
            recentTitles = history.recentTitles(),
            showRecentTitles = true,
        )
    }

    fun clearHistory() {
        searchJob?.cancel()
        history.clear()
        _state.value = _state.value.copy(query = "", results = emptyList(), loading = false,
            searched = false, error = null, recentTitles = emptyList(), showRecentTitles = true)
    }

    private suspend fun executeSearch(query: String) {
        _state.value = _state.value.copy(showRecentTitles = false)
        try {
            val hits = client.search(query, BuildConfig.TMDB_TOKEN)
            _state.value = _state.value.copy(results = hits, loading = false, searched = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: TmdbFailure) {
            _state.value = _state.value.copy(results = emptyList(), loading = false,
                searched = true, error = error.message)
        } catch (_: Exception) {
            _state.value = _state.value.copy(results = emptyList(), loading = false,
                searched = true, error = "Не вдалося виконати пошук TMDB. Повторіть спробу")
        }
    }
}
