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
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.core.tmdb.TmdbSeason
import ua.any2b.core.tmdb.TmdbGeneralCatalog
import ua.any2b.core.anilist.HOME_FETCH_SIZE
import ua.any2b.tvplayer.data.AniListTmdbCatalog
import ua.any2b.tvplayer.data.AniListLocalDataMigrator
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistEntry
import ua.any2b.tvplayer.data.TmdbWatchlistStore
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
    val watchlist: List<TmdbWatchlistHomeItem> = emptyList(),
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

data class TmdbWatchlistHomeItem(
    val entry: TmdbWatchlistEntry,
    val logoUrl: String? = null,
    val seasons: List<TmdbSeason> = emptyList(),
    val selectedSeasonNumber: Int? = null,
    val airedEpisodeCount: Int? = null,
    val selectedSeasonEpisodeCount: Int? = null,
    val nextEpisodes: List<TmdbEpisodeHomeCard> = emptyList(),
    val episodesLoading: Boolean = false,
)

data class TmdbEpisodeHomeCard(val episode: TmdbEpisode, val cachedImagePath: String?)

class TmdbLibraryViewModel(application: Application,
    private val animationOnly: Boolean = false) : AndroidViewModel(application) {
    private val malTmdbCatalog = MalTmdbCatalog(application)
    private val aniListCatalog = AniListTmdbCatalog(application)
    private val history = TmdbWatchHistoryStore(application)
    private val watchlist = TmdbWatchlistStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val localDataMigrator = AniListLocalDataMigrator(application)
    private val settings = SettingsManager(application)
    private val _state = MutableStateFlow(TmdbLibraryState(
        history = history.all().filter(::visibleInCurrentCatalog),
        favorites = favorites.all().filter(::visibleInCurrentCatalog),
        mainSections = if (animationOnly) settings.mainHomeSections() else emptyList(),
        initialHomeLoading = animationOnly))
    val state: StateFlow<TmdbLibraryState> = _state
    private fun visibleInCurrentCatalog(title: TmdbTitle): Boolean = animationOnly ||
        title.genreIds.isNotEmpty() && TmdbGeneralCatalog.accepts(title)
    private var recommendationSources: List<Pair<TmdbMediaType, Int>>? = null
    private var recommendationsJob: Job? = null
    private var aniListHomeJob: Job? = null
    private var legacyMigrationJob: Job? = null
    private var watchlistEpisodesJob: Job? = null
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
            refreshPersonalLists()
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
        _state.value = _state.value.copy(
            watchlist = watchlist.all().filter {
                animationOnly || visibleInCurrentCatalog(it.title)
            }.map {
                TmdbWatchlistHomeItem(it, logoUrl = watchlist.cachedLogo(it.title.type, it.title.id),
                    seasons = if (it.title.type == TmdbMediaType.TV)
                        watchlist.cachedSeasons(it.title.id).orEmpty() else emptyList(),
                    episodesLoading = it.title.type == TmdbMediaType.TV)
            },
            history = watched.filter(::visibleInCurrentCatalog),
            favorites = saved.filter(::visibleInCurrentCatalog),
            mainSections = sections)
        refreshWatchlistEpisodes(watchlist.all().filter {
            animationOnly || visibleInCurrentCatalog(it.title)
        })
        if (!animationOnly) {
            return
        }
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

    fun selectWatchlistSeason(title: TmdbTitle, seasonNumber: Int) {
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(selectedSeasonNumber = seasonNumber, episodesLoading = true)
            else item
        })
        refreshWatchlistEpisodes(watchlist.all().filter {
            animationOnly || visibleInCurrentCatalog(it.title)
        })
    }

    fun removeFromHistory(title: TmdbTitle) {
        history.removeFromHistory(title)
        refreshPersonalLists()
    }

    private fun refreshWatchlistEpisodes(entries: List<TmdbWatchlistEntry>) {
        watchlistEpisodesJob?.cancel()
        if (entries.isEmpty()) return
        watchlistEpisodesJob = viewModelScope.launch {
            val connection = workerTmdbConnection(getApplication()) ?: run {
                _state.value = _state.value.copy(watchlist = _state.value.watchlist.map {
                    it.copy(episodesLoading = false)
                })
                return@launch
            }
            for (entry in entries) {
                try {
                    val title = entry.title
                    val requiresDetails = !watchlist.hasCachedLogo(title.type, title.id) ||
                        title.originCountries.isEmpty() || title.ukrainianTitle.isNullOrBlank() ||
                        (title.type == TmdbMediaType.TV && watchlist.cachedSeasons(title.id) == null)
                    val details = if (requiresDetails) connection.client.details(title.type,
                        title.id, connection.accessKey) else null
                    if (details != null) {
                        watchlist.saveLogo(title.type, title.id, details.logoUrl)
                        watchlist.updateTitleMetadata(details.title)
                        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map {
                            if (it.entry.title.type == title.type && it.entry.title.id == title.id)
                                it.copy(entry = it.entry.copy(title = details.title),
                                    seasons = details.seasonEntries,
                                    logoUrl = details.logoUrl) else it
                        })
                        if (title.type == TmdbMediaType.TV)
                            watchlist.saveSeasons(title.id, details.seasonEntries)
                    }
                    if (title.type != TmdbMediaType.TV) continue
                    val seasons = watchlist.cachedSeasons(title.id).orEmpty()
                    val progress = history.episodeProgress(TmdbMediaType.TV, title.id)
                    val selectedSeason = _state.value.watchlist.firstOrNull {
                        it.entry.title.type == title.type && it.entry.title.id == title.id
                    }?.selectedSeasonNumber
                    var seasonNumber = selectedSeason ?: progress?.seasonNumber ?: seasons.firstOrNull {
                        it.number > 0 && (it.episodeCount ?: 0) > 0
                    }?.number ?: 1
                    var episodeNumber = if (selectedSeason != null &&
                        selectedSeason != progress?.seasonNumber) 1 else progress?.let {
                        if (it.progress >= 0.95f) it.episodeNumber + 1 else it.episodeNumber
                    } ?: 1
                    suspend fun seasonEpisodes(number: Int): List<TmdbEpisode> =
                        watchlist.cachedEpisodes(title.id, number) ?: connection.client
                            .seasonDetails(title.id, number, connection.accessKey).episodes.also {
                                watchlist.saveEpisodes(title.id, number, it)
                            }
                    var season = seasons.firstOrNull { it.number == seasonNumber }
                    var episodes = season?.let { seasonEpisodes(it.number) }.orEmpty()
                    var upcoming = episodes.filter { it.episodeNumber >= episodeNumber && it.hasAired() }
                    if (upcoming.isEmpty()) {
                        for (candidate in seasons.filter { it.number > 0 }
                            .sortedByDescending { it.number }) {
                            val released = seasonEpisodes(candidate.number).filter { it.hasAired() }
                            if (released.isNotEmpty()) {
                                season = candidate
                                seasonNumber = candidate.number
                                episodes = released
                                upcoming = released.take(1)
                                break
                            }
                        }
                    }
                    if (season == null) {
                        updateWatchlistEpisodes(title, emptyList(), false)
                        continue
                    }
                    updateSelectedWatchlistSeason(title, seasonNumber)
                    val airedCount = episodes.count { it.hasAired() }
                    updateWatchlistSeasonCounts(title, airedCount,
                        season?.episodeCount ?: episodes.size)
                    upcoming = upcoming.take(12)
                    watchlist.cacheStills(title.id, upcoming)
                    updateWatchlistEpisodes(title, upcoming.map { episode ->
                        TmdbEpisodeHomeCard(episode,
                            watchlist.cachedStill(title.id, episode)?.absolutePath)
                    }, false)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    updateWatchlistEpisodes(entry.title, emptyList(), false)
                }
            }
        }
    }

    private fun updateSelectedWatchlistSeason(title: TmdbTitle, seasonNumber: Int) {
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(selectedSeasonNumber = seasonNumber) else item
        })
    }

    private fun updateWatchlistSeasonCounts(title: TmdbTitle, airedCount: Int, episodeCount: Int) {
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map {
            if (it.entry.title.type == title.type && it.entry.title.id == title.id)
                it.copy(airedEpisodeCount = airedCount,
                    selectedSeasonEpisodeCount = episodeCount) else it
        })
    }

    private fun TmdbEpisode.hasAired(): Boolean {
        val date = airDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: return false
        return !date.isAfter(java.time.LocalDate.now())
    }

    private fun updateWatchlistEpisodes(
        title: TmdbTitle,
        episodes: List<TmdbEpisodeHomeCard>,
        loading: Boolean,
    ) {
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(nextEpisodes = episodes, episodesLoading = loading) else item
        })
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
                    genreId = if (animationOnly) 16 else null,
                    excludedGenreIds = if (animationOnly) emptySet() else
                        ua.any2b.core.tmdb.TmdbGeneralCatalog.excludedGenreIds)
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
