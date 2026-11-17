package ua.any2b.tvplayer.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbFailure
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.core.tmdb.TmdbSeason
import ua.any2b.core.tmdb.TmdbGeneralCatalog
import ua.any2b.core.anilist.HOME_FETCH_SIZE
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.core.toloka.TorrServerClient
import ua.any2b.core.toloka.TorrServerFailure
import ua.any2b.core.toloka.TolokaReleaseMetadataParser
import ua.any2b.core.toloka.WorkerFailure
import ua.any2b.tvplayer.data.AniListTmdbCatalog
import ua.any2b.tvplayer.data.AniListLocalDataMigrator
import ua.any2b.tvplayer.data.TmdbFavoritesStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistEntry
import ua.any2b.tvplayer.data.TmdbWatchlistStore
import ua.any2b.tvplayer.data.AniTubeVoiceOption
import ua.any2b.tvplayer.data.MalTmdbCatalog
import ua.any2b.tvplayer.data.HomeSectionSetting
import ua.any2b.tvplayer.data.HomeRowCache
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaConnections
import ua.any2b.tvplayer.data.tmdbHomeRecommendations
import ua.any2b.tvplayer.data.workerTmdbConnection
import ua.any2b.tvplayer.data.TolokaRssTmdbCatalog
import ua.any2b.tvplayer.data.TolokaRssTmdbRows

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

sealed interface TolokaRssState {
    data object Loading : TolokaRssState
    data class Results(val rows: TolokaRssTmdbRows) : TolokaRssState
    data class Error(val message: String) : TolokaRssState
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
    val tolokaReleases: TolokaRssState = TolokaRssState.Loading,
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
    val anitubeVoices: List<AniTubeVoiceOption> = emptyList(),
    val selectedVoiceIndex: Int = 0,
    val displaySeasonNumber: Int? = null,
    val playbackProgress: Float? = null,
    val playbackRemainingMs: Long? = null,
    val playbackEpisodeNumber: Int? = null,
)

data class TmdbEpisodeHomeCard(
    val episode: TmdbEpisode,
    val cachedImagePath: String?,
    val watched: Boolean = false,
)

private data class PersonalListsSnapshot(
    val watched: List<TmdbTitle>,
    val favorites: List<TmdbTitle>,
    val watchlist: List<TmdbWatchlistEntry>,
    val sections: List<HomeSectionSetting>,
    val watchlistItems: List<TmdbWatchlistHomeItem>,
)

