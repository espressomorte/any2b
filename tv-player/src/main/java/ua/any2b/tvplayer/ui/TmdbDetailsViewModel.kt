package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbGeneralCatalog
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbSearchHistoryStore
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistStore
import ua.any2b.tvplayer.data.AnitubeController
import ua.any2b.tvplayer.data.AniListAvailability
import ua.any2b.tvplayer.data.TmdbEpisodeProgress
import ua.any2b.core.anitube.AnitubeSearchResult
import ua.any2b.tvplayer.data.workerTmdbConnection
import ua.any2b.tvplayer.data.ExplicitRelatedCatalog

sealed interface TmdbDetailsState {
    data object Loading : TmdbDetailsState
    data class Ready(
        val details: TmdbDetails,
        val isFavorite: Boolean,
        val isWatchlisted: Boolean = false,
        val related: TmdbRelatedState = TmdbRelatedState.Loading,
        val activePanel: Panel = Panel.TORRENT,
    ) : TmdbDetailsState
    data class Error(val message: String) : TmdbDetailsState
}

sealed interface TmdbRelatedState {
    data object Loading : TmdbRelatedState
    data class Results(val titles: List<TmdbTitle>) : TmdbRelatedState
    data class Error(val message: String) : TmdbRelatedState
}

class TmdbDetailsViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val history = TmdbSearchHistoryStore(application)
    private val watchHistory = TmdbWatchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val watchlist = TmdbWatchlistStore(application)
    private val explicitRelated = ExplicitRelatedCatalog(application)
    private val _state = MutableStateFlow<TmdbDetailsState>(TmdbDetailsState.Loading)
    val state: StateFlow<TmdbDetailsState> = _state
    private val anitubeController = AnitubeController(application, viewModelScope)
    val anitubeState = anitubeController.state
    val aniListAvailability = anitubeController.aniListAvailability
    private var loadedKey: Pair<TmdbMediaType, Int>? = null
    private var activePanelStateKey: String? = null
    private var torrentsOnly = false
    private var loadJob: Job? = null
    private var relatedJob: Job? = null
    private var englishTitleJob: Job? = null

    init {
        viewModelScope.launch {
            anitubeController.aniListAvailability.collect { availability ->
                if (availability == AniListAvailability.MISSING) {
                    val ready = _state.value as? TmdbDetailsState.Ready ?: return@collect
                    if (ready.activePanel == Panel.ONLINE)
                        _state.value = ready.copy(activePanel = Panel.TORRENT)
                }
            }
        }
    }

    fun toggleFavorite() {
        val ready = _state.value as? TmdbDetailsState.Ready ?: return
        val selected = favorites.toggle(ready.details.title)
        _state.value = ready.copy(isFavorite = selected)
    }

    fun toggleWatchlist() {
        val ready = _state.value as? TmdbDetailsState.Ready ?: return
        if (ready.isWatchlisted) {
            watchlist.remove(ready.details.title)
            _state.value = ready.copy(isWatchlisted = false)
        } else {
            if (ready.details.title.type == TmdbMediaType.TV)
                watchlist.saveSeasons(ready.details.title.id, ready.details.seasonEntries)
            watchlist.saveLogo(ready.details.title.type, ready.details.title.id,
                ready.details.logoUrl)
            watchlist.addManual(ready.details.title)
            _state.value = ready.copy(isWatchlisted = true)
        }
    }

    fun retryRelated() {
        val ready = _state.value as? TmdbDetailsState.Ready ?: return
        relatedJob?.cancel()
        _state.value = ready.copy(related = TmdbRelatedState.Loading)
        relatedJob = viewModelScope.launch { loadRelated(ready.details) }
    }

    private suspend fun loadRelated(details: TmdbDetails) {
        val title = details.title
        val collectionId = details.collectionId
        try {
            val titles = when {
                title.type == TmdbMediaType.MOVIE && collectionId != null -> {
                    val connection = workerTmdbConnection(getApplication())
                        ?: throw TmdbFailure.MissingToken()
                    connection.client.collectionParts(collectionId, connection.accessKey)
                        .filterNot { it.id == title.id }
                }
                !torrentsOnly && 16 in title.genreIds ->
                    explicitRelated.animeRelations(title)
                else -> emptyList()
            }.let { if (torrentsOnly) it.filter(TmdbGeneralCatalog::accepts) else it }
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
        originalLanguage: String?, originalTitle: String?, torrentsOnly: Boolean = false,
        seasonNumber: Int? = null, force: Boolean = false,
    ) {
        val key = type to id
        if (!force && loadedKey == key) return
        loadedKey = key
        activePanelStateKey = "details_active_panel_${type.path}_$id"
        this.torrentsOnly = torrentsOnly
        loadJob?.cancel()
        relatedJob?.cancel()
        englishTitleJob?.cancel()
        _state.value = TmdbDetailsState.Loading
        if (torrentsOnly) anitubeController.cancel() else
            anitubeController.start(type, id, season = seasonNumber,
                names = listOfNotNull(preferredTitle, query, originalTitle),
                format = if (type == TmdbMediaType.MOVIE) "MOVIE" else "TV",
                resumeProgress = watchHistory.episodeProgress(type, id))
        loadJob = viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: throw TmdbFailure.MissingToken()
                val fetched = connection.client.details(type, id, connection.accessKey)
                if (type == TmdbMediaType.TV) watchlist.saveSeasons(id, fetched.seasonEntries)
                val selectedSeason = fetched.seasonEntries.firstOrNull {
                    it.number == seasonNumber
                }
                val details = if (selectedSeason == null) fetched else fetched.copy(
                    title = fetched.title.copy(matchedSeasonNumber = seasonNumber,
                        matchedSeasonTitle = selectedSeason.name,
                        matchedSeasonYear = selectedSeason.year))
                if (!torrentsOnly) anitubeController.addNames(listOfNotNull(details.title.ukrainianTitle,
                    details.title.title, details.title.englishTitle, details.title.originalTitle) +
                    details.alternativeTitles)
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
                val savedPanel = savedState.get<String>(activePanelStateKey!!)
                    ?.let { runCatching { Panel.valueOf(it) }.getOrNull() }
                    ?.takeIf { !torrentsOnly || it == Panel.TORRENT }
                    ?: Panel.TORRENT
                _state.value = TmdbDetailsState.Ready(details,
                    favorites.contains(type, id), watchlist.contains(type, id),
                    activePanel = savedPanel)
                relatedJob = viewModelScope.launch { loadRelated(details) }
                englishTitleJob = viewModelScope.launch {
                    try {
                        val english = connection.client.details(type, id, connection.accessKey, "en-US")
                        val ready = _state.value as? TmdbDetailsState.Ready ?: return@launch
                        if (ready.details.title.type == type && ready.details.title.id == id) {
                            val enriched = ready.details.copy(
                                title = ready.details.title.copy(englishTitle = english.title.title),
                                alternativeTitles = (ready.details.alternativeTitles +
                                    english.alternativeTitles).distinctBy { it.lowercase() },
                            )
                            _state.value = ready.copy(details = enriched)
                            if (!torrentsOnly) anitubeController.addNames(listOfNotNull(enriched.title.ukrainianTitle,
                                enriched.title.title, enriched.title.englishTitle,
                                enriched.title.originalTitle) + enriched.alternativeTitles)
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

    fun setPanel(panel: Panel) {
        if (torrentsOnly && panel == Panel.ONLINE) return
        val ready = _state.value as? TmdbDetailsState.Ready ?: return
        if (panel == Panel.ONLINE && anitubeController.aniListAvailability.value in
            setOf(AniListAvailability.CHECKING, AniListAvailability.MISSING)) return
        if (ready.activePanel != panel) {
            activePanelStateKey?.let { savedState[it] = panel.name }
            _state.value = ready.copy(activePanel = panel)
        }
    }

    fun retryAnitube() = anitubeController.retry()

    fun selectAnitubeRelease(release: AnitubeSearchResult) =
        anitubeController.selectRelease(release)

    fun selectAnitubeSeason(number: Int) = anitubeController.selectSeason(number)

    fun selectAnitubeVoice(index: Int) = anitubeController.selectVoice(index)

    fun selectAnitubeEpisode(index: Int) = anitubeController.selectEpisode(index)

    fun loadSavedAnitubeRelease(
        releaseId: Int,
        seasonNumber: Int?,
        names: List<String>,
        format: String,
    ) {
        val title = (_state.value as? TmdbDetailsState.Ready)?.details?.title ?: return
        anitubeController.loadSavedRelease(releaseId, title.type, title.id, seasonNumber,
            names, format, watchHistory.episodeProgress(title.type, title.id))
    }

    fun restoreAnitubeProgress(progress: TmdbEpisodeProgress?) =
        anitubeController.restoreProgress(progress)

    fun playAnitube(
        onError: (String) -> Unit,
        onSuccess: (String, Int, Int, Int, Float,
            List<ua.any2b.tvplayer.data.AnitubeStreamFallback>) -> Unit,
    ) = anitubeController.resolveSelectedStream(onError, onSuccess)

    override fun onCleared() {
        anitubeController.cancel()
        super.onCleared()
    }
}
