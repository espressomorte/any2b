package ua.any2b.tvplayer.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
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
import ua.any2b.core.anilist.HOME_FETCH_SIZE
import ua.any2b.tvplayer.data.AniListTmdbCatalog
import ua.any2b.tvplayer.data.AniListLocalDataMigrator
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

sealed interface AniListHomeState {
    data object Loading : AniListHomeState
    data class Results(val titles: List<TmdbTitle>) : AniListHomeState
    data class Error(val message: String) : AniListHomeState
}

data class TmdbLibraryState(
    val history: List<TmdbTitle> = emptyList(),
    val favorites: List<TmdbTitle> = emptyList(),
    val recommendations: List<TmdbTitle> = emptyList(),
    val mainSections: List<HomeSectionSetting> = emptyList(),
    val tv: TmdbPopularState = TmdbPopularState.Loading,
    val movies: TmdbPopularState = TmdbPopularState.Loading,
    val malTop: MalTopState = MalTopState.Loading,
    val trending: AniListHomeState = AniListHomeState.Loading,
    val popularSeason: AniListHomeState = AniListHomeState.Loading,
    val initialHomeLoading: Boolean = false,
)

class TmdbLibraryViewModel(application: Application,
    private val animationOnly: Boolean = false) : AndroidViewModel(application) {
    private val malTmdbCatalog = MalTmdbCatalog(application)
    private val aniListCatalog = AniListTmdbCatalog(application)
    private val history = TmdbWatchHistoryStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val localDataMigrator = AniListLocalDataMigrator(application)
    private val settings = SettingsManager(application)
    private val _state = MutableStateFlow(TmdbLibraryState(
        history = history.all(), favorites = favorites.all(),
        mainSections = if (animationOnly) settings.mainHomeSections() else emptyList(),
        initialHomeLoading = animationOnly))
    val state: StateFlow<TmdbLibraryState> = _state
    private var recommendationSources: List<Pair<TmdbMediaType, Int>>? = null
    private var recommendationsJob: Job? = null
    private var aniListHomeJob: Job? = null
    private var legacyMigrationJob: Job? = null
    private val initialSectionsPending = mutableSetOf<String>().apply {
        if (animationOnly) {
            val enabled = _state.value.mainSections.filter { it.enabled }.map { it.id }.toSet()
            addAll(enabled.intersect(setOf("animation_tv", "animation_movie", "mal_top", "trending", "popular_season")))
            val hasRecommendationSources = (_state.value.history + _state.value.favorites)
                .distinctBy { it.type to it.id }.take(4).isNotEmpty()
            if ("recommendations" in enabled && hasRecommendationSources) add("recommendations")
        }
    }

    init {
        migrateLegacyAniListData()
        if (animationOnly) {
            val enabled = _state.value.mainSections.filter { it.enabled }.map { it.id }.toSet()
            Log.i(TAG, "Home load started animationOnly=true enabledSections=${enabled.sorted()}")
            if (initialSectionsPending.isEmpty()) finishInitialHomeLoad()
            if ("animation_tv" in enabled) refreshPopular(TmdbMediaType.TV)
            if ("animation_movie" in enabled) refreshPopular(TmdbMediaType.MOVIE)
            if ("mal_top" in enabled) refreshMalTop()
            if (enabled.any { it == "trending" || it == "popular_season" }) refreshAniListHome()
            refreshPersonalLists()
        } else {
            refreshPopular(TmdbMediaType.TV)
            refreshPopular(TmdbMediaType.MOVIE)
        }
    }

    fun refreshMalTop() {
        _state.value = _state.value.copy(malTop = MalTopState.Loading)
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Home section load started section=mal_top source=MAL+Fribb")
            try {
                val page = malTmdbCatalog.page(page = 1)
                _state.value = _state.value.copy(malTop =
                    MalTopState.Results(page.titles, page.hasMore))
                Log.i(TAG, "Home section load succeeded section=mal_top count=${page.titles.size} " +
                    "hasMore=${page.hasMore} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                completeInitialSection("mal_top")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Home section load failed section=mal_top " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "errorType=${error.javaClass.name} message=${error.message}", error)
                _state.value = _state.value.copy(malTop = MalTopState.Error(
                    error.message ?: "Не вдалося завантажити рейтинг MAL"))
                completeInitialSection("mal_top")
            }
        }
    }

    fun refreshAniListHome() {
        aniListHomeJob?.cancel()
        _state.value = _state.value.copy(trending = AniListHomeState.Loading,
            popularSeason = AniListHomeState.Loading)
        aniListHomeJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Home section load started section=trending,popular_season source=AniList")
            try {
                val rows = aniListCatalog.homeRows(perRow = HOME_FETCH_SIZE)
                _state.value = _state.value.copy(
                    trending = AniListHomeState.Results(rows["trending"].orEmpty()),
                    popularSeason = AniListHomeState.Results(rows["popular_season"].orEmpty()))
                Log.i(TAG, "Home section load succeeded source=AniList " +
                    "trendingCount=${rows["trending"]?.size ?: 0} " +
                    "popularSeasonCount=${rows["popular_season"]?.size ?: 0} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Home section load failed source=AniList " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "errorType=${error.javaClass.name} message=${error.message}", error)
                val message = error.message ?: "Не вдалося завантажити аніме AniList"
                _state.value = _state.value.copy(trending = AniListHomeState.Error(message),
                    popularSeason = AniListHomeState.Error(message))
            } finally {
                completeInitialSection("trending")
                completeInitialSection("popular_season")
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
        if (nowEnabled.any { it == "trending" || it == "popular_season" } &&
            (_state.value.trending == AniListHomeState.Loading || _state.value.popularSeason == AniListHomeState.Loading) &&
            nowEnabled.intersect(setOf("trending", "popular_season")) != previouslyEnabled.intersect(setOf("trending", "popular_season"))) refreshAniListHome()
        if (sections.none { it.id == "recommendations" && it.enabled }) {
            recommendationsJob?.cancel()
            recommendationSources = null
            _state.value = _state.value.copy(recommendations = emptyList())
            completeInitialSection("recommendations")
            return
        }
        val sources = (watched + saved).distinctBy { it.type to it.id }
            .take(4).map { it.type to it.id }
        if (sources == recommendationSources) return
        recommendationSources = sources
        recommendationsJob?.cancel()
        _state.value = _state.value.copy(recommendations = emptyList())
        if (sources.isEmpty()) {
            completeInitialSection("recommendations")
            return
        }
        recommendationsJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Home section load started section=recommendations source=TMDB " +
                "seedCount=${sources.size}")
            try {
                val titles = tmdbHomeRecommendations(getApplication())
                if (recommendationSources == sources) {
                    _state.value = _state.value.copy(recommendations = titles)
                    Log.i(TAG, "Home section load succeeded section=recommendations " +
                        "count=${titles.size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                    completeInitialSection("recommendations")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Home section load failed section=recommendations " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "errorType=${error.javaClass.name} message=${error.message}", error)
                if (recommendationSources == sources) {
                    recommendationSources = null
                    _state.value = _state.value.copy(recommendations = emptyList())
                    completeInitialSection("recommendations")
                }
            }
        }
    }

    private fun migrateLegacyAniListData() {
        if (legacyMigrationJob?.isActive == true) return
        legacyMigrationJob = viewModelScope.launch {
            if (localDataMigrator.migrate()) {
                Log.i(TAG, "Migrated legacy AniList favorites and playback history into TMDB lists")
                refreshPersonalLists()
            }
        }
    }

    fun refreshPopular(type: TmdbMediaType) {
        setRow(type, TmdbPopularState.Loading)
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val section = if (type == TmdbMediaType.TV) "animation_tv" else "animation_movie"
            Log.i(TAG, "Home section load started section=$section source=TMDB " +
                "query=popular genreId=${if (animationOnly) 16 else "none"}")
            try {
                val connection = workerTmdbConnection(getApplication())
                    ?: throw IllegalStateException("Налаштуйте інтеграцію TMDB у конфігу")
                val connectionMode = if (settings.isEncryptedConfigEnabled()) "direct_config" else "worker"
                Log.d(TAG, "TMDB connection ready section=$section mode=$connectionMode")
                val titles = connection.client.popular(type, connection.accessKey,
                    genreId = if (animationOnly) 16 else null)
                setRow(type, TmdbPopularState.Results(titles))
                Log.i(TAG, "Home section load succeeded section=$section count=${titles.size} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                completeInitialSection(
                    if (type == TmdbMediaType.TV) "animation_tv" else "animation_movie")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Home section load failed section=$section " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "errorType=${error.javaClass.name} message=${error.message}", error)
                val message = if (error is TmdbFailure.Http && error.status == 404)
                    "Проксі Worker ще не підтримує популярні тайтли"
                else error.message ?: "Не вдалося завантажити популярні тайтли"
                setRow(type, TmdbPopularState.Error(message))
                completeInitialSection(
                    if (type == TmdbMediaType.TV) "animation_tv" else "animation_movie")
            }
        }
    }

    private fun setRow(type: TmdbMediaType, row: TmdbPopularState) {
        _state.value = if (type == TmdbMediaType.TV) _state.value.copy(tv = row)
            else _state.value.copy(movies = row)
    }

    private fun completeInitialSection(id: String) {
        initialSectionsPending.remove(id)
        if (initialSectionsPending.isEmpty()) finishInitialHomeLoad()
    }

    private fun finishInitialHomeLoad() {
        if (_state.value.initialHomeLoading) {
            Log.i(TAG, "Initial home load finished")
            _state.value = _state.value.copy(initialHomeLoading = false)
        }
    }

    private companion object {
        const val TAG = "HomeCatalog"
    }
}