class TmdbLibraryViewModel(application: Application,
    private val animationOnly: Boolean = false) : AndroidViewModel(application) {
    private val malTmdbCatalog = MalTmdbCatalog(application)
    private val aniListCatalog = AniListTmdbCatalog(application)
    private val history = TmdbWatchHistoryStore(application)
    private val watchlist = TmdbWatchlistStore(application)
    private val favorites = TmdbFavoritesStore(application)
    private val localDataMigrator = AniListLocalDataMigrator(application)
    private val settings = SettingsManager(application)
    private val torrServer = TorrServerClient()
    private val tolokaRss = TolokaRssTmdbCatalog(application)
    private val homeRows = HomeRowCache(application)
    private val _state = MutableStateFlow(TmdbLibraryState(
        initialHomeLoading = animationOnly))
    val state: StateFlow<TmdbLibraryState> = _state
    private fun visibleInCurrentCatalog(title: TmdbTitle): Boolean = animationOnly ||
        title.genreIds.isNotEmpty() && TmdbGeneralCatalog.accepts(title)
    private var recommendationSources: List<Pair<TmdbMediaType, Int>>? = null
    private var recommendationsJob: Job? = null
    private var aniListHomeJob: Job? = null
    private var legacyMigrationJob: Job? = null
    private var watchlistEpisodesJob: Job? = null
    private var personalListsJob: Job? = null
    private var tolokaRefreshJob: Job? = null
    private var lastTolokaRefreshAtMs = 0L
    private val popularJobs = mutableMapOf<TmdbMediaType, Job>()
    private var malTopJob: Job? = null
    private val homeNetworkPermits = Semaphore(2)
    private val stillCachePermits = Semaphore(1)
    // Network rows must never keep the watchlist behind the initial loading screen.
    private val initialSectionsPending = mutableSetOf("personal_lists")

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { readCachedHomeState() }
            _state.value = cached.copy(initialHomeLoading = false)
            // Let Compose draw the cached screen before starting metadata/image work.
            delay(200L)
            startHomeRefreshes()
        }
    }

    private fun readCachedHomeState(): TmdbLibraryState {
        val watchlistItems = if (animationOnly) watchlist.all().map { entry ->
            val savedProgress = history.episodeProgress(entry.title.type, entry.title.id)
            val savedProgressSeason = savedProgress?.seasonNumber
                ?: entry.title.matchedSeasonNumber
            val savedSelection = savedProgressSeason?.let {
                history.playbackSelection(entry.title.type, entry.title.id, it)
            } ?: history.playbackSelection(entry.title.type, entry.title.id)
            TmdbWatchlistHomeItem(entry,
                logoUrl = watchlist.cachedLogo(entry.title.type, entry.title.id),
                seasons = if (entry.title.type == TmdbMediaType.TV)
                    watchlist.cachedSeasons(entry.title.id).orEmpty() else emptyList(),
                selectedSeasonNumber = savedProgressSeason,
                displaySeasonNumber = savedProgress?.animeSeasonNumber
                    ?: entry.title.matchedAnimeSeasonNumber ?: savedSelection?.anitubeSeasonNumber,
                playbackProgress = savedProgress?.takeIf { it.durationMs > 0L }?.progress,
                playbackRemainingMs = savedProgress?.takeIf { it.durationMs > 0L }?.let {
                    (it.durationMs - it.positionMs).coerceAtLeast(0L)
                },
                playbackEpisodeNumber = savedProgress?.episodeNumber,
                anitubeVoices = if (savedSelection?.source ==
                    ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE)
                    watchlist.cachedAniTubeVoices(entry.title.type, entry.title.id,
                        savedProgressSeason ?: 1, savedSelection.anitubeReleaseId ?: -1)
                    else emptyList(),
                selectedVoiceIndex = savedSelection?.voiceIndex ?: 0)
        } else emptyList()
        val cacheKeys = setOf("recommendations", "mal_top", "trending", "popular_season",
            if (animationOnly) "animation_tv" else "general_tv",
            if (animationOnly) "animation_movie" else "general_movie")
        val cachedRows = homeRows.readRows(cacheKeys)
        fun cachedRow(key: String) = cachedRows[key].orEmpty()
        fun cachedPopular(key: String) = cachedRow(key)
            .takeIf(List<TmdbTitle>::isNotEmpty)?.let(TmdbPopularState::Results)
            ?: TmdbPopularState.Loading
        fun cachedAnime(key: String) = cachedRow(key)
            .takeIf(List<TmdbTitle>::isNotEmpty)?.let(AniListHomeState::Results)
            ?: AniListHomeState.Loading
        val toloka = tolokaRss.cached()
        return TmdbLibraryState(
            watchlist = watchlistItems,
            history = history.all().filter(::visibleInCurrentCatalog),
            favorites = favorites.all().filter(::visibleInCurrentCatalog),
            recommendations = cachedRow("recommendations"),
            mainSections = if (animationOnly) settings.mainHomeSections() else emptyList(),
            tv = cachedPopular(if (animationOnly) "animation_tv" else "general_tv"),
            movies = cachedPopular(if (animationOnly) "animation_movie" else "general_movie"),
            malTop = cachedRow("mal_top").takeIf(List<TmdbTitle>::isNotEmpty)
                ?.let { MalTopState.Results(it, hasMore = true) } ?: MalTopState.Loading,
            trending = cachedAnime("trending"),
            popularSeason = cachedAnime("popular_season"),
            tolokaReleases = if (toloka.isEmpty) TolokaRssState.Loading
                else TolokaRssState.Results(toloka),
            initialHomeLoading = false,
        )
    }

    private fun startHomeRefreshes() {
        migrateLegacyAniListData()
        refreshPersonalLists()
        if (animationOnly) {
            val enabled = _state.value.mainSections.filter { it.enabled }.map { it.id }.toSet()
            Log.i(TAG, "Home load started animationOnly=true enabledSections=${enabled.sorted()}")
            if ("animation_tv" in enabled) refreshPopular(TmdbMediaType.TV)
            if ("animation_movie" in enabled) refreshPopular(TmdbMediaType.MOVIE)
            if ("mal_top" in enabled) refreshMalTop()
            if (enabled.any { it == "trending" || it == "popular_season" }) refreshAniListHome()
        } else {
            refreshPopular(TmdbMediaType.TV)
            refreshPopular(TmdbMediaType.MOVIE)
        }
    }

    fun loadTolokaRssIfNeeded() = refreshTolokaRss(force = false)

    fun refreshTolokaRss() = refreshTolokaRss(force = true)

    private fun refreshTolokaRss(force: Boolean) {
        if (tolokaRefreshJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastTolokaRefreshAtMs > 0L && now - lastTolokaRefreshAtMs <
            TOLOKA_VISIBLE_REFRESH_COOLDOWN_MS) return
        lastTolokaRefreshAtMs = now
        tolokaRefreshJob = viewModelScope.launch {
            try {
                val rows = homeNetworkPermits.withPermit {
                    withContext(Dispatchers.IO) { tolokaRss.refresh() }
                }
                _state.value = _state.value.copy(tolokaReleases = TolokaRssState.Results(rows))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Toloka RSS load failed: ${error.message}", error)
                if (_state.value.tolokaReleases !is TolokaRssState.Results)
                    _state.value = _state.value.copy(tolokaReleases =
                        TolokaRssState.Error("Не вдалося завантажити новинки Toloka"))
            }
        }
    }

    fun refreshMalTop() {
        if (malTopJob?.isActive == true) return
        if (_state.value.malTop !is MalTopState.Results)
            _state.value = _state.value.copy(malTop = MalTopState.Loading)
        malTopJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Home section load started section=mal_top source=MAL+Fribb")
            try {
                val page = homeNetworkPermits.withPermit {
                    malTmdbCatalog.page(page = 1)
                }
                _state.value = _state.value.copy(malTop =
                    MalTopState.Results(page.titles, page.hasMore))
                withContext(Dispatchers.IO) { homeRows.save("mal_top", page.titles) }
                Log.i(TAG, "Home section load succeeded section=mal_top count=${page.titles.size} " +
                    "hasMore=${page.hasMore} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                completeInitialSection("mal_top")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Home section load failed section=mal_top " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "errorType=${error.javaClass.name} message=${error.message}", error)
                if (_state.value.malTop !is MalTopState.Results)
                    _state.value = _state.value.copy(malTop = MalTopState.Error(
                        error.message ?: "Не вдалося завантажити рейтинг MAL"))
                completeInitialSection("mal_top")
            }
        }
    }

    fun refreshAniListHome() {
        if (aniListHomeJob?.isActive == true) return
        _state.value = _state.value.copy(
            trending = _state.value.trending.takeIf { it is AniListHomeState.Results }
                ?: AniListHomeState.Loading,
            popularSeason = _state.value.popularSeason.takeIf { it is AniListHomeState.Results }
                ?: AniListHomeState.Loading)
        aniListHomeJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Home section load started section=trending,popular_season source=AniList")
            try {
                val rows = homeNetworkPermits.withPermit {
                    aniListCatalog.homeRows(perRow = HOME_FETCH_SIZE)
                }
                val freshTrending = rows["trending"].orEmpty()
                val freshSeason = rows["popular_season"].orEmpty()
                _state.value = _state.value.copy(
                    trending = freshTrending.takeIf(List<TmdbTitle>::isNotEmpty)
                        ?.let(AniListHomeState::Results) ?: _state.value.trending,
                    popularSeason = freshSeason.takeIf(List<TmdbTitle>::isNotEmpty)
                        ?.let(AniListHomeState::Results) ?: _state.value.popularSeason)
                withContext(Dispatchers.IO) {
                    homeRows.save("trending", freshTrending)
                    homeRows.save("popular_season", freshSeason)
                }
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
                _state.value = _state.value.copy(
                    trending = _state.value.trending.takeIf { it is AniListHomeState.Results }
                        ?: AniListHomeState.Error(message),
                    popularSeason = _state.value.popularSeason.takeIf {
                        it is AniListHomeState.Results
                    } ?: AniListHomeState.Error(message))
            } finally {
                completeInitialSection("trending")
                completeInitialSection("popular_season")
            }
        }
    }

    fun refreshPersonalLists() {
        if (personalListsJob?.isActive == true) return
        personalListsJob = viewModelScope.launch {
        val oldWatchlist = _state.value.watchlist
        val snapshot = withContext(Dispatchers.IO) {
            val watched = history.all()
            val saved = favorites.all()
            val savedWatchlist = if (animationOnly) watchlist.all() else emptyList()
            val sections = if (animationOnly) settings.mainHomeSections() else emptyList()
            val items = savedWatchlist.map { entry ->
                val previous = oldWatchlist.firstOrNull {
                    it.entry.title.type == entry.title.type && it.entry.title.id == entry.title.id
                }
                val progress = history.episodeProgress(entry.title.type, entry.title.id)
                val progressSeason = progress?.seasonNumber
                val selection = history.playbackSelection(entry.title.type, entry.title.id,
                    progressSeason ?: entry.title.matchedSeasonNumber)
                TmdbWatchlistHomeItem(entry,
                    logoUrl = watchlist.cachedLogo(entry.title.type, entry.title.id),
                    seasons = if (entry.title.type == TmdbMediaType.TV)
                        watchlist.cachedSeasons(entry.title.id).orEmpty() else emptyList(),
                    selectedSeasonNumber = when {
                        previous == null -> progressSeason ?: entry.title.matchedSeasonNumber
                        progressSeason != null && previous.selectedSeasonNumber != progressSeason ->
                            progressSeason
                        else -> previous.selectedSeasonNumber
                    },
                    nextEpisodes = previous?.nextEpisodes.orEmpty(),
                    episodesLoading = entry.title.type == TmdbMediaType.TV,
                    anitubeVoices = selection?.anitubeReleaseId?.let { releaseId ->
                        watchlist.cachedAniTubeVoices(entry.title.type, entry.title.id,
                            progressSeason ?: entry.title.matchedSeasonNumber ?: 1, releaseId)
                    }.orEmpty(),
                    selectedVoiceIndex = selection?.voiceIndex ?: 0,
                    displaySeasonNumber = progress?.animeSeasonNumber
                        ?: entry.title.matchedAnimeSeasonNumber ?: selection?.anitubeSeasonNumber,
                    playbackProgress = progress?.takeIf { it.durationMs > 0L }?.progress,
                    playbackRemainingMs = progress?.takeIf { it.durationMs > 0L }?.let {
                        (it.durationMs - it.positionMs).coerceAtLeast(0L)
                    },
                    playbackEpisodeNumber = progress?.episodeNumber)
            }
            PersonalListsSnapshot(watched, saved, savedWatchlist, sections, items)
        }
        val watched = snapshot.watched
        val saved = snapshot.favorites
        val savedWatchlist = snapshot.watchlist
        val sections = snapshot.sections
        if (animationOnly) Log.i(TAG, "Home watchlist loaded entries=${savedWatchlist.size}")
        val previouslyEnabled = _state.value.mainSections.filter { it.enabled }.map { it.id }.toSet()
        val nowEnabled = sections.filter { it.enabled }.map { it.id }.toSet()
        _state.value = _state.value.copy(
            watchlist = snapshot.watchlistItems,
            history = watched.filter(::visibleInCurrentCatalog),
            favorites = saved.filter(::visibleInCurrentCatalog),
            mainSections = sections)
        if (animationOnly) refreshWatchlistEpisodes(savedWatchlist)
        if (!animationOnly) {
            return@launch
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
            return@launch
        }
        val sources = (watched + saved).distinctBy { it.type to it.id }
            .take(4).map { it.type to it.id }
        if (sources == recommendationSources) return@launch
        recommendationSources = sources
        recommendationsJob?.cancel()
        // Keep the last successful row visible until the new suggestions arrive.
        if (sources.isEmpty()) {
            completeInitialSection("recommendations")
            return@launch
        }
        recommendationsJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Home section load started section=recommendations source=TMDB " +
                "seedCount=${sources.size}")
            try {
                val titles = homeNetworkPermits.withPermit {
                    tmdbHomeRecommendations(getApplication())
                }
                if (recommendationSources == sources) {
                    _state.value = _state.value.copy(recommendations = titles)
                    withContext(Dispatchers.IO) { homeRows.save("recommendations", titles) }
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
                    // A failed refresh must not remove cached suggestions.
                    completeInitialSection("recommendations")
                }
            }
        }
        }
    }

    fun selectWatchlistSeason(title: TmdbTitle, seasonNumber: Int) {
        val selection = history.playbackSelection(title.type, title.id, seasonNumber)
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(selectedSeasonNumber = seasonNumber, episodesLoading = true,
                    displaySeasonNumber = selection?.anitubeSeasonNumber ?: seasonNumber,
                    anitubeVoices = selection?.anitubeReleaseId?.let { releaseId ->
                        watchlist.cachedAniTubeVoices(title.type, title.id, seasonNumber, releaseId)
                    }.orEmpty(), selectedVoiceIndex = selection?.voiceIndex ?: 0)
            else item
        })
        refreshWatchlistEpisodes(watchlist.all().filter {
            animationOnly || visibleInCurrentCatalog(it.title)
        })
    }

    fun selectWatchlistVoice(title: TmdbTitle, seasonNumber: Int, voiceIndex: Int) {
        val selection = history.playbackSelection(title.type, title.id, seasonNumber)
            ?: return
        if (selection.source != ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE)
            return
        val names = watchlist.cachedAniTubeVoices(title.type, title.id, seasonNumber,
            selection.anitubeReleaseId ?: return)
        val option = names.getOrNull(voiceIndex) ?: return
        val selectedIndex = option.primaryIndex
        history.savePlaybackSelection(title.type, title.id,
            selection.copy(voiceIndex = selectedIndex, voiceName = option.name), seasonNumber)
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(selectedVoiceIndex = selectedIndex, anitubeVoices = names,
                    episodesLoading = true) else item
        })
        refreshWatchlistEpisodes(watchlist.all().filter {
            animationOnly || visibleInCurrentCatalog(it.title)
        })
    }

    fun hasPlayableWatchlistSeason(title: TmdbTitle, seasonNumber: Int): Boolean {
        val selection = history.playbackSelection(title.type, title.id, seasonNumber)
            ?: history.inferredPlaybackSelection(title.type, title.id)?.takeIf {
                it.source == ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT ||
                    (it.tmdbSeasonNumber ?: it.anitubeSeasonNumber) == seasonNumber
            }
            ?: return true
        val playlistKey = when (selection.source) {
            ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT ->
                selection.topicId?.let { "torrent_$it" }
            ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE ->
                selection.anitubeReleaseId?.let { "anitube_${it}_${selection.voiceIndex}" }
        } ?: return false
        return watchlist.cachedPlayableEpisodes(title.type, title.id, playlistKey,
            seasonNumber)?.isNotEmpty() == true
    }

    fun removeFromHistory(title: TmdbTitle) {
        history.removeFromHistory(title)
        refreshPersonalLists()
    }

    private fun refreshWatchlistEpisodes(entries: List<TmdbWatchlistEntry>) {
        watchlistEpisodesJob?.cancel()
        if (entries.isEmpty()) return
        watchlistEpisodesJob = viewModelScope.launch(Dispatchers.IO) {
            val connection = withContext(Dispatchers.IO) {
                workerTmdbConnection(getApplication())
            } ?: run {
                _state.value = _state.value.copy(watchlist = _state.value.watchlist.map {
                    it.copy(episodesLoading = false)
                })
                return@launch
            }
            for (entry in entries) {
                try {
                    val title = entry.title
                    val logoLanguagePriority = settings.logoLanguagePriority()
                    val requiresDetails = !watchlist.hasCachedLogo(title.type, title.id,
                        logoLanguagePriority) ||
                        title.originCountries.isEmpty() || title.ukrainianTitle.isNullOrBlank() ||
                        (title.type == TmdbMediaType.TV && watchlist.cachedSeasons(title.id) == null)
                    val details = if (requiresDetails) homeNetworkPermits.withPermit {
                        connection.client.details(title.type, title.id, connection.accessKey,
                            logoLanguagePriority = logoLanguagePriority)
                    } else null
                    if (details != null) {
                        watchlist.saveLogo(title.type, title.id, details.logoUrl,
                            logoLanguagePriority)
                        val enrichedTitle = details.title.copy(
                            matchedSeasonTitle = title.matchedSeasonTitle,
                            matchedSeasonYear = title.matchedSeasonYear,
                            matchedSeasonNumber = title.matchedSeasonNumber,
                            matchedAniListId = title.matchedAniListId,
                            matchedAnimeSeasonNumber = title.matchedAnimeSeasonNumber)
                        watchlist.updateTitleMetadata(enrichedTitle)
                        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map {
                            if (it.entry.title.type == title.type && it.entry.title.id == title.id)
                                it.copy(entry = it.entry.copy(title = enrichedTitle),
                                    seasons = details.seasonEntries,
                                    logoUrl = details.logoUrl) else it
                        })
                        if (title.type == TmdbMediaType.TV)
                            watchlist.saveSeasons(title.id, details.seasonEntries)
                    }
                    if (title.type != TmdbMediaType.TV) continue
                    val seasons = watchlist.cachedSeasons(title.id).orEmpty().toMutableList()
                    val progress = history.episodeProgress(TmdbMediaType.TV, title.id)
                    val selectedSeason = _state.value.watchlist.firstOrNull {
                        it.entry.title.type == title.type && it.entry.title.id == title.id
                    }?.selectedSeasonNumber
                    var seasonNumber = selectedSeason ?: progress?.seasonNumber ?: seasons.firstOrNull {
                        it.number > 0 && (it.episodeCount ?: 0) > 0
                    }?.number ?: 1
                    val selection = history.playbackSelection(title.type, title.id, seasonNumber)
                        ?: history.inferredPlaybackSelection(title.type, title.id)?.takeIf {
                            it.source == ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT ||
                                (it.tmdbSeasonNumber ?: it.anitubeSeasonNumber) == seasonNumber
                        }
                    var episodeNumber = if (selectedSeason != null &&
                        selectedSeason != progress?.seasonNumber) 1 else progress?.let {
                        if (it.progress >= 0.95f) it.episodeNumber + 1 else it.episodeNumber
                    } ?: 1
                    suspend fun seasonEpisodes(number: Int): List<TmdbEpisode> =
                        watchlist.cachedEpisodes(title.id, number) ?: homeNetworkPermits.withPermit {
                            connection.client.seasonDetails(title.id, number,
                                connection.accessKey).episodes
                        }.also {
                                watchlist.saveEpisodes(title.id, number, it)
                            }
                    val isAniTubeSeason = selection?.source ==
                        ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE &&
                        watchlist.cachedEpisodes(title.id, seasonNumber) != null &&
                        (selection.tmdbSeasonNumber ?: progress?.seasonNumber ?:
                            title.matchedSeasonNumber) == seasonNumber
                    if (isAniTubeSeason && seasons.none { it.number == seasonNumber }) {
                        val cached = watchlist.cachedEpisodes(title.id, seasonNumber).orEmpty()
                        seasons.add(TmdbSeason(seasonNumber, "Сезон $seasonNumber", null,
                            cached.size))
                        watchlist.saveSeasons(title.id, seasons)
                        updateWatchlistSeasons(title, seasons)
                    }
                    var season = seasons.firstOrNull { it.number == seasonNumber }
                    var episodes = if (isAniTubeSeason) watchlist.cachedEpisodes(title.id,
                        seasonNumber).orEmpty() else season?.let { seasonEpisodes(it.number) }.orEmpty()
                    val playlistKey = when (selection?.source) {
                        ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT ->
                            selection.topicId?.let { "torrent_$it" }
                        ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE ->
                            selection.anitubeReleaseId?.let {
                                "anitube_${it}_${selection.voiceIndex}"
                            }
                        null -> null
                    }
                    var playableEpisodes = playlistKey?.let {
                        watchlist.cachedPlayableEpisodes(title.type, title.id, it, seasonNumber)
                    }?.takeIf { it.isNotEmpty() }
                    if (playableEpisodes == null && selection?.source ==
                        ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT) {
                        recoverTolokaEpisodes(title, selection, seasonNumber, seasons)
                        playableEpisodes = playlistKey?.let {
                            watchlist.cachedPlayableEpisodes(title.type, title.id, it, seasonNumber)
                        }?.takeIf { it.isNotEmpty() }
                    }
                    var upcoming = episodes.filter {
                        it.episodeNumber >= episodeNumber && (it.hasAired() || isAniTubeSeason) &&
                            playableEpisodes?.contains(it.episodeNumber) != false
                    }
                    if (upcoming.isEmpty() && !isAniTubeSeason) {
                        for (candidate in seasons.filter { it.number > seasonNumber }
                            .sortedBy { it.number }) {
                            val released = seasonEpisodes(candidate.number).filter { it.hasAired() }
                            if (released.isNotEmpty()) {
                                season = candidate
                                seasonNumber = candidate.number
                                episodes = released
                                upcoming = released.take(1)
                                playableEpisodes = playlistKey?.let {
                                    watchlist.cachedPlayableEpisodes(title.type, title.id, it,
                                        seasonNumber)
                                }?.takeIf { it.isNotEmpty() }
                                if (playableEpisodes != null)
                                    upcoming = upcoming.filter {
                                        playableEpisodes?.contains(it.episodeNumber) == true
                                    }
                                break
                            }
                        }
                    }
                    if (season == null) {
                        updateWatchlistEpisodes(title, emptyList(), false)
                        continue
                    }
                    updateSelectedWatchlistSeason(title, seasonNumber)
                    val airedCount = if (isAniTubeSeason) episodes.size else episodes.count { it.hasAired() }
                    updateWatchlistSeasonCounts(title, airedCount,
                        season?.episodeCount ?: episodes.size)
                    val progressSeason = progress?.seasonNumber
                    val watchedEpisodes = episodes.filter { episode ->
                        (episode.hasAired() || isAniTubeSeason) && (progressSeason != null &&
                            (seasonNumber < progressSeason || seasonNumber == progressSeason &&
                                (episode.episodeNumber < (progress?.episodeNumber ?: 1) ||
                                    episode.episodeNumber == progress.episodeNumber &&
                                    (progress?.progress ?: 0f) >= 0.95f)))
                    }
                    val visibleUpcoming = upcoming.take(12)
                    val visibleEpisodes = (watchedEpisodes + visibleUpcoming)
                        .distinctBy { it.episodeNumber }
                    val cards = visibleEpisodes.map { episode ->
                        TmdbEpisodeHomeCard(episode,
                            watchlist.cachedStill(title.id, episode)?.absolutePath,
                            watched = episode in watchedEpisodes)
                    }
                    updateWatchlistEpisodes(title, cards, false)
                    // Fill the visible episode still cache after the row is already usable.
                    if (visibleUpcoming.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) {
                        stillCachePermits.withPermit {
                            watchlist.cacheStills(title.id, visibleUpcoming.take(5))
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    updateWatchlistEpisodes(entry.title, emptyList(), false)
                }
            }
        }
    }

    /** Rebuilds a missing playlist episode index from TorrServer's persisted file stats. */
    private suspend fun recoverTolokaEpisodes(
        title: TmdbTitle,
        selection: ua.any2b.tvplayer.data.TmdbPlaybackSelection,
        selectedSeason: Int,
        seasons: List<TmdbSeason>,
    ) {
        val topicId = selection.topicId ?: return
        val serverUrl = settings.torrServerUrl().trimEnd('/')
        if (serverUrl.isBlank()) return
        val hashKey = "$serverUrl|$topicId"
        try {
            val credentials = TorrServerAuthStore(getApplication()).read()
                ?.let { TorrServerBasicAuth(it.username, it.password) }
            val hashPrefs = getApplication<Application>().getSharedPreferences(
                "torrserver_topic_hashes", 0)
            val savedHash = hashPrefs.getString(hashKey, null)
            val torrent = try {
                savedHash?.let { torrServer.get(serverUrl, it, credentials) }
            } catch (error: TorrServerFailure.Http) {
                if (error.status != 404) throw error
                Log.i("WatchlistPlayback", "Saved TorrServer hash expired; reloading topic=$topicId")
                null
            } ?: run {
                val bytes = try {
                    TolokaConnections.torrent(getApplication(), topicId)
                } catch (_: WorkerFailure.LinkExpired) {
                    val queries = listOfNotNull(title.ukrainianTitle, title.title,
                        title.originalTitle, title.englishTitle)
                        .map(String::trim).filter(String::isNotBlank)
                        .distinctBy(String::lowercase).take(4)
                    var refreshed = false
                    for (query in queries) {
                        val result = TolokaConnections.search(getApplication(), query)
                        if (result.results.any { it.id == topicId }) {
                            refreshed = true
                            break
                        }
                    }
                    if (!refreshed) throw WorkerFailure.LinkExpired()
                    TolokaConnections.torrent(getApplication(), topicId)
                }
                torrServer.upload(serverUrl, bytes, credentials).also { refreshed ->
                    hashPrefs.edit().putString(hashKey, refreshed.hash).apply()
                    Log.i("WatchlistPlayback", "Re-added torrent to TorrServer " +
                        "topic=$topicId files=${refreshed.files.size}")
                }
            }
            val episodesBySeason = mutableMapOf<Int, MutableSet<Int>>()
            torrent.videoFiles.forEach { file ->
                val metadata = TolokaReleaseMetadataParser.parse(file.name)
                val folderSeason = file.path.substringBeforeLast('/', "")
                    .takeIf(String::isNotBlank)?.let(TolokaReleaseMetadataParser::parse)?.season
                val absolute = TolokaReleaseMetadataParser.mapAbsoluteEpisodes(metadata, seasons)
                if (absolute != null) {
                    absolute.forEach { range ->
                        episodesBySeason.getOrPut(range.seasonNumber) { mutableSetOf() }
                            .addAll(range.episodeStart..range.episodeEnd)
                    }
                } else {
                    val season = metadata.season ?: folderSeason ?: selectedSeason
                    val first = metadata.episodeStart ?: return@forEach
                    val last = metadata.episodeEnd ?: first
                    if (season > 0 && first > 0 && last >= first) {
                        episodesBySeason.getOrPut(season) { mutableSetOf() }
                            .addAll(first..last)
                    }
                }
            }
            if (episodesBySeason.isNotEmpty()) {
                watchlist.savePlayableEpisodes(title.type, title.id, "torrent_$topicId",
                    episodesBySeason)
                Log.i("WatchlistPlayback", "Rebuilt playlist episode index from TorrServer " +
                    "tmdb=${title.type.path}:${title.id} topic=$topicId " +
                    "seasons=${episodesBySeason.mapValues { it.value.size }}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("WatchlistPlayback", "Could not rebuild playlist episode index " +
                "tmdb=${title.type.path}:${title.id} topic=$topicId: ${error.message}")
        }
    }

    private fun updateSelectedWatchlistSeason(title: TmdbTitle, seasonNumber: Int) {
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(selectedSeasonNumber = seasonNumber) else item
        })
    }

    private fun updateWatchlistSeasons(title: TmdbTitle, seasons: List<TmdbSeason>) {
        _state.value = _state.value.copy(watchlist = _state.value.watchlist.map { item ->
            if (item.entry.title.type == title.type && item.entry.title.id == title.id)
                item.copy(seasons = seasons) else item
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
            if (withContext(Dispatchers.IO) { localDataMigrator.migrate() }) {
                Log.i(TAG, "Migrated legacy AniList favorites and playback history into TMDB lists")
                refreshPersonalLists()
            }
        }
    }

    fun refreshPopular(type: TmdbMediaType) {
        if (popularJobs[type]?.isActive == true) return
        if ((if (type == TmdbMediaType.TV) _state.value.tv else _state.value.movies)
            !is TmdbPopularState.Results) setRow(type, TmdbPopularState.Loading)
        popularJobs[type] = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val section = if (type == TmdbMediaType.TV) "animation_tv" else "animation_movie"
            Log.i(TAG, "Home section load started section=$section source=TMDB " +
                "query=popular genreId=${if (animationOnly) 16 else "none"}")
            try {
                val connection = withContext(Dispatchers.IO) {
                    workerTmdbConnection(getApplication())
                }
                    ?: throw IllegalStateException("Налаштуйте інтеграцію TMDB у конфігу")
                val connectionMode = withContext(Dispatchers.IO) {
                    if (settings.isEncryptedConfigEnabled()) "direct_config" else "worker"
                }
                Log.d(TAG, "TMDB connection ready section=$section mode=$connectionMode")
                val titles = homeNetworkPermits.withPermit {
                    connection.client.popular(type, connection.accessKey,
                        genreId = if (animationOnly) 16 else null,
                        excludedGenreIds = if (animationOnly) emptySet() else
                            ua.any2b.core.tmdb.TmdbGeneralCatalog.excludedGenreIds)
                }
                setRow(type, TmdbPopularState.Results(titles))
                val cacheKey = if (animationOnly) section else
                    if (type == TmdbMediaType.TV) "general_tv" else "general_movie"
                withContext(Dispatchers.IO) { homeRows.save(cacheKey, titles) }
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
                if ((if (type == TmdbMediaType.TV) _state.value.tv else _state.value.movies)
                    !is TmdbPopularState.Results) setRow(type, TmdbPopularState.Error(message))
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
        const val TOLOKA_VISIBLE_REFRESH_COOLDOWN_MS = 30L * 60L * 1_000L
    }
}
