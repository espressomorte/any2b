package ua.any2b.tvplayer

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.core.toloka.TorrServerProbe
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaPlaybackStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistStore
import ua.any2b.tvplayer.data.TmdbPlaybackSelection
import ua.any2b.tvplayer.data.AnitubeState
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbDetailsScreen
import ua.any2b.tvplayer.ui.TmdbDetailsPreparingScreen
import ua.any2b.tvplayer.ui.TmdbDetailsViewModel
import ua.any2b.tvplayer.ui.TolokaSearchViewModel
import ua.any2b.tvplayer.ui.TolokaSearchState
import ua.any2b.tvplayer.ui.TmdbDetailsState
import ua.any2b.tvplayer.ui.TolokaTransferState
import ua.any2b.tvplayer.ui.tmdbTorrentQueryOptions
import ua.any2b.core.matcher.SeriesTitleSearch
import ua.any2b.core.toloka.TolokaReleaseMetadataParser
import ua.any2b.tvplayer.ui.FanartDayTheme
import ua.any2b.tvplayer.ui.FanartArtworkKey
import ua.any2b.tvplayer.ui.FanartArtworkState
import ua.any2b.tvplayer.ui.FanartArtworkViewModel
import ua.any2b.tvplayer.ui.WatchlistPlaybackScreen

class TmdbDetailsActivity : ComponentActivity() {
    companion object {
        private const val TORRSERVE_PACKAGE = "ru.yourok.torrserve"
        const val EXTRA_TMDB_ID = "TMDB_ID"
        const val EXTRA_TMDB_TYPE = "TMDB_TYPE"
        const val EXTRA_TMDB_SEASON = "TMDB_SEASON"
        const val EXTRA_SEARCH_QUERY = "SEARCH_QUERY"
        const val EXTRA_PREFERRED_TITLE = "PREFERRED_TITLE"
        const val EXTRA_ORIGINAL_LANGUAGE = "ORIGINAL_LANGUAGE"
        const val EXTRA_ORIGINAL_TITLE = "ORIGINAL_TITLE"
        const val EXTRA_TORRENTS_ONLY = "TORRENTS_ONLY"
        const val EXTRA_AUTO_PLAY_EPISODE = "AUTO_PLAY_EPISODE"
        const val EXTRA_AUTO_PLAY_SOURCE = "AUTO_PLAY_SOURCE"
        const val EXTRA_AUTO_PLAY_TORRENT_TOPIC = "AUTO_PLAY_TORRENT_TOPIC"
        const val EXTRA_AUTO_PLAY_TORRENT_FILE = "AUTO_PLAY_TORRENT_FILE"
        const val EXTRA_AUTO_PLAY_ANITUBE_RELEASE = "AUTO_PLAY_ANITUBE_RELEASE"
        const val EXTRA_AUTO_PLAY_ANITUBE_SEASON = "AUTO_PLAY_ANITUBE_SEASON"
        const val EXTRA_AUTO_PLAY_ANITUBE_VOICE = "AUTO_PLAY_ANITUBE_VOICE"
        const val EXTRA_AUTO_PLAY_ANITUBE_VOICE_NAME = "AUTO_PLAY_ANITUBE_VOICE_NAME"
        const val EXTRA_RETURN_TO_WATCHLIST = "RETURN_TO_WATCHLIST"
        const val EXTRA_PLAYBACK_ONLY = "PLAYBACK_ONLY"
    }

    private val detailsViewModel: TmdbDetailsViewModel by viewModels()
    private val tolokaViewModel: TolokaSearchViewModel by viewModels()
    private val torrServeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { tolokaViewModel.retryTorrServer() }
    private val artworkViewModel: FanartArtworkViewModel by viewModels()
    private var launchingPlayer = false
    private val torrServerProbe = TorrServerProbe()
    private var returningFromAnitubePlayer = false
    private var openedTopics by mutableStateOf(emptySet<String>())
    private var openedFiles by mutableStateOf(emptySet<Int>())
    private var selectedTopicId: String? = null
    private var tmdbId = -1
    private var tmdbType = ""
    private var autoPlaybackStarted = false
    private var autoPlaybackFailed = false
    private var autoReleaseRequested = false
    private var autoSavedReleaseLookupStarted = false
    private var autoTargetAniTubeReleaseId: Int? = null
    private var pendingVimuPlayback: PendingVimuPlayback? = null

    private data class PendingVimuPlayback(
        val title: ua.any2b.core.tmdb.TmdbTitle,
        val source: TmdbPlaybackSelection.Source,
        val topicId: String? = null,
        val fileId: Int? = null,
        val episodeNumber: Int? = null,
        val seasonNumber: Int? = null,
        val voiceIndex: Int = 0,
        val episodeIndex: Int = 0,
    )

