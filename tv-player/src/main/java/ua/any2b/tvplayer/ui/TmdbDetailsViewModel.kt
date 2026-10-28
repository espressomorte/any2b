package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbClient
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.tvplayer.BuildConfig
import ua.any2b.tvplayer.data.TmdbSearchHistoryStore

sealed interface TmdbDetailsState {
    data object Loading : TmdbDetailsState
    data class Ready(val details: TmdbDetails) : TmdbDetailsState
    data class Error(val message: String) : TmdbDetailsState
}

class TmdbDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val client = TmdbClient()
    private val history = TmdbSearchHistoryStore(application)
    private val _state = MutableStateFlow<TmdbDetailsState>(TmdbDetailsState.Loading)
    val state: StateFlow<TmdbDetailsState> = _state
    private var loadedKey: Pair<TmdbMediaType, Int>? = null
    private var loadJob: Job? = null

    fun load(
        type: TmdbMediaType, id: Int, query: String, preferredTitle: String?,
        originalLanguage: String?, originalTitle: String?, force: Boolean = false,
    ) {
        val key = type to id
        if (!force && loadedKey == key) return
        loadedKey = key
        loadJob?.cancel()
        _state.value = TmdbDetailsState.Loading
        loadJob = viewModelScope.launch {
            try {
                val details = client.details(type, id, BuildConfig.TMDB_TOKEN)
                val preferredLocale = when {
                    query.any { it in '\u0400'..'\u04FF' } -> "uk-UA"
                    originalLanguage != null && originalLanguage != "en" &&
                        originalLanguage != "uk" &&
                        originalTitle?.contains(query.trim(), ignoreCase = true) == true ->
                        originalLanguage
                    else -> "en-US"
                }
                val localizedCollection = if (type == TmdbMediaType.MOVIE &&
                    details.collectionName != null && preferredLocale != "uk-UA") {
                    try {
                        client.details(type, id, BuildConfig.TMDB_TOKEN, preferredLocale).collectionName
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                } else null
                history.recordSelectedTitle(
                    localizedCollection ?: details.collectionName ?: preferredTitle ?: details.title.title)
                _state.value = TmdbDetailsState.Ready(details)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TmdbFailure) {
                _state.value = TmdbDetailsState.Error(error.message.orEmpty())
            } catch (_: Exception) {
                _state.value = TmdbDetailsState.Error("Не вдалося завантажити деталі TMDB. Повторіть спробу")
            }
        }
    }
}
