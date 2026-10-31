package ua.any2b.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.MalTmdbCatalog
import ua.any2b.tvplayer.data.HomeSectionSetting
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.tmdbHomeRecommendations
import ua.any2b.tvplayer.data.workerTmdbConnection

sealed interface TmdbPopularState {
    data object Loading : TmdbPopularState
    data class Results(val titles: List<TmdbTitle>) : TmdbPopularState
    data class Error(val message: String) : TmdbPopularState
}

sealed interface MalTopState {
    data object Loading : MalTopState
    data class Results(val titles: List<TmdbTitle>, val hasMore: Boolean) : MalTopState
    data class Error(val message: String) : MalTopState
}

data class TmdbLibraryState(
    val history: List<TmdbTitle> = emptyList(),
    val favorites: List<TmdbTitle> = emptyList(),
    val recommendations: List<TmdbTitle> = emptyList(),
    val mainSections: List<HomeSectionSetting> = emptyList(),
    val tv: TmdbPopularState = TmdbPopularState.Loading,
    val movies: TmdbPopularState = TmdbPopularState.Loading,
    val malTop: MalTopState = MalTopState.Loading,
)

class TmdbLibraryViewModel(application: Application,
    private val animationOnly: Boolean = false) : AndroidViewModel(application) {
    private val malTmdbCatalog = MalTmdbCatalog(application)
    private val history = TmdbWatchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val settings = SettingsManager(application)
    private val _state = MutableStateFlow(TmdbLibraryState(
        history = history.all(), favorites = favorites.all(),
        mainSections = if (animationOnly) settings.mainHomeSections() else emptyList()))
    val state: StateFlow<TmdbLibraryState> = _state
    private var recommendationSources: List<Pair<TmdbMediaType, Int>>? = null
    private var recommendationsJob: Job? = null

    init {
        if (animationOnly) {
            val enabled = _state.value.mainSections.filter { it.enabled }.map { it.id }.toSet()
            if ("animation_tv" in enabled) refreshPopular(TmdbMediaType.TV)
            if ("animation_movie" in enabled) refreshPopular(TmdbMediaType.MOVIE)
            if ("mal_top" in enabled) refreshMalTop()
            refreshPersonalLists()
        } else {
            refreshPopular(TmdbMediaType.TV)
            refreshPopular(TmdbMediaType.MOVIE)
        }
    }

    fun refreshMalTop() {
        _state.value = _state.value.copy(malTop = MalTopState.Loading)
        viewModelScope.launch {
            try {
                val page = malTmdbCatalog.page(page = 1)
                _state.value = _state.value.copy(malTop =
                    MalTopState.Results(page.titles, page.hasMore))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(malTop = MalTopState.Error(
                    error.message ?: "Не вдалося завантажити рейтинг MAL"))
            }
        }
    }

    fun refreshPersonalLists() {
        val watched = history.all()
        val saved = favorites.all()
        val sections = if (animationOnly) settings.mainHomeSections() else emptyList()
        val previouslyEnabled = _state.value.mainSections.filter { it.enabled }.map { it.id }.toSet()
        val nowEnabled = sections.filter { it.enabled }.map { it.id }.toSet()
        _state.value = _state.value.copy(history = watched, favorites = saved,
            mainSections = sections)
        if (!animationOnly) return
        if ("animation_tv" in nowEnabled && "animation_tv" !in previouslyEnabled &&
            _state.value.tv == TmdbPopularState.Loading) refreshPopular(TmdbMediaType.TV)
        if ("animation_movie" in nowEnabled && "animation_movie" !in previouslyEnabled &&
            _state.value.movies == TmdbPopularState.Loading) refreshPopular(TmdbMediaType.MOVIE)
        if ("mal_top" in nowEnabled && "mal_top" !in previouslyEnabled &&
            _state.value.malTop == MalTopState.Loading) refreshMalTop()
        if (sections.none { it.id == "recommendations" && it.enabled }) {
            recommendationsJob?.cancel()
            recommendationSources = null
            _state.value = _state.value.copy(recommendations = emptyList())
            return
        }
        val sources = (watched + saved).distinctBy { it.type to it.id }
            .take(4).map { it.type to it.id }
        if (sources == recommendationSources) return
        recommendationSources = sources
        recommendationsJob?.cancel()
        _state.value = _state.value.copy(recommendations = emptyList())
        if (sources.isEmpty()) return
        recommendationsJob = viewModelScope.launch {
            try {
                val titles = tmdbHomeRecommendations(getApplication())
                if (recommendationSources == sources)
                    _state.value = _state.value.copy(recommendations = titles)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (recommendationSources == sources) {
                    recommendationSources = null
                    _state.value = _state.value.copy(recommendations = emptyList())
                }
            }
        }
    }

    fun refreshPopular(type: TmdbMediaType) {
        setRow(type, TmdbPopularState.Loading)
        viewModelScope.launch {
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: throw IllegalStateException("Налаштуйте URL і ключ Worker")
                val titles = connection.client.popular(type, connection.accessKey,
                    genreId = if (animationOnly) 16 else null)
                setRow(type, TmdbPopularState.Results(titles))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if (error is TmdbFailure.Http && error.status == 404)
                    "Проксі Worker ще не підтримує популярні тайтли"
                else error.message ?: "Не вдалося завантажити популярні тайтли"
                setRow(type, TmdbPopularState.Error(message))
            }
        }
    }

    private fun setRow(type: TmdbMediaType, row: TmdbPopularState) {
        _state.value = if (type == TmdbMediaType.TV) _state.value.copy(tv = row)
            else _state.value.copy(movies = row)
    }
}
