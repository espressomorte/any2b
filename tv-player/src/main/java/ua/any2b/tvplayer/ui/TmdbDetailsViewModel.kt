package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbSearchHistoryStore
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.workerTmdbConnection

sealed interface TmdbDetailsState {
    data object Loading : TmdbDetailsState
    data class Ready(
        val details: TmdbDetails,
        val isFavorite: Boolean,
        val related: TmdbRelatedState = TmdbRelatedState.Loading,
    ) : TmdbDetailsState
    data class Error(val message: String) : TmdbDetailsState
}

sealed interface TmdbRelatedState {
    data object Loading : TmdbRelatedState
    data class Results(val titles: List<TmdbTitle>) : TmdbRelatedState
    data class Error(val message: String) : TmdbRelatedState
}

class TmdbDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val history = TmdbSearchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val _state = MutableStateFlow<TmdbDetailsState>(TmdbDetailsState.Loading)
    val state: StateFlow<TmdbDetailsState> = _state
    private var loadedKey: Pair<TmdbMediaType, Int>? = null
    private var loadJob: Job? = null
    private var relatedJob: Job? = null
    private var englishTitleJob: Job? = null

    fun toggleFavorite() {
        val ready = _state.value as? TmdbDetailsState.Ready ?: return
        val selected = favorites.toggle(ready.details.title)
        _state.value = ready.copy(isFavorite = selected)
    }

    fun retryRelated() {
        val ready = _state.value as? TmdbDetailsState.Ready ?: return
        relatedJob?.cancel()
        _state.value = ready.copy(related = TmdbRelatedState.Loading)
        relatedJob = viewModelScope.launch { loadRelated(ready.details.title) }
    }

    private suspend fun loadRelated(title: TmdbTitle) {
        try {
            val connection = workerTmdbConnection(getApplication())
                ?: throw TmdbFailure.MissingToken()
            val titles = connection.client.related(title.type, title.id, connection.accessKey)
            val ready = _state.value as? TmdbDetailsState.Ready ?: return
            if (ready.details.title.id == title.id && ready.details.title.type == title.type)
                _state.value = ready.copy(related = TmdbRelatedState.Results(titles))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val ready = _state.value as? TmdbDetailsState.Ready ?: return
            if (ready.details.title.id == title.id && ready.details.title.type == title.type)
                _state.value = ready.copy(related = TmdbRelatedState.Error(
                    if (error is TmdbFailure.Http && error.status == 404)
                        "Проксі Worker ще не підтримує пов’язані тайтли"
                    else error.message ?: "Не вдалося завантажити пов’язані тайтли"))
        }
    }

    fun load(
        type: TmdbMediaType, id: Int, query: String, preferredTitle: String?,
        originalLanguage: String?, originalTitle: String?, force: Boolean = false,
    ) {
        val key = type to id
        if (!force && loadedKey == key) return
        loadedKey = key
        loadJob?.cancel()
        relatedJob?.cancel()
        englishTitleJob?.cancel()
        _state.value = TmdbDetailsState.Loading
        loadJob = viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: throw TmdbFailure.MissingToken()
                val details = connection.client.details(type, id, connection.accessKey)
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
                        connection.client.details(type, id, connection.accessKey, preferredLocale).collectionName
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                } else null
                history.recordSelectedTitle(
                    localizedCollection ?: details.collectionName ?: preferredTitle ?: details.title.title)
                _state.value = TmdbDetailsState.Ready(details,
                    favorites.contains(type, id))
                relatedJob = viewModelScope.launch { loadRelated(details.title) }
                englishTitleJob = viewModelScope.launch {
                    try {
                        val english = connection.client.details(type, id, connection.accessKey, "en-US")
                        val ready = _state.value as? TmdbDetailsState.Ready ?: return@launch
                        if (ready.details.title.type == type && ready.details.title.id == id) {
                            _state.value = ready.copy(details = ready.details.copy(
                                title = ready.details.title.copy(englishTitle = english.title.title),
                                alternativeTitles = (ready.details.alternativeTitles +
                                    english.alternativeTitles).distinctBy { it.lowercase() },
                            ))
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The localized details remain usable without an English title.
                    }
                }
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