    private val vimuPlayerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ::onVimuPlayerResult)

    override fun onSaveInstanceState(outState: Bundle) {
        pendingVimuPlayback?.let { pending ->
            outState.putBoolean("vimu_pending", true)
            outState.putInt("vimu_title_id", pending.title.id)
            outState.putString("vimu_title_type", pending.title.type.path)
            outState.putString("vimu_title_name", pending.title.title)
            outState.putString("vimu_original_title", pending.title.originalTitle)
            outState.putInt("vimu_year", pending.title.year ?: 0)
            outState.putString("vimu_poster", pending.title.posterUrl)
            outState.putDouble("vimu_rating", pending.title.rating ?: Double.NaN)
            outState.putIntArray("vimu_genres", pending.title.genreIds.toIntArray())
            outState.putString("vimu_source", pending.source.name)
            outState.putString("vimu_topic", pending.topicId)
            outState.putInt("vimu_file", pending.fileId ?: -1)
            outState.putInt("vimu_episode", pending.episodeNumber ?: -1)
            outState.putInt("vimu_season", pending.seasonNumber ?: -1)
            outState.putInt("vimu_voice", pending.voiceIndex)
            outState.putInt("vimu_episode_index", pending.episodeIndex)
        }
        super.onSaveInstanceState(outState)
    }

    private val autoPlayEpisode: Int
        get() = intent.getIntExtra(EXTRA_AUTO_PLAY_EPISODE, -1)
    private val autoPlaySeason: Int
        get() = intent.getIntExtra(EXTRA_TMDB_SEASON, 1).coerceAtLeast(1)
    private val autoPlaySource: String?
        get() = intent.getStringExtra(EXTRA_AUTO_PLAY_SOURCE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingVimuPlayback = savedInstanceState?.takeIf { it.getBoolean("vimu_pending") }
            ?.let { state ->
                val type = TmdbMediaType.entries.firstOrNull {
                    it.path == state.getString("vimu_title_type")
                }
                val source = TmdbPlaybackSelection.Source.entries.firstOrNull {
                    it.name == state.getString("vimu_source")
                }
                val id = state.getInt("vimu_title_id", -1)
                val name = state.getString("vimu_title_name").orEmpty()
                if (type == null || source == null || id <= 0 || name.isBlank()) null
                else PendingVimuPlayback(
                    ua.any2b.core.tmdb.TmdbTitle(id, type, name,
                        state.getString("vimu_original_title"),
                        state.getInt("vimu_year", 0).takeIf { it > 0 },
                        state.getString("vimu_poster"), null,
                        state.getDouble("vimu_rating", Double.NaN).takeIf(Double::isFinite),
                        genreIds = state.getIntArray("vimu_genres")?.toSet().orEmpty()),
                    source, state.getString("vimu_topic"),
                    state.getInt("vimu_file", -1).takeIf { it > 0 },
                    state.getInt("vimu_episode", -1).takeIf { it > 0 },
                    state.getInt("vimu_season", -1).takeIf { it > 0 },
                    state.getInt("vimu_voice", 0), state.getInt("vimu_episode_index", 0))
            }
        val id = intent.getIntExtra(EXTRA_TMDB_ID, -1)
        val type = TmdbMediaType.entries.firstOrNull { it.path == intent.getStringExtra(EXTRA_TMDB_TYPE) }
        if (id <= 0 || type == null) {
            finish()
            return
        }
        tmdbId = id
        tmdbType = type.path
        val query = intent.getStringExtra(EXTRA_SEARCH_QUERY).orEmpty()
        val preferredTitle = intent.getStringExtra(EXTRA_PREFERRED_TITLE)
        val originalLanguage = intent.getStringExtra(EXTRA_ORIGINAL_LANGUAGE)
        val originalTitle = intent.getStringExtra(EXTRA_ORIGINAL_TITLE)
        val seasonNumber = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it >= 0 }
        val torrentsOnly = intent.getBooleanExtra(EXTRA_TORRENTS_ONLY, false)
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_WATCHLIST, false)) {
            Log.i("WatchlistPlayback", "Playback resolver started tmdb=$tmdbType:$tmdbId " +
                "source=$autoPlaySource season=${intent.getIntExtra(EXTRA_TMDB_SEASON, -1)} " +
                "episode=$autoPlayEpisode release=" +
                "${intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_RELEASE, -1)} " +
                "voice=${intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_VOICE, 0)}")
        }
        if (autoPlaySource == TmdbPlaybackSelection.Source.TORRENT.name) {
            selectedTopicId = intent.getStringExtra(EXTRA_AUTO_PLAY_TORRENT_TOPIC)
            selectedTopicId?.let(tolokaViewModel::selectSavedTopic)
        }
        val experimental = SettingsManager(this).isExperimentalAppearanceEnabled()
        detailsViewModel.load(type, id, query, preferredTitle, originalLanguage, originalTitle,
            torrentsOnly = torrentsOnly, seasonNumber = seasonNumber)
        setContent {
            AppTheme {
                val artworkState = artworkViewModel.state.collectAsStateWithLifecycle().value
                val detailsState = detailsViewModel.state.collectAsStateWithLifecycle().value
                val anitubeState = detailsViewModel.anitubeState.collectAsStateWithLifecycle().value
                val aniListAvailability = detailsViewModel.aniListAvailability.collectAsStateWithLifecycle().value
                val tolokaState = tolokaViewModel.state.collectAsStateWithLifecycle().value
                val readyDetails = (detailsState as? TmdbDetailsState.Ready)?.details
                val tmdbArtwork = readyDetails?.let { details ->
                    val poster = details.title.posterUrl
                    val logo = details.logoUrl
                    if (poster != null && logo != null)
                        ua.any2b.core.fanart.FanartArtwork(poster, logo) else null
                }
                val artworkKey = readyDetails?.let { FanartArtworkKey(type, id) }
                LaunchedEffect(experimental, artworkKey, tmdbArtwork) {
                    if (experimental && tmdbArtwork != null)
                        artworkViewModel.loadTmdbArtwork(type, id,
                            tmdbArtwork.posterUrl, tmdbArtwork.logoUrl)
                }
                val readyArtwork = (artworkState as? FanartArtworkState.Ready)
                    ?.takeIf { it.key == artworkKey }
                val activePalette = if (experimental) readyArtwork?.palette else null
                val waitForArtwork = experimental && readyDetails != null &&
                    tmdbArtwork != null && readyArtwork == null
                LaunchedEffect(tolokaState.search) {
                    (tolokaState.search as? ua.any2b.tvplayer.ui.TolokaSearchState.Results)
                        ?.selectedId?.let { selectedTopicId = it }
                    refreshPlaybackMarks()
                }
                LaunchedEffect(detailsState, intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false)) {
                    if (intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false) &&
                        detailsState is TmdbDetailsState.Error) {
                        failWatchlistPlayback((detailsState as TmdbDetailsState.Error).message)
                    }
                }
                LaunchedEffect(tolokaState.transfer, readyDetails?.title?.id) {
                    val details = readyDetails ?: return@LaunchedEffect
                    val files = (tolokaState.transfer as? TolokaTransferState.Files)
                        ?.torrent?.videoFiles ?: return@LaunchedEffect
                    val topicId = files.firstOrNull()?.id
                        ?.let(tolokaViewModel::playbackTopicId) ?: return@LaunchedEffect
                    val progress = TmdbWatchHistoryStore(this@TmdbDetailsActivity)
                        .episodeProgress(details.title.type, details.title.id)
                    val fallbackSeason = progress?.seasonNumber
                        ?: details.title.matchedSeasonNumber ?: seasonNumber ?: 1
                    val episodesBySeason = mutableMapOf<Int, MutableSet<Int>>()
                    files.forEach { file ->
                        val metadata = TolokaReleaseMetadataParser.parse(file.name)
                        val start = metadata.episodeStart ?: return@forEach
                        val end = metadata.episodeEnd ?: start
                        val absoluteRanges = TolokaReleaseMetadataParser
                            .mapAbsoluteEpisodes(metadata, details.seasonEntries)
                        if (absoluteRanges != null) {
                            absoluteRanges.forEach { range ->
                                episodesBySeason.getOrPut(range.seasonNumber) { mutableSetOf() }
                                    .addAll(range.episodeStart..range.episodeEnd)
                            }
                            return@forEach
                        }
                        val firstSeason = metadata.season
                        val targetSeasons = if (firstSeason != null) {
                            (firstSeason..(metadata.seasonEnd ?: firstSeason)).toList()
                        } else listOf(fallbackSeason)
                        targetSeasons.forEach { targetSeason ->
                            episodesBySeason.getOrPut(targetSeason) { mutableSetOf() }
                                .addAll(start..end)
                        }
                    }
                    if (episodesBySeason.isNotEmpty()) TmdbWatchlistStore(this@TmdbDetailsActivity)
                        .savePlayableEpisodes(details.title.type, details.title.id,
                            "torrent_$topicId", episodesBySeason)
                }
                LaunchedEffect(anitubeState, readyDetails?.title?.id) {
                    val details = readyDetails ?: return@LaunchedEffect
                    val found = anitubeState as? AnitubeState.Found ?: return@LaunchedEffect
                    val targetSeason = found.catalog?.candidates?.firstOrNull {
                        it.release.newsId == found.release.newsId
                    }?.seasonNumber ?: details.title.matchedSeasonNumber
                        ?: TmdbWatchHistoryStore(this@TmdbDetailsActivity)
                            .episodeProgress(details.title.type, details.title.id)?.seasonNumber
                        ?: seasonNumber ?: return@LaunchedEffect
                    val store = TmdbWatchlistStore(this@TmdbDetailsActivity)
                    found.voices.forEachIndexed { voiceIndex, voice ->
                        store.savePlayableEpisodes(details.title.type, details.title.id,
                            "anitube_${found.release.newsId}_$voiceIndex",
                            mapOf(targetSeason to voice.episodes.map {
                                it.episodeNumber
                            }.toSet()))
                    }
                }
                LaunchedEffect(anitubeState, tolokaState.transfer, autoPlayEpisode,
                    autoPlaySource, autoPlaySeason,
                    intent.getIntExtra(EXTRA_AUTO_PLAY_TORRENT_FILE, -1),
                    readyDetails?.title?.id) {
                    val savedFileId = intent.getIntExtra(EXTRA_AUTO_PLAY_TORRENT_FILE, -1)
                        .takeIf { it > 0 }
                    if (autoPlaySource == TmdbPlaybackSelection.Source.TORRENT.name &&
                        intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false) &&
                        tolokaState.transfer is TolokaTransferState.Error &&
                        !(tolokaState.transfer as TolokaTransferState.Error).torrServerUnavailable) {
                        failWatchlistPlayback((tolokaState.transfer as TolokaTransferState.Error).message)
                        return@LaunchedEffect
                    }
                    if (autoPlaybackStarted || (autoPlayEpisode <= 0 && savedFileId == null) ||
                        readyDetails == null)
                        return@LaunchedEffect
                    when (autoPlaySource) {
                        TmdbPlaybackSelection.Source.TORRENT.name -> {
                            if (tolokaState.transfer is TolokaTransferState.Files) {
                                val fileId = savedFileId?.takeIf {
                                    tolokaViewModel.playbackUrl(it) != null
                                } ?: autoPlayEpisode.takeIf { it > 0 }?.let {
                                    tolokaViewModel.playbackFileId(autoPlaySeason, it)
                                }
                                if (fileId != null) {
                                    autoPlaybackStarted = true
                                    openFile(fileId)
                                } else if (!autoPlaybackFailed) {
                                    failWatchlistPlayback("У вибраному torrent не знайдено потрібний відеофайл")
                                }
                            }
                        }
                        TmdbPlaybackSelection.Source.ANITUBE.name ->
                            continueWatchlistAnitubeEpisode(anitubeState)
                    }
                }
                LaunchedEffect(readyDetails?.title?.type, readyDetails?.title?.id,
                    readyDetails?.title?.englishTitle) {
                    val details = readyDetails ?: return@LaunchedEffect
                    if (tolokaViewModel.state.value.search !is TolokaSearchState.Idle) {
                        tolokaViewModel.updateAutomaticDetails(details)
                        return@LaunchedEffect
                    }
                    val initial = tmdbTorrentQueryOptions(details).firstOrNull()?.second
                        ?: details.title.title
                    val english = (details.title.englishTitle
                        ?: details.title.originalTitle?.takeIf {
                            details.title.originalLanguage == "en"
                        })?.let(SeriesTitleSearch::clean)
                    tolokaViewModel.searchAutomatically(details, initial, english)
                }
                FanartDayTheme(activePalette?.light == true) {
                if (intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false)) {
                    WatchlistPlaybackScreen(
                        transfer = tolokaState.transfer,
                        title = readyDetails?.title?.title
                            ?: intent.getStringExtra(EXTRA_PREFERRED_TITLE).orEmpty(),
                        logoUrl = readyDetails?.logoUrl,
                        onRetryTorrServer = tolokaViewModel::retryTorrServer,
                        onOpenTorrServe = ::openTorrServe,
                    )
                } else if (waitForArtwork) {
                    TmdbDetailsPreparingScreen()
                } else {
                AnimatedVisibility(visible = true, enter = fadeIn(tween(220))) {
                TmdbDetailsScreen(
                    state = detailsState,
                    torrentsOnly = torrentsOnly,
                    experimentalPalette = activePalette,
                    experimentalArtwork = if (experimental) readyArtwork?.artwork else null,
                    fanartNotice = null,
                    toloka = tolokaState,
                    anitube = anitubeState,
                    aniListAvailability = aniListAvailability,
                    openedTopics = openedTopics,
                    openedFiles = openedFiles,
                    onRetryDetails = {
                        detailsViewModel.load(type, id, query, preferredTitle,
                            originalLanguage, originalTitle, torrentsOnly = torrentsOnly,
                            seasonNumber = seasonNumber, force = true)
                    },
                    onSearchTorrent = { query ->
                        val details = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details
                        if (details != null) tolokaViewModel.searchForTmdb(details, query)
                    },
                    onSelectTorrent = { topicId ->
                        selectedTopicId = topicId
                        refreshPlaybackMarks()
                        tolokaViewModel.selectHit(topicId)
                    },
                    onPromoteTorrent = tolokaViewModel::promoteHit,
                    onDiscardTorrent = tolokaViewModel::discardWeakHit,
                    onRejectTorrent = tolokaViewModel::rejectHit,
                    onRetryMetadata = tolokaViewModel::retryMetadata,
                    onRetryTorrServer = tolokaViewModel::retryTorrServer,
                    onOpenTorrServe = ::openTorrServe,
                    onPlayFile = ::openFile,
                    onPanelSelected = detailsViewModel::setPanel,
                    onRetryAnitube = detailsViewModel::retryAnitube,
                    onSelectAnitubeRelease = detailsViewModel::selectAnitubeRelease,
                    onSelectAnitubeSeason = detailsViewModel::selectAnitubeSeason,
                    onSelectAnitubeVoice = detailsViewModel::selectAnitubeVoice,
                    onSelectAnitubeEpisode = detailsViewModel::selectAnitubeEpisode,
                    onPlayAnitube = ::playAnitube,
                    onToggleFavorite = detailsViewModel::toggleFavorite,
                    onToggleWatchlist = detailsViewModel::toggleWatchlist,
                    onTrailer = {
                        val details = (detailsViewModel.state.value as?
                            ua.any2b.tvplayer.ui.TmdbDetailsState.Ready)?.details
                        if (details != null) {
                            startActivity(Intent(this@TmdbDetailsActivity, TrailerActivity::class.java).apply {
                                putExtra(TrailerActivity.EXTRA_TYPE, details.title.type.path)
                                putExtra(TrailerActivity.EXTRA_ID, details.title.id)
                                putExtra(TrailerActivity.EXTRA_TITLE, details.title.originalTitle
                                    ?: details.title.title)
                                putExtra(TrailerActivity.EXTRA_YEAR, details.title.year ?: 0)
                                details.title.matchedSeasonNumber?.let {
                                    putExtra(TrailerActivity.EXTRA_SEASON, it)
                                }
                            })
                        }
                    },
                    onRetryRelated = detailsViewModel::retryRelated,
                    onOpenRelated = { related ->
                        startActivity(TvNavigation.details(this@TmdbDetailsActivity, related,
                            torrentsOnly = torrentsOnly))
                    },
                    onBack = ::handleDetailsBack,
                )
                }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchingPlayer = false
        refreshPlaybackMarks()
        if (returningFromAnitubePlayer) {
            returningFromAnitubePlayer = false
            val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
            if (title != null) {
                val progress = TmdbWatchHistoryStore(this).episodeProgress(title.type, title.id)
                detailsViewModel.restoreAnitubeProgress(progress)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        Log.i("WatchlistPlayback", "Playback flow cancelled by system back tmdb=$tmdbType:$tmdbId " +
            "autoplayStarted=$autoPlaybackStarted")
        cancelWatchlistAutoplay()
        super.onBackPressed()
    }

    private fun handleDetailsBack() {
        Log.i("WatchlistPlayback", "Playback flow cancelled by back tmdb=$tmdbType:$tmdbId " +
            "autoplayStarted=$autoPlaybackStarted")
        cancelWatchlistAutoplay()
        finish()
    }

    private fun cancelWatchlistAutoplay() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_WATCHLIST, false))
            autoPlaybackFailed = true
    }

    private fun refreshPlaybackMarks() {
        val store = TolokaPlaybackStore(this)
        val results = tolokaViewModel.state.value.search as? TolokaSearchState.Results
        openedTopics = (results?.hits.orEmpty() + results?.weakHits.orEmpty())
            .mapNotNull { hit ->
                hit.id.takeIf { store.openedTmdbFileIds(tmdbType, tmdbId, it).isNotEmpty() }
            }.toSet()
        openedFiles = selectedTopicId?.let {
            store.openedTmdbFileIds(tmdbType, tmdbId, it)
        }.orEmpty()
    }

    private fun continueWatchlistAnitubeEpisode(state: AnitubeState) {
        if (autoPlaybackStarted || autoPlaybackFailed || autoPlayEpisode <= 0) return
        Log.i("WatchlistPlayback", "AniTube autoplay state=${state.javaClass.simpleName} " +
            "tmdb=$tmdbType:$tmdbId season=$autoPlaySeason episode=$autoPlayEpisode")
        val savedSeason = intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_SEASON, -1)
            .takeIf { it > 0 }
        val requestedRelease = intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_RELEASE, -1)
            .takeIf { it > 0 }
            ?.takeIf { savedSeason == null || savedSeason == autoPlaySeason }
        if (autoTargetAniTubeReleaseId == null) autoTargetAniTubeReleaseId = requestedRelease
        when (state) {
            is AnitubeState.Catalog -> {
                val seasonCandidate = if (tmdbType == TmdbMediaType.MOVIE.path) {
                    state.resolution.confidentBest
                } else state.resolution.candidates.firstOrNull {
                    it.seasonNumber == autoPlaySeason
                } ?: state.resolution.seasons.firstOrNull {
                    it.number == autoPlaySeason
                }?.releases?.firstOrNull()
                val candidate = autoTargetAniTubeReleaseId?.let { releaseId ->
                    state.resolution.candidates.firstOrNull { it.release.newsId == releaseId }
                } ?: seasonCandidate
                if (candidate != null) {
                    autoTargetAniTubeReleaseId = candidate.release.newsId
                    autoReleaseRequested = true
                    detailsViewModel.selectAnitubeRelease(candidate.release)
                } else failWatchlistPlayback("Не знайдено реліз AniTube для цього сезону")
            }
            is AnitubeState.Found -> {
                if (autoTargetAniTubeReleaseId == null) {
                    autoTargetAniTubeReleaseId = if (tmdbType == TmdbMediaType.MOVIE.path) {
                        state.catalog?.confidentBest?.release?.newsId
                    } else state.catalog?.candidates?.firstOrNull {
                        it.seasonNumber == autoPlaySeason
                    }?.release?.newsId
                }
                val targetReleaseId = autoTargetAniTubeReleaseId
                val currentSeason = state.catalog?.candidates?.firstOrNull {
                    it.release.newsId == state.release.newsId
                }?.seasonNumber
                if (tmdbType != TmdbMediaType.MOVIE.path && targetReleaseId == null && currentSeason != null &&
                    currentSeason != autoPlaySeason) {
                    failWatchlistPlayback("Не знайдено реліз AniTube для цього сезону")
                    return
                }
                if (targetReleaseId != null && state.release.newsId != targetReleaseId) {
                    val candidate = state.catalog?.candidates?.firstOrNull {
                        it.release.newsId == targetReleaseId
                    }
                    if (candidate != null && !autoReleaseRequested) {
                        autoReleaseRequested = true
                        detailsViewModel.selectAnitubeRelease(candidate.release)
                    } else if (candidate == null) {
                        failWatchlistPlayback("Збережений реліз AniTube більше не знайдено")
                    }
                    return
                }
                val savedVoiceName = intent.getStringExtra(EXTRA_AUTO_PLAY_ANITUBE_VOICE_NAME)
                val voiceIndex = if (savedVoiceName != null) {
                    state.voices.indexOfFirst { it.name.equals(savedVoiceName, ignoreCase = true) }
                } else intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_VOICE, 0)
                if (voiceIndex !in state.voices.indices) {
                    failWatchlistPlayback("Збережену озвучку AniTube більше не знайдено")
                    return
                }
                detailsViewModel.selectAnitubeVoice(voiceIndex)
                val selected = detailsViewModel.anitubeState.value as? AnitubeState.Found
                    ?: return
                val episodeIndex = selected.voices[voiceIndex].episodes.indexOfFirst {
                    it.episodeNumber == autoPlayEpisode
                }.takeIf { it >= 0 } ?: if (tmdbType == TmdbMediaType.MOVIE.path)
                    selected.voices[voiceIndex].episodes.indices.firstOrNull() ?: -1 else -1
                if (episodeIndex < 0) {
                    failWatchlistPlayback("У вибраній озвучці не знайдено цю серію")
                    return
                }
                detailsViewModel.selectAnitubeEpisode(episodeIndex)
                val tmdbTitle = (detailsViewModel.state.value as? TmdbDetailsState.Ready)
                    ?.details?.title ?: return
                val savedProgress = TmdbWatchHistoryStore(this).episodeProgress(
                    tmdbTitle.type, tmdbTitle.id)
                if (savedProgress?.voiceIndex == voiceIndex &&
                    savedProgress.episodeNumber == autoPlayEpisode &&
                    savedProgress.seasonNumber == autoPlaySeason) {
                    detailsViewModel.restoreAnitubeProgress(savedProgress)
                }
                autoPlaybackStarted = true
                playAnitube()
            }
            is AnitubeState.NotFound -> failWatchlistPlayback("Не знайдено відео на AniTube")
            is AnitubeState.Error -> {
                val savedReleaseId = autoTargetAniTubeReleaseId
                    ?: intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_RELEASE, -1)
                        .takeIf { it > 0 }
                val details = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details
                if (savedReleaseId != null && details != null &&
                    !autoSavedReleaseLookupStarted) {
                    autoSavedReleaseLookupStarted = true
                    Log.w("WatchlistPlayback", "AniList lookup failed; loading saved AniTube release directly " +
                        "tmdb=${details.title.type.path}:${details.title.id} release=$savedReleaseId")
                    detailsViewModel.loadSavedAnitubeRelease(savedReleaseId,
                        savedSeason ?: autoPlaySeason,
                        listOfNotNull(details.title.ukrainianTitle, details.title.title,
                            details.title.englishTitle, details.title.originalTitle),
                        if (details.title.type == TmdbMediaType.MOVIE) "MOVIE" else "TV")
                } else {
                    Log.e("WatchlistPlayback", "AniTube autoplay failed: ${state.message}")
                    failWatchlistPlayback(state.message)
                }
            }
            AnitubeState.Idle, AnitubeState.Searching -> Unit
        }
    }

    private fun failWatchlistPlayback(message: String) {
        if (autoPlaybackFailed) return
        autoPlaybackFailed = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false)) finish()
    }

    private fun openTorrServe() {
        val launchIntent = packageManager.getLaunchIntentForPackage(TORRSERVE_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(this,
                "Застосунок TorrServe не знайдено. Відкрийте його вручну й запустіть сервер.",
                Toast.LENGTH_LONG).show()
            return
        }
        try {
            torrServeLauncher.launch(launchIntent)
        } catch (error: Exception) {
            Log.w("TorrentPlayback", "Could not open TorrServe", error)
            Toast.makeText(this, "Не вдалося відкрити TorrServe", Toast.LENGTH_LONG).show()
        }
    }

    private fun openFile(fileId: Int) {
        if (launchingPlayer) return
        val topicId = tolokaViewModel.playbackTopicId(fileId)
        val url = tolokaViewModel.playbackUrl(fileId)
        if (topicId == null || url == null) {
            tolokaViewModel.reportPlaybackStatus("Не вдалося сформувати URL потоку для вибраного файлу")
            Toast.makeText(this, "Відеофайл недоступний. Повторіть вибір роздачі", Toast.LENGTH_LONG).show()
            return
        }
        launchingPlayer = true
        tolokaViewModel.reportPlaybackStatus("Перевіряємо доступність TorrServer…")
        lifecycleScope.launch {
            try {
                val credentials = TorrServerAuthStore(this@TmdbDetailsActivity).read()
                torrServerProbe.version(
                    SettingsManager(this@TmdbDetailsActivity).torrServerUrl(),
                    credentials?.let { TorrServerBasicAuth(it.username, it.password) },
                )
                if (!isFinishing && !isDestroyed) launchFilePlayback(fileId, url, topicId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                launchingPlayer = false
                if (isFinishing || isDestroyed) return@launch
                val message = when {
                    error is IllegalArgumentException -> "Перевірте адресу TorrServer у налаштуваннях."
                    error is IllegalStateException && error.message?.contains("авторизац", ignoreCase = true) == true ->
                        "TorrServer відхилив авторизацію (HTTP 401). Перевірте логін і пароль."
                    error is IllegalStateException -> error.message ?: "TorrServer повернув помилку."
                    error is java.io.InterruptedIOException -> "TorrServer не відповів за 8 секунд. Перевірте, чи сервер запущений."
                    error is java.net.ConnectException -> "Не вдалося встановити з’єднання з TorrServer. Перевірте адресу та чи запущений сервер."
                    else -> "Не вдалося з’єднатися з TorrServer. Перевірте, чи сервер запущений і доступний за цією адресою."
                }
                tolokaViewModel.reportPlaybackStatus("Перевірка TorrServer не пройшла: $message")
                showTorrServerUnavailable(fileId, message)
            }
        }
    }

    private fun showTorrServerUnavailable(fileId: Int, message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("TorrServer недоступний")
            .setMessage(message)
            .setPositiveButton("Повторити") { _, _ -> openFile(fileId) }
            .setNeutralButton("Налаштування") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Назад", null)
            .show()
    }

    private fun launchFilePlayback(fileId: Int, url: String, topicId: String) {
        val fileName = tolokaViewModel.playbackFileName(fileId)
        tolokaViewModel.reportPlaybackStatus("URL потоку сформовано для індексу $fileId. Відкриваємо плеєр…")
        val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
        val selectedPackage = SettingsManager(this).selectedPlayerPackage()
        val serverRequiresAuth = TorrServerAuthStore(this).read() != null
        if (selectedPackage != null && !serverRequiresAuth) {
            val externalIntent = ExternalVideoPlayers(this).intentForVideo(url, selectedPackage)
            if (externalIntent != null) {
                try {
                    val history = TmdbWatchHistoryStore(this)
                    TolokaPlaybackStore(this).markTmdbOpened(tmdbType, tmdbId, topicId, fileId)
                    title?.let {
                        history.record(it)
                        history.savePlaybackSelection(it.type, it.id,
                            TmdbPlaybackSelection(TmdbPlaybackSelection.Source.TORRENT,
                                topicId = topicId, fileId = fileId, fileName = fileName))
                        if (it.type == TmdbMediaType.TV) {
                            val episode = fileName?.let(TolokaReleaseMetadataParser::parse)
                                ?.takeIf { meta -> meta.episodeStart != null &&
                                    (meta.episodeEnd == null || meta.episodeEnd == meta.episodeStart) }
                            val episodeNumber = episode?.episodeStart
                            if (episodeNumber != null) {
                                history.saveEpisodeProgress(it, 0, episodeNumber - 1,
                                    episodeNumber, 0f,
                                    episode.season ?: it.matchedSeasonNumber ?: 1)
                            } else {
                                ua.any2b.tvplayer.data.TmdbWatchlistStore(this)
                                    .onPlaybackStarted(it)
                            }
                        }
                    }
                    val episode = fileName?.let(TolokaReleaseMetadataParser::parse)
                        ?.takeIf { meta -> meta.episodeStart != null &&
                            (meta.episodeEnd == null || meta.episodeEnd == meta.episodeStart) }
                    val torrentContext = title?.let {
                        PendingVimuPlayback(it, TmdbPlaybackSelection.Source.TORRENT,
                            topicId, fileId, episode?.episodeStart,
                            episode?.season ?: it.matchedSeasonNumber)
                    }
                    launchExternalPlayer(externalIntent, selectedPackage,
                            title?.let {
                                history.torrentProgress(it.type, it.id, topicId, fileId)?.positionMs
                            } ?: 0L, torrentContext)
                    refreshPlaybackMarks()
                    tolokaViewModel.reportPlaybackStatus("URL передано зовнішньому плеєру. Результат відтворення перевіряйте в ньому")
                    if (!ExternalVideoPlayers(this).isVimu(selectedPackage))
                        finishDetailsForWatchlistPlayback()
                    return
                } catch (_: ActivityNotFoundException) {
                    // The selected player may have been removed.
                } catch (_: SecurityException) {
                    // The selected player no longer accepts explicit launches.
                }
            }
            Toast.makeText(this, "Обраний плеєр недоступний. Відкриваємо вбудований", Toast.LENGTH_LONG).show()
        }
        if (selectedPackage != null && serverRequiresAuth) {
            Toast.makeText(this, "Захищений TorrServer відкриваємо у вбудованому плеєрі", Toast.LENGTH_LONG).show()
        }
        try {
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
                putExtra(PlayerActivity.EXTRA_SOURCE_TYPE, PlayerActivity.SOURCE_TOLOKA)
                putExtra(PlayerActivity.EXTRA_TOLOKA_TOPIC_ID, topicId)
                putExtra(PlayerActivity.EXTRA_TOLOKA_FILE_ID, fileId)
                fileName?.let { putExtra(PlayerActivity.EXTRA_TOLOKA_FILE_NAME, it) }
                title?.let {
                    val resumePosition = TmdbWatchHistoryStore(this@TmdbDetailsActivity)
                        .torrentProgress(it.type, it.id, topicId, fileId)?.positionMs ?: 0L
                    putExtra(PlayerActivity.EXTRA_TORRENT_RESUME_POSITION_MS, resumePosition)
                    putExtra(PlayerActivity.EXTRA_TMDB_ID, it.id)
                    putExtra(PlayerActivity.EXTRA_TMDB_TYPE, it.type.path)
                    putExtra(PlayerActivity.EXTRA_TMDB_TITLE, it.title)
                    (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.logoUrl
                        ?.let { logo -> putExtra(PlayerActivity.EXTRA_TMDB_LOGO_URL, logo) }
                    putExtra(PlayerActivity.EXTRA_TMDB_ORIGINAL_TITLE, it.originalTitle)
                    putExtra(PlayerActivity.EXTRA_TMDB_YEAR, it.year ?: 0)
                    putExtra(PlayerActivity.EXTRA_TMDB_POSTER_URL, it.posterUrl)
                    putExtra(PlayerActivity.EXTRA_TMDB_RATING, it.rating ?: Double.NaN)
                    putExtra(PlayerActivity.EXTRA_TMDB_GENRE_IDS, it.genreIds.toIntArray())
                    fileName?.let { name ->
                        val release = TolokaReleaseMetadataParser.parse(name)
                        release.episodeStart?.let {
                            putExtra(PlayerActivity.EXTRA_TMDB_EPISODE, it)
                            if (it == release.episodeEnd || release.episodeEnd == null) {
                                val season = release.season ?: title.matchedSeasonNumber
                                val nextFileId = season?.let { seasonNumber ->
                                    tolokaViewModel.playbackFileId(seasonNumber, it + 1)
                                }
                                nextFileId?.let(tolokaViewModel::playbackUrl)?.let { nextUrl ->
                                    putExtra(PlayerActivity.EXTRA_NEXT_STREAM_URL, nextUrl)
                                }
                            }
                        }
                        (release.season ?: it.matchedSeasonNumber)?.let { season ->
                            putExtra(PlayerActivity.EXTRA_TMDB_SEASON, season)
                        }
                    }
                    if (!hasExtra(PlayerActivity.EXTRA_TMDB_SEASON)) it.matchedSeasonNumber?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_SEASON, it)
                    }
                }
            })
            finishDetailsForWatchlistPlayback()
            tolokaViewModel.reportPlaybackStatus("URL передано вбудованому плеєру")
        } catch (_: Exception) {
            launchingPlayer = false
            tolokaViewModel.reportPlaybackStatus("Не вдалося запустити плеєр після формування URL потоку")
            Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
        }
    }

    private fun playAnitube() {
        if (launchingPlayer) return
        val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
            ?: return
        detailsViewModel.playAnitube(
            onError = { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() },
            onSuccess = { url, voiceIndex, episodeIndex, episodeNumber, resumeProgress, backups ->
                if (launchingPlayer || autoPlaybackFailed || isFinishing || isDestroyed)
                    return@playAnitube
                Log.i("WatchlistPlayback", "AniTube playback launch tmdb=$tmdbType:$tmdbId " +
                    "season=$autoPlaySeason episode=$episodeNumber voice=$voiceIndex " +
                    "resumeProgress=$resumeProgress")
                launchingPlayer = true
                val selectedRelease = (detailsViewModel.anitubeState.value as?
                    ua.any2b.tvplayer.data.AnitubeState.Found)?.release?.newsId
                val found = detailsViewModel.anitubeState.value as?
                    ua.any2b.tvplayer.data.AnitubeState.Found
                val selectedSeason = found?.catalog?.candidates?.firstOrNull {
                    it.release.newsId == selectedRelease
                }?.seasonNumber ?: title.matchedSeasonNumber
                TmdbWatchHistoryStore(this).savePlaybackSelection(title.type, title.id,
                    TmdbPlaybackSelection(TmdbPlaybackSelection.Source.ANITUBE,
                        anitubeReleaseId = selectedRelease,
                        anitubeSeasonNumber = selectedSeason,
                        voiceIndex = voiceIndex,
                        voiceName = found?.voices?.getOrNull(voiceIndex)?.name))
                val selectedPackage = SettingsManager(this).selectedPlayerPackage()
                if (selectedPackage != null) {
                    val externalIntent = ExternalVideoPlayers(this).intentFor(url, selectedPackage)
                    if (externalIntent != null) {
                        try {
                            val history = TmdbWatchHistoryStore(this)
                            val storedProgress = history.episodeProgress(title.type, title.id)
                                ?.takeIf { it.voiceIndex == voiceIndex &&
                                    it.episodeNumber == episodeNumber &&
                                    (selectedSeason == null || it.seasonNumber == selectedSeason) }
                            val positionMs = storedProgress?.positionMs?.takeIf { it > 0L }
                                ?: if (storedProgress != null && storedProgress.durationMs > 0L)
                                    (storedProgress.durationMs * resumeProgress).toLong() else 0L
                            val vimuContext = PendingVimuPlayback(title,
                                TmdbPlaybackSelection.Source.ANITUBE,
                                episodeNumber = episodeNumber, seasonNumber = selectedSeason,
                                voiceIndex = voiceIndex, episodeIndex = episodeIndex)
                            launchExternalPlayer(externalIntent, selectedPackage,
                                positionMs, vimuContext)
                            history.saveEpisodeProgress(title, voiceIndex, episodeIndex,
                                episodeNumber, resumeProgress, selectedSeason ?: 1,
                                storedProgress?.positionMs ?: 0L,
                                storedProgress?.durationMs ?: 0L)
                            if (!ExternalVideoPlayers(this).isVimu(selectedPackage))
                                finishDetailsForWatchlistPlayback()
                            returningFromAnitubePlayer = true
                            return@playAnitube
                        } catch (_: ActivityNotFoundException) {
                            // The selected player may have been removed after discovery.
                        } catch (_: SecurityException) {
                            // The selected player no longer accepts explicit launches.
                        }
                    }
                    Toast.makeText(this, "Обраний плеєр недоступний. Відкриваємо вбудований",
                        Toast.LENGTH_LONG).show()
                }
                returningFromAnitubePlayer = true
                try { startActivity(Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_STREAM_URL, url)
                    putExtra(PlayerActivity.EXTRA_SOURCE_TYPE, PlayerActivity.SOURCE_ANITUBE_TMDB)
                    putExtra(PlayerActivity.EXTRA_VOICE_INDEX, voiceIndex)
                    putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, episodeIndex)
                    putExtra(PlayerActivity.EXTRA_EPISODE_NUMBER, episodeNumber)
                    putExtra(PlayerActivity.EXTRA_TMDB_ID, title.id)
                    putExtra(PlayerActivity.EXTRA_TMDB_TYPE, title.type.path)
                    putExtra(PlayerActivity.EXTRA_TMDB_TITLE, title.title)
                    (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.logoUrl
                        ?.let { logo -> putExtra(PlayerActivity.EXTRA_TMDB_LOGO_URL, logo) }
                    putExtra(PlayerActivity.EXTRA_TMDB_ORIGINAL_TITLE, title.originalTitle)
                    putExtra(PlayerActivity.EXTRA_TMDB_YEAR, title.year ?: 0)
                    putExtra(PlayerActivity.EXTRA_TMDB_POSTER_URL, title.posterUrl)
                    putExtra(PlayerActivity.EXTRA_TMDB_RATING, title.rating ?: Double.NaN)
                    putExtra(PlayerActivity.EXTRA_TMDB_GENRE_IDS, title.genreIds.toIntArray())
                    putExtra(PlayerActivity.EXTRA_TMDB_RESUME_PROGRESS, resumeProgress)
                    (selectedSeason ?: title.matchedSeasonNumber)?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_SEASON, it)
                    }
                    putExtra(PlayerActivity.EXTRA_TMDB_EPISODE, episodeNumber)
                    putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_AVAILABLE,
                        found?.voices?.getOrNull(voiceIndex)?.episodes?.any {
                            it.episodeNumber == episodeNumber + 1
                        } == true)
                    selectedRelease?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_ANITUBE_RELEASE, it)
                    }
                    found?.voices?.getOrNull(voiceIndex)?.name?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_ANITUBE_VOICE_NAME, it)
                    }
                    putStringArrayListExtra(PlayerActivity.EXTRA_BACKUP_EMBED_URLS,
                        ArrayList(backups.map { it.embedUrl }))
                    putExtra(PlayerActivity.EXTRA_BACKUP_VOICE_INDICES,
                        backups.map { it.voiceIndex }.toIntArray())
                    putExtra(PlayerActivity.EXTRA_BACKUP_EPISODE_INDICES,
                        backups.map { it.episodeIndex }.toIntArray())
                }) } catch (_: Exception) {
                    launchingPlayer = false
                    returningFromAnitubePlayer = false
                    Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
                }
                if (launchingPlayer) finishDetailsForWatchlistPlayback()
            },
        )
    }

    private fun finishDetailsForWatchlistPlayback() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_WATCHLIST, false)) finish()
    }

    private fun launchExternalPlayer(
        playerIntent: Intent,
        packageName: String,
        resumePositionMs: Long,
        context: PendingVimuPlayback?,
    ) {
        val players = ExternalVideoPlayers(this)
        if (players.isVimu(packageName)) {
            players.applyVimuResume(playerIntent, resumePositionMs)
            context?.title?.let { ua.any2b.tvplayer.data.TmdbWatchlistStore(this)
                .onPlaybackStarted(it) }
            pendingVimuPlayback = context
            vimuPlayerLauncher.launch(playerIntent)
        } else {
            startActivity(playerIntent)
        }
    }

    private fun onVimuPlayerResult(result: ActivityResult) {
        launchingPlayer = false
        returningFromAnitubePlayer = false
        val pending = pendingVimuPlayback ?: run {
            finishDetailsForWatchlistPlayback()
            return
        }
        pendingVimuPlayback = null
        val history = TmdbWatchHistoryStore(this)
        when (result.resultCode) {
            0 -> {
                val data = result.data ?: run {
                    finishDetailsForWatchlistPlayback()
                    return
                }
                val position = data.getIntExtra("position", -1).toLong()
                val duration = data.getIntExtra("duration", -1).toLong()
                if (position < 0L || duration <= 0L) {
                    finishDetailsForWatchlistPlayback()
                    return
                }
                if (pending.source == TmdbPlaybackSelection.Source.TORRENT) {
                    val topicId = pending.topicId ?: run {
                        finishDetailsForWatchlistPlayback()
                        return
                    }
                    val fileId = pending.fileId ?: run {
                        finishDetailsForWatchlistPlayback()
                        return
                    }
                    history.saveTorrentProgress(pending.title, topicId, fileId,
                        position, duration, pending.episodeNumber, pending.seasonNumber)
                } else {
                    history.saveEpisodeProgress(pending.title, pending.voiceIndex,
                        pending.episodeIndex, pending.episodeNumber ?: 1,
                        (position.toFloat() / duration).coerceIn(0f, 1f),
                        pending.seasonNumber ?: 1, position, duration)
                }
            }
            1 -> {
                if (pending.source == TmdbPlaybackSelection.Source.TORRENT) {
                    pending.topicId?.let { topic -> pending.fileId?.let { file ->
                        history.completeTorrentPlayback(pending.title, topic, file,
                            pending.episodeNumber, pending.seasonNumber)
                    } }
                } else {
                    history.saveEpisodeProgress(pending.title, pending.voiceIndex,
                        pending.episodeIndex, pending.episodeNumber ?: 1, 1f,
                        pending.seasonNumber ?: 1)
                }
            }
            else -> Unit // Vimu reports playback errors with result code 4.
        }
        finishDetailsForWatchlistPlayback()
    }
}
