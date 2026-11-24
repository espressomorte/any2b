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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.core.toloka.TorrServerProbe
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaPlaybackStore
import ua.any2b.tvplayer.data.PlaybackDependencies
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistStore
import ua.any2b.tvplayer.data.TmdbPlaybackSelection
import ua.any2b.tvplayer.data.AnitubeState
import ua.any2b.domain.playback.PlaybackRequest
import ua.any2b.domain.playback.AutoPlaybackRequest
import ua.any2b.domain.playback.PlaybackHostEffect
import ua.any2b.domain.playback.PlaybackPreparationRequest
import ua.any2b.domain.playback.PlaybackSource
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
        const val EXTRA_ANI_LIST_ID = "ANI_LIST_ID"
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
        const val EXTRA_AUTO_PLAY_ANITUBE_PAGE_URL = "AUTO_PLAY_ANITUBE_PAGE_URL"
        const val EXTRA_AUTO_PLAY_ANITUBE_SEASON = "AUTO_PLAY_ANITUBE_SEASON"
        const val EXTRA_AUTO_PLAY_ANITUBE_VOICE = "AUTO_PLAY_ANITUBE_VOICE"
        const val EXTRA_AUTO_PLAY_ANITUBE_VOICE_NAME = "AUTO_PLAY_ANITUBE_VOICE_NAME"
        const val EXTRA_RETURN_TO_WATCHLIST = "RETURN_TO_WATCHLIST"
        const val EXTRA_PLAYBACK_ONLY = "PLAYBACK_ONLY"
        const val EXTRA_SPECIAL_AUTO_PLAY = "SPECIAL_AUTO_PLAY"
    }

    private val detailsViewModel: TmdbDetailsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                require(modelClass.isAssignableFrom(TmdbDetailsViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return TmdbDetailsViewModel(application, extras.createSavedStateHandle(),
                    PlaybackDependencies(application)) as T
            }
        }
    }
    private val tolokaViewModel: TolokaSearchViewModel by viewModels()
    private val torrServeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        // Returning from TorrServe, including with Back, must not restart the torrent upload.
        // The user can explicitly retry from the recovery screen.
    }
    private val artworkViewModel: FanartArtworkViewModel by viewModels()
    private val torrServerProbe = TorrServerProbe()
    private var openedTopics by mutableStateOf(emptySet<String>())
    private var openedFiles by mutableStateOf(emptySet<Int>())
    private var selectedTopicId: String? = null
    private var specialAutoStarted = false
    private var specialCandidates: List<ua.any2b.core.toloka.TolokaHit> = emptyList()
    private var specialAttempt = 0
    private var specialTargetSeason = 1
    private var specialTargetEpisode = 1
    private var tmdbId = -1
    private var tmdbType = ""
    private val playbackScenarioViewModel: PlaybackScenarioViewModel by viewModels()
    private val playbackCoordinator get() = playbackScenarioViewModel.coordinator
    private val playbackSourcePorts get() = playbackScenarioViewModel.sourcePorts
    private val playbackScenarioId get() = playbackCoordinator.scenarioId
    private var pendingVimuPlayback: ExternalPlaybackContext? = null

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
        get() = intent.getIntExtra(EXTRA_AUTO_PLAY_EPISODE, -1).takeIf { it > 0 }
            ?: if (intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false) &&
                (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title?.type == TmdbMediaType.TV)
                specialTargetEpisode else -1
    private val autoPlaySeason: Int
        get() = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it > 0 }
            ?: if (intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false)) specialTargetSeason else 1
    private val autoPlaySource: String?
        get() = intent.getStringExtra(EXTRA_AUTO_PLAY_SOURCE)
            ?: TmdbPlaybackSelection.Source.TORRENT.name.takeIf {
                intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false) && selectedTopicId != null
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingVimuPlayback = savedInstanceState?.takeIf { it.getBoolean("vimu_pending") }
            ?.let { state ->
                val type = TmdbMediaType.entries.firstOrNull {
                    it.path == state.getString("vimu_title_type")
                }
                val source = PlaybackSource.entries.firstOrNull {
                    it.name == state.getString("vimu_source")
                }
                val id = state.getInt("vimu_title_id", -1)
                val name = state.getString("vimu_title_name").orEmpty()
                if (type == null || source == null || id <= 0 || name.isBlank()) null
                else ExternalPlaybackContext(
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
        if (playbackScenarioId == null) playbackCoordinator.begin()
        else playbackCoordinator.onHostRecreated()
        val query = intent.getStringExtra(EXTRA_SEARCH_QUERY).orEmpty()
        val preferredTitle = intent.getStringExtra(EXTRA_PREFERRED_TITLE)
        val originalLanguage = intent.getStringExtra(EXTRA_ORIGINAL_LANGUAGE)
        val originalTitle = intent.getStringExtra(EXTRA_ORIGINAL_TITLE)
        val seasonNumber = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it >= 0 }
        val aniListId = intent.getIntExtra(EXTRA_ANI_LIST_ID, -1).takeIf { it > 0 }
        val torrentsOnly = intent.getBooleanExtra(EXTRA_TORRENTS_ONLY, false)
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_WATCHLIST, false)) {
            Log.i("WatchlistPlayback", "Playback resolver started tmdb=$tmdbType:$tmdbId " +
                "source=$autoPlaySource season=${intent.getIntExtra(EXTRA_TMDB_SEASON, -1)} " +
                "episode=$autoPlayEpisode release=" +
                "${intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_RELEASE, -1)} " +
                "voice=${intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_VOICE, 0)}")
        }
        if (autoPlaySource == TmdbPlaybackSelection.Source.TORRENT.name ||
            intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false)) {
            selectedTopicId = intent.getStringExtra(EXTRA_AUTO_PLAY_TORRENT_TOPIC)
            selectedTopicId?.let(tolokaViewModel::selectSavedTopic)
        }
        if (intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false)) {
            val progress = TmdbWatchHistoryStore(this).episodeProgress(type, id)
            specialTargetSeason = progress?.seasonNumber?.coerceAtLeast(1) ?: 1
            specialTargetEpisode = progress?.let {
                it.episodeNumber + if (it.progress >= 0.95f) 1 else 0
            }?.coerceAtLeast(1) ?: 1
        }
        val experimental = SettingsManager(this).isExperimentalAppearanceEnabled()
        detailsViewModel.load(type, id, query, preferredTitle, originalLanguage, originalTitle,
            torrentsOnly = torrentsOnly, seasonNumber = seasonNumber, aniListId = aniListId)
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
                    if (intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false) && !specialAutoStarted) {
                        val results = tolokaState.search as? TolokaSearchState.Results
                        if (results != null) {
                            val candidates = rankSpecialCandidates(results.hits + results.weakHits,
                                readyDetails?.title, specialTargetSeason)
                            specialCandidates = candidates
                            specialAttempt = 0
                            val chosen = candidates.firstOrNull()
                            if (chosen != null) {
                                specialAutoStarted = true
                                tolokaViewModel.selectHit(chosen.id)
                                val metadata = TolokaReleaseMetadataParser.parse(chosen.title)
                                val detailsLine = (metadata.qualityTags + metadata.audioTags +
                                    metadata.subtitleTags.map { "$it субтитри" })
                                    .distinct().joinToString(" · ")
                                tolokaViewModel.reportPlaybackStatus(
                                    "Роздача: ${detailsLine.ifBlank { "перевіряємо доріжки" }}")
                            } else if (readyDetails != null) {
                                specialAutoStarted = true
                                openDetailsFallback(readyDetails.title)
                            }
                        } else if (tolokaState.search is TolokaSearchState.Error && readyDetails != null) {
                            specialAutoStarted = true
                            openDetailsFallback(readyDetails.title)
                        }
                    }
                    refreshPlaybackMarks()
                }
                LaunchedEffect(tolokaState.transfer, selectedTopicId) {
                    val transferError = tolokaState.transfer as? TolokaTransferState.Error
                    if (!intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false) ||
                        transferError == null || transferError.torrServerUnavailable)
                        return@LaunchedEffect
                    val next = specialCandidates.getOrNull(specialAttempt + 1)
                    if (next != null && specialAttempt < 2) {
                        specialAttempt++
                        specialAutoPlaybackLaunched = false
                        tolokaViewModel.selectHit(next.id)
                        val metadata = TolokaReleaseMetadataParser.parse(next.title)
                        tolokaViewModel.reportPlaybackStatus("Спроба ${specialAttempt + 1}/3 · " +
                            (metadata.qualityTags + metadata.audioTags).distinct().joinToString(" · "))
                    } else {
                        val title = (detailsState as? TmdbDetailsState.Ready)?.details?.title
                        if (title != null) openDetailsFallback(title)
                    }
                }
                LaunchedEffect(detailsState, intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false)) {
                    if (intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false) &&
                        detailsState is TmdbDetailsState.Error) {
                        playbackScenarioId?.let {
                            playbackCoordinator.dispatch(PlaybackScenarioEvent.Failed(it))
                        }
                        failWatchlistPlayback((detailsState as TmdbDetailsState.Error).message)
                    }
                }
                LaunchedEffect(readyDetails?.title?.id) {
                    if (readyDetails != null) playbackScenarioId?.let {
                        playbackCoordinator.dispatch(PlaybackScenarioEvent.DetailsReady(it))
                    }
                }
                LaunchedEffect(tolokaState.transfer, readyDetails?.title?.id) {
                    val details = readyDetails ?: return@LaunchedEffect
                    val files = (tolokaState.transfer as? TolokaTransferState.Files)
                        ?.torrent?.videoFiles ?: return@LaunchedEffect
                    if (intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false)) {
                        val targetFile = if (details.title.type == TmdbMediaType.MOVIE) {
                            files.filterNot { file ->
                                val name = file.name.lowercase()
                                listOf("sample", "trailer", "bonus").any(name::contains)
                            }.filter { it.length >= 0L }
                                .maxByOrNull { it.length }?.id
                        } else tolokaViewModel.playbackFileId(specialTargetSeason, specialTargetEpisode)
                            ?: files.firstOrNull { file ->
                                val parsed = TolokaReleaseMetadataParser.parse(file.name)
                                parsed.season == specialTargetSeason && parsed.episodeStart == specialTargetEpisode
                            }?.id
                        if (targetFile != null && !specialAutoPlaybackLaunched) {
                            specialAutoPlaybackLaunched = true
                            openFile(targetFile)
                        } else if (targetFile == null) openDetailsFallback(details.title)
                        return@LaunchedEffect
                    }
                    playbackScenarioId?.let {
                        playbackCoordinator.dispatch(PlaybackScenarioEvent.TorrentFilesReady(it))
                    }
                    val topicId = files.firstOrNull()?.id
                        ?.let(tolokaViewModel::playbackTopicId) ?: return@LaunchedEffect
                    val progress = TmdbWatchHistoryStore(this@TmdbDetailsActivity)
                        .episodeProgress(details.title.type, details.title.id)
                    val fallbackSeason = progress?.seasonNumber
                        ?: details.title.matchedSeasonNumber ?: seasonNumber ?: 1
                    val availabilityStore = TmdbWatchlistStore(this@TmdbDetailsActivity)
                    val searchResults = tolokaState.search as? TolokaSearchState.Results
                    val releaseTitle = (searchResults?.hits.orEmpty() + searchResults?.weakHits.orEmpty())
                        .firstOrNull { it.id == topicId }?.title
                    releaseTitle?.let { value ->
                        availabilityStore.saveTorrentUkrainian(topicId,
                            TolokaReleaseMetadataParser.parse(value).audioTags.any { it.contains("Ukr") })
                    }
                    val episodesBySeason = ua.any2b.core.toloka.TolokaEpisodeAvailability.index(
                        ua.any2b.core.toloka.TolokaEpisodeAvailability.ukrainianFiles(files,
                            availabilityStore.torrentUkrainian(topicId) == true), details.seasonEntries, fallbackSeason)
                        .mapValues { it.value.toMutableSet() }.toMutableMap()
                    if (availabilityStore.torrentUkrainian(topicId) != true) episodesBySeason.clear()
                    episodesBySeason.getOrPut(fallbackSeason) { mutableSetOf() }
                    availabilityStore.savePlayableEpisodes(details.title.type, details.title.id,
                            "torrent_$topicId", episodesBySeason)
                }
                LaunchedEffect(anitubeState, readyDetails?.title?.id) {
                    val details = readyDetails ?: return@LaunchedEffect
                    val found = anitubeState as? AnitubeState.Found ?: return@LaunchedEffect
                    val targetSeason = details.title.matchedSeasonNumber
                        ?: seasonNumber
                        ?: TmdbWatchHistoryStore(this@TmdbDetailsActivity)
                            .episodeProgress(details.title.type, details.title.id)?.seasonNumber
                        ?: 1
                    val store = TmdbWatchlistStore(this@TmdbDetailsActivity)
                    store.saveAniTubeVoices(details.title.type, details.title.id, targetSeason,
                        found.release.newsId,
                        ua.any2b.core.anitube.groupedVoices(found.voices).map { group ->
                            ua.any2b.tvplayer.data.AniTubeVoiceOption(group.category,
                                group.name, group.primaryIndex,
                                group.variants.map { variant -> variant.index })
                        })
                    found.voices.forEachIndexed { voiceIndex, voice ->
                        store.savePlayableEpisodes(details.title.type, details.title.id,
                            "anitube_${found.release.newsId}_$voiceIndex",
                            mapOf(targetSeason to voice.episodes.map {
                                it.episodeNumber
                            }.toSet()))
                        store.saveEpisodes(details.title.id, targetSeason, voice.episodes.map { episode ->
                            val episodeId = (episode.id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
                            TmdbEpisode(episodeId, targetSeason, episode.episodeNumber,
                                episode.title, null, null, null)
                        })
                    }
                }
                LaunchedEffect(anitubeState, tolokaState.transfer, autoPlayEpisode,
                    autoPlaySource, autoPlaySeason,
                    intent.getIntExtra(EXTRA_AUTO_PLAY_TORRENT_FILE, -1),
                    readyDetails?.title?.id) {
                    val savedFileId = intent.getIntExtra(EXTRA_AUTO_PLAY_TORRENT_FILE, -1)
                        .takeIf { it > 0 }
                    val source = TmdbPlaybackSelection.Source.entries.firstOrNull {
                        it.name == autoPlaySource
                    }
                    val playbackSource = when (source) {
                        TmdbPlaybackSelection.Source.TORRENT -> PlaybackSource.TORRENT
                        TmdbPlaybackSelection.Source.ANITUBE -> PlaybackSource.ANITUBE
                        null -> null
                    }
                    if (source == TmdbPlaybackSelection.Source.ANITUBE) playbackScenarioId?.let {
                        playbackCoordinator.dispatch(PlaybackScenarioEvent.AniTubeStateChanged(it))
                    }
                    if (source == TmdbPlaybackSelection.Source.TORRENT &&
                        tolokaState.transfer !is TolokaTransferState.Files) playbackScenarioId?.let {
                        playbackCoordinator.dispatch(PlaybackScenarioEvent.WaitingForTorrent(it))
                    }
                    if (source == TmdbPlaybackSelection.Source.TORRENT &&
                        intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false) &&
                        !intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false) &&
                        tolokaState.transfer is TolokaTransferState.Error &&
                        !(tolokaState.transfer as TolokaTransferState.Error).torrServerUnavailable) {
                        failWatchlistPlayback((tolokaState.transfer as TolokaTransferState.Error).message)
                        return@LaunchedEffect
                    }
                    val torrentFilesReady = tolokaState.transfer is TolokaTransferState.Files
                    val episodeFileId = autoPlayEpisode.takeIf { it > 0 }?.let {
                        tolokaViewModel.playbackFileId(autoPlaySeason, it)
                    }
                    when (val effect = playbackCoordinator.decideAutoPlayback(
                        request = AutoPlaybackRequest(playbackSource, autoPlayEpisode,
                            ua.any2b.domain.watchlist.WatchlistPolicy.savedFileForEpisode(savedFileId, autoPlayEpisode, episodeFileId)),
                        detailsReady = readyDetails != null,
                        torrentFilesReady = torrentFilesReady,
                        savedFileAvailable = savedFileId?.let {
                            tolokaViewModel.playbackUrl(it) != null
                        } == true,
                        episodeFileId = episodeFileId,
                        alreadyStarted = playbackCoordinator.launchPending || playbackCoordinator.phase in setOf(
                            PlaybackScenarioPhase.PLAYER_ACTIVE,
                            PlaybackScenarioPhase.RETURNING_FROM_PLAYER,
                            PlaybackScenarioPhase.COMPLETE),
                        failed = playbackCoordinator.phase == PlaybackScenarioPhase.FAILED,
                    )) {
                        is PlaybackHostEffect.PrepareSource -> when (val request = effect.request) {
                            PlaybackPreparationRequest.AniTubeEpisode ->
                                continueWatchlistAnitubeEpisode(anitubeState)
                            is PlaybackPreparationRequest.TolokaFile -> openFile(request.fileId)
                        }
                        is PlaybackHostEffect.LaunchPlayback -> Unit
                        is PlaybackHostEffect.ShowError -> {
                            Log.w("WatchlistPlayback", "Episode file unresolved " +
                                "tmdb=$tmdbType:$tmdbId season=$autoPlaySeason " +
                                "episode=$autoPlayEpisode files=" +
                                (tolokaState.transfer as? TolokaTransferState.Files)
                                    ?.torrent?.videoFiles?.take(20)?.joinToString { it.path })
                            failWatchlistPlayback(effect.message)
                        }
                        null, is PlaybackHostEffect.RetrySource, PlaybackHostEffect.Finish -> Unit
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
                        playbackStatus = tolokaState.playbackStatus,
                        logoUrl = readyDetails?.logoUrl,
                        onRetryTorrServer = tolokaViewModel::retryTorrServer,
                        onOpenTorrServe = ::openTorrServe,
                    )
                } else if (waitForArtwork) {
                    TmdbDetailsPreparingScreen(logoUrl = readyDetails?.logoUrl)
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
                            seasonNumber = seasonNumber, aniListId = aniListId, force = true)
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
        refreshPlaybackMarks()
        val returnedFromPlayer = if (playbackCoordinator.phase == PlaybackScenarioPhase.PLAYER_ACTIVE) {
            playbackScenarioId?.let {
                playbackCoordinator.playerReturned(it)
            } == true
        } else false
        if (returnedFromPlayer && playbackCoordinator.activeSource == PlaybackSource.ANITUBE) {
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
            "phase=${playbackCoordinator.phase}")
        cancelWatchlistAutoplay()
        super.onBackPressed()
    }

    private fun handleDetailsBack() {
        Log.i("WatchlistPlayback", "Playback flow cancelled by back tmdb=$tmdbType:$tmdbId " +
            "phase=${playbackCoordinator.phase}")
        cancelWatchlistAutoplay()
        finish()
    }

    private fun cancelWatchlistAutoplay() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_WATCHLIST, false))
            playbackScenarioId?.let { playbackCoordinator.dispatch(PlaybackScenarioEvent.Failed(it)) }
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
        if (playbackCoordinator.phase == PlaybackScenarioPhase.FAILED || autoPlayEpisode <= 0) return
        Log.i("WatchlistPlayback", "AniTube autoplay state=${state.javaClass.simpleName} " +
            "tmdb=$tmdbType:$tmdbId season=$autoPlaySeason episode=$autoPlayEpisode")
        val animeSeason = intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_SEASON, -1)
            .takeIf { it > 0 }
        val savedRelease = intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_RELEASE, -1)
            .takeIf { it > 0 }
        val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
            ?: return
        val mediaType = TmdbMediaType.entries.firstOrNull { it.path == tmdbType }
            ?: return
        val action = playbackSourcePorts.aniTube.onWatchlistState(
            ua.any2b.tvplayer.data.AniTubeWatchlistRequest(
                mediaType = mediaType,
                tmdbSeason = autoPlaySeason,
                animeSeason = animeSeason,
                episodeNumber = autoPlayEpisode,
                savedReleaseId = savedRelease,
                savedReleasePageUrl = intent.getStringExtra(EXTRA_AUTO_PLAY_ANITUBE_PAGE_URL),
                savedVoiceIndex = intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_VOICE, 0),
                savedVoiceName = intent.getStringExtra(EXTRA_AUTO_PLAY_ANITUBE_VOICE_NAME),
                savedProgress = TmdbWatchHistoryStore(this).episodeProgress(title.type, title.id),
            ), state,
        )
        when (action) {
            ua.any2b.tvplayer.data.AniTubeWatchlistAction.None -> Unit
            is ua.any2b.tvplayer.data.AniTubeWatchlistAction.SelectRelease ->
                detailsViewModel.selectAnitubeRelease(action.candidate.release)
            is ua.any2b.tvplayer.data.AniTubeWatchlistAction.LoadSavedRelease -> {
                Log.i("WatchlistPlayback", "Loading saved AniTube release newsId=${action.releaseId}")
                detailsViewModel.loadSavedAnitubeRelease(action.releaseId, action.pageUrl,
                    action.season, if (mediaType == TmdbMediaType.MOVIE) "MOVIE" else "TV")
            }
            is ua.any2b.tvplayer.data.AniTubeWatchlistAction.SelectPlayback -> {
                detailsViewModel.selectAnitubeVoice(action.voiceIndex)
                detailsViewModel.selectAnitubeEpisode(action.episodeIndex)
                action.progress?.let(detailsViewModel::restoreAnitubeProgress)
                playAnitube()
            }
            is ua.any2b.tvplayer.data.AniTubeWatchlistAction.Failed -> {
                Log.w("WatchlistPlayback", "AniTube autoplay failed: ${action.message}")
                failWatchlistPlayback(action.message)
            }
        }
    }

    private fun failWatchlistPlayback(message: String) {
        if (playbackCoordinator.phase == PlaybackScenarioPhase.FAILED) return
        val effect = playbackScenarioId?.let {
            playbackCoordinator.transition(PlaybackScenarioEvent.Failed(it, message))
                .effects.filterIsInstance<PlaybackHostEffect.ShowError>().firstOrNull()
        }
        Log.w("WatchlistPlayback", "Playback resolver failed tmdb=$tmdbType:$tmdbId: $message")
        Toast.makeText(this, effect?.message ?: message, Toast.LENGTH_LONG).show()
        if (intent.getBooleanExtra(EXTRA_SPECIAL_AUTO_PLAY, false)) {
            val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
            if (title != null) openDetailsFallback(title) else finish()
        } else if (intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false)) finish()
    }

    private var specialAutoPlaybackLaunched = false

    private fun openDetailsFallback(title: ua.any2b.core.tmdb.TmdbTitle) {
        if (isFinishing || isDestroyed) return
        startActivity(TvNavigation.details(this, title, torrentsOnly = true))
        finish()
    }

    private fun rankSpecialCandidates(
        hits: List<ua.any2b.core.toloka.TolokaHit>,
        title: ua.any2b.core.tmdb.TmdbTitle?,
        season: Int,
    ): List<ua.any2b.core.toloka.TolokaHit> {
        val year = title?.year
        return hits.distinctBy { it.id }.mapNotNull { hit ->
            val seeds = hit.seeders ?: 0
            if (seeds <= 0) return@mapNotNull null
            val metadata = TolokaReleaseMetadataParser.parse(hit.title)
            val releaseSeason = metadata.season
            if (metadata.hasConflictingMetadata || Regex("(?i)\\b(?:3d|iso|bdmv)\\b")
                    .containsMatchIn(hit.title) || Regex("(?i)(^|[ ._\\[(])(?:cam|hdcam|ts|telesync|tc)(?:$|[ ._\\])])")
                    .containsMatchIn(hit.title)) return@mapNotNull null
            if (year != null && metadata.yearRanges.isNotEmpty() &&
                metadata.yearRanges.none { year in (it.first - 1)..(it.last + 1) }) return@mapNotNull null
            if (title?.type == TmdbMediaType.TV && releaseSeason != null &&
                season !in releaseSeason..(metadata.seasonEnd ?: releaseSeason)) return@mapNotNull null
            if (title?.type == TmdbMediaType.TV && releaseSeason == null &&
                !Regex("(?i)(?:\\bS0?${season}\\b|season[ ._-]*0?${season}\\b|\\bcomplete\\b)")
                    .containsMatchIn(hit.title)) return@mapNotNull null
            val size = Regex("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*(GB|GiB|MB|MiB)")
                .find(hit.sizeText)?.let { match ->
                    match.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { amount ->
                        amount * if (match.groupValues[2].uppercase() in setOf("MB", "MIB")) 1.0 / 1024 else 1.0
                    }
                } ?: 0.0
            val text = hit.title.lowercase()
            val audio = when {
                metadata.audioTags.any { it.contains("Ukr", true) } -> 100
                metadata.subtitleTags.any { it.contains("Ukr", true) } -> 60
                else -> 20
            }
            val videoQuality = when {
                text.contains("remux") && size > 45 -> 15
                (text.contains("2160p") || text.contains("4k")) && text.contains("web") && size <= 35 -> 40
                text.contains("720p") -> 15
                text.contains("1080p") && (title?.type == TmdbMediaType.TV || size in 3.0..18.0) &&
                    listOf("web-dl", "webdl", "bdrip", "bluray").any(text::contains) -> 50
                else -> 0
            }
            val source = when {
                listOf("web-dl", "webdl", "bdrip", "bluray").any(text::contains) -> 8
                listOf("hdtvrip", "dvdrip").any(text::contains) -> 0
                else -> 3
            }
            val score = audio + videoQuality + source + minOf(seeds, 50) * 0.5
            hit to score
        }.sortedByDescending { it.second }.map { it.first }
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
        val requestId = playbackCoordinator.beginLaunch() ?: return
        val request = tolokaViewModel.playbackRequest(fileId)
        if (request == null) {
            playbackCoordinator.launchFailed(requestId)
            playbackScenarioId?.let { playbackCoordinator.dispatch(PlaybackScenarioEvent.Failed(it)) }
            tolokaViewModel.reportPlaybackStatus("Не вдалося сформувати URL потоку для вибраного файлу")
            Toast.makeText(this, "Відеофайл недоступний. Повторіть вибір роздачі", Toast.LENGTH_LONG).show()
            return
        }
        tolokaViewModel.reportPlaybackStatus("Перевіряємо доступність TorrServer…")
        lifecycleScope.launch {
            try {
                val credentials = TorrServerAuthStore(this@TmdbDetailsActivity).read()
                torrServerProbe.version(
                    SettingsManager(this@TmdbDetailsActivity).torrServerUrl(),
                    credentials?.let { TorrServerBasicAuth(it.username, it.password) },
                )
                if (!isFinishing && !isDestroyed && playbackCoordinator.isCurrentRequest(requestId))
                    launchFilePlayback(request, requestId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!playbackCoordinator.isCurrentRequest(requestId)) return@launch
                playbackCoordinator.launchFailed(requestId)
                if (isFinishing || isDestroyed) return@launch
                val message = playbackSourcePorts.toloka.classifyServerError(error)
                tolokaViewModel.reportPlaybackStatus("Перевірка TorrServer не пройшла: $message")
                playbackScenarioId?.let { playbackCoordinator.dispatch(PlaybackScenarioEvent.Failed(it)) }
                showTorrServerUnavailable(fileId, message)
            }
        }
    }

    private fun showTorrServerUnavailable(fileId: Int, message: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("TorrServer недоступний")
            .setMessage(message)
            .setPositiveButton("Повторити") { _, _ ->
                val retry = playbackScenarioId?.let {
                    playbackCoordinator.transition(PlaybackScenarioEvent.RetryRequested(
                        it, PlaybackSource.TORRENT))
                }
                if (retry?.effects?.contains(PlaybackHostEffect.RetrySource(
                        PlaybackSource.TORRENT)) == true) openFile(fileId)
            }
            .setNeutralButton("Налаштування") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Назад", null)
            .show()
    }

    private fun launchFilePlayback(candidate: PlaybackRequest.TorrentFile, requestId: PlaybackRequestId) {
        val request = playbackCoordinator.launchPlaybackRequest(requestId, candidate)?.request
            as? PlaybackRequest.TorrentFile ?: return
        val fileId = request.fileId
        val url = request.streamUrl
        val topicId = request.topicId
        val fileName = request.fileName
        tolokaViewModel.reportPlaybackStatus("URL потоку сформовано для індексу $fileId. Відкриваємо плеєр…")
        val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
        val fileMetadata = fileName?.let(TolokaReleaseMetadataParser::parse)
        val folderSeason = (tolokaViewModel.state.value.transfer as? TolokaTransferState.Files)
            ?.torrent?.videoFiles?.firstOrNull { it.id == fileId }
            ?.path?.substringBeforeLast('/', "")?.takeIf(String::isNotBlank)
            ?.let(TolokaReleaseMetadataParser::parse)?.season
        val requestedEpisode = autoPlayEpisode.takeIf {
            autoPlaySource == TmdbPlaybackSelection.Source.TORRENT.name && it > 0 &&
            tolokaViewModel.playbackFileId(autoPlaySeason, it) == fileId }
        val playbackPosition = playbackSourcePorts.toloka.episodeContext(
            fileSeason = fileMetadata?.season,
            fileEpisodeStart = fileMetadata?.episodeStart,
            fileEpisodeEnd = fileMetadata?.episodeEnd,
            folderSeason = folderSeason,
            requestedSeason = autoPlaySeason,
            requestedEpisode = requestedEpisode,
            matchedSeason = title?.matchedSeasonNumber,
            isTv = title?.type == TmdbMediaType.TV,
            mappedEpisodeForSeason = { season -> tolokaViewModel.playbackEpisodeNumber(fileId, season) },
        )
        val currentSeason = playbackPosition.season
        val currentEpisode = playbackPosition.episode
        Log.d("NextEp", "Toloka playlist current=$currentSeason:$currentEpisode " +
            "file=$fileId next=${currentSeason?.let { season ->
                currentEpisode?.let { tolokaViewModel.playbackFileId(season, it + 1) }
            }}")
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
                                topicId = topicId, fileId = fileId, fileName = fileName, tmdbSeasonNumber = currentSeason))
                        if (it.type == TmdbMediaType.TV) {
                            if (currentEpisode != null) {
                                history.saveEpisodeProgress(it, 0, currentEpisode - 1,
                                    currentEpisode, 0f, currentSeason ?: 1)
                            } else {
                                ua.any2b.tvplayer.data.TmdbWatchlistStore(this)
                                    .onPlaybackStarted(it)
                            }
                        }
                    }
                    val torrentContext = title?.let {
                    ExternalPlaybackContext(it, PlaybackSource.TORRENT,
                            topicId, fileId, currentEpisode, currentSeason)
                    }
                    launchExternalPlayer(externalIntent, selectedPackage,
                            title?.let {
                                history.torrentProgress(it.type, it.id, topicId, fileId)?.positionMs
                            } ?: 0L, torrentContext, requestId)
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
                    currentEpisode?.let { episode ->
                        putExtra(PlayerActivity.EXTRA_TMDB_EPISODE, episode)
                        val nextFileId = currentSeason?.let { season ->
                            tolokaViewModel.playbackFileId(season, episode + 1)
                        }?.takeIf { it != fileId }
                        if (nextFileId != null) {
                            putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_AVAILABLE, true)
                            nextFileId.let(tolokaViewModel::playbackUrl)?.let { nextUrl ->
                                putExtra(PlayerActivity.EXTRA_NEXT_STREAM_URL, nextUrl)
                            }
                        }
                    }
                    currentSeason?.let { season ->
                        putExtra(PlayerActivity.EXTRA_TMDB_SEASON, season)
                    }
                    if (!hasExtra(PlayerActivity.EXTRA_TMDB_SEASON)) it.matchedSeasonNumber?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_SEASON, it)
                    }
                }
            })
            playbackScenarioId?.let {
                playbackCoordinator.playbackLaunched(it, requestId,
                    PlaybackSource.TORRENT)
            }
            finishDetailsForWatchlistPlayback()
            tolokaViewModel.reportPlaybackStatus("URL передано вбудованому плеєру")
        } catch (_: Exception) {
            playbackCoordinator.launchFailed(requestId)
            playbackScenarioId?.let { playbackCoordinator.dispatch(PlaybackScenarioEvent.Failed(it)) }
            tolokaViewModel.reportPlaybackStatus("Не вдалося запустити плеєр після формування URL потоку")
            Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
        }
    }

    private fun playAnitube() {
        val requestId = playbackCoordinator.beginLaunch() ?: return
        val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
            ?: run {
                playbackCoordinator.launchFailed(requestId)
                return
            }
        detailsViewModel.playAnitube(
            onError = { message ->
                if (!playbackCoordinator.isCurrentRequest(requestId)) return@playAnitube
                if (intent.getBooleanExtra(EXTRA_PLAYBACK_ONLY, false)) failWatchlistPlayback(message)
                else Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                playbackCoordinator.launchFailed(requestId)
            },
            onSuccess = { request ->
                val voiceIndex = request.voiceIndex
                val episodeIndex = request.episodeIndex
                val episodeNumber = request.episodeNumber
                val resumeProgress = request.resumeProgress
                if (!playbackCoordinator.isCurrentRequest(requestId) ||
                    playbackCoordinator.phase == PlaybackScenarioPhase.FAILED || isFinishing || isDestroyed) {
                    playbackCoordinator.launchFailed(requestId)
                    return@playAnitube
                }
                val backupStreams = request.backupStreams.map {
                    PlaybackRequest.BackupStream(it.embedUrl, it.voiceIndex, it.episodeIndex)
                }
                val launchEffect = playbackCoordinator.launchPlaybackRequest(requestId,
                    PlaybackRequest.OnlineStream(request.streamUrl, voiceIndex, episodeIndex,
                        episodeNumber, resumeProgress, backupStreams)) ?: run {
                    playbackCoordinator.launchFailed(requestId)
                    return@playAnitube
                }
                val streamRequest = launchEffect.request as? PlaybackRequest.OnlineStream
                    ?: return@playAnitube
                val url = streamRequest.streamUrl
                Log.i("WatchlistPlayback", "AniTube playback launch tmdb=$tmdbType:$tmdbId " +
                    "season=$autoPlaySeason episode=$episodeNumber voice=$voiceIndex " +
                    "resumeProgress=$resumeProgress")
                val selectedRelease = (detailsViewModel.anitubeState.value as?
                    ua.any2b.tvplayer.data.AnitubeState.Found)?.release?.newsId
                val found = detailsViewModel.anitubeState.value as?
                    ua.any2b.tvplayer.data.AnitubeState.Found
                // AniTube season numbering is independent from TMDB's season numbering.
                // On continuation, keep the explicit AniTube season carried by the player;
                // catalog candidates can describe the TMDB-wide mapping as season 1.
                val seasons = playbackSourcePorts.aniTube.playbackSeasons(title, autoPlaySeason,
                    intent.getIntExtra(EXTRA_AUTO_PLAY_ANITUBE_SEASON, -1).takeIf { it > 0 },
                    selectedRelease, found)
                val selectedTmdbSeason = seasons.tmdbSeason
                val selectedAnimeSeason = seasons.animeSeason
                TmdbWatchHistoryStore(this).savePlaybackSelection(title.type, title.id,
                    TmdbPlaybackSelection(TmdbPlaybackSelection.Source.ANITUBE,
                        anitubeReleaseId = selectedRelease,
                        anitubeSeasonNumber = selectedAnimeSeason,
                        tmdbSeasonNumber = selectedTmdbSeason,
                        voiceIndex = voiceIndex,
                        voiceName = found?.voices?.getOrNull(voiceIndex)?.name,
                        anitubePageUrl = found?.release?.pageUrl),
                    seasonNumber = selectedTmdbSeason)
                val selectedPackage = SettingsManager(this).selectedPlayerPackage()
                if (selectedPackage != null) {
                    val externalIntent = ExternalVideoPlayers(this).intentFor(url, selectedPackage)
                    if (externalIntent != null) {
                        try {
                            val history = TmdbWatchHistoryStore(this)
                            val storedProgress = history.episodeProgress(title.type, title.id)
                                ?.takeIf { it.voiceIndex == voiceIndex &&
                                    it.episodeNumber == episodeNumber &&
                                    (selectedTmdbSeason == null || it.seasonNumber == selectedTmdbSeason) }
                            val positionMs = storedProgress?.positionMs?.takeIf { it > 0L }
                                ?: if (storedProgress != null && storedProgress.durationMs > 0L)
                                    (storedProgress.durationMs * resumeProgress).toLong() else 0L
                            val vimuContext = ExternalPlaybackContext(title,
                                PlaybackSource.ANITUBE,
                                episodeNumber = episodeNumber, seasonNumber = selectedTmdbSeason,
                                voiceIndex = voiceIndex, episodeIndex = episodeIndex)
                            launchExternalPlayer(externalIntent, selectedPackage,
                                positionMs, vimuContext, requestId)
                            val aniListId = title.matchedAniListId
                                ?: intent.getIntExtra(EXTRA_ANI_LIST_ID, -1).takeIf { it > 0 }
                            history.saveEpisodeProgress(title.copy(
                                matchedSeasonNumber = selectedTmdbSeason,
                                matchedAnimeSeasonNumber = selectedAnimeSeason,
                                matchedAniListId = aniListId), voiceIndex, episodeIndex,
                                episodeNumber, resumeProgress, selectedTmdbSeason ?: 1,
                                storedProgress?.positionMs ?: 0L,
                                storedProgress?.durationMs ?: 0L,
                                animeSeasonNumber = selectedAnimeSeason)
                            if (!ExternalVideoPlayers(this).isVimu(selectedPackage))
                                finishDetailsForWatchlistPlayback()
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
                try { startActivity(Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_STREAM_URL, streamRequest.streamUrl)
                    putExtra(PlayerActivity.EXTRA_SOURCE_TYPE, PlayerActivity.SOURCE_ANITUBE_TMDB)
                    putExtra(PlayerActivity.EXTRA_VOICE_INDEX, streamRequest.voiceIndex)
                    putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, streamRequest.episodeIndex)
                    putExtra(PlayerActivity.EXTRA_EPISODE_NUMBER, streamRequest.episodeNumber)
                    putExtra(PlayerActivity.EXTRA_TMDB_ID, title.id)
                    putExtra(PlayerActivity.EXTRA_TMDB_TYPE, title.type.path)
                    (title.matchedAniListId
                        ?: intent.getIntExtra(EXTRA_ANI_LIST_ID, -1).takeIf { it > 0 })
                        ?.let { putExtra(PlayerActivity.EXTRA_ANI_LIST_ID, it) }
                    putExtra(PlayerActivity.EXTRA_TMDB_TITLE, title.title)
                    (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.logoUrl
                        ?.let { logo -> putExtra(PlayerActivity.EXTRA_TMDB_LOGO_URL, logo) }
                    putExtra(PlayerActivity.EXTRA_TMDB_ORIGINAL_TITLE, title.originalTitle)
                    putExtra(PlayerActivity.EXTRA_TMDB_YEAR, title.year ?: 0)
                    putExtra(PlayerActivity.EXTRA_TMDB_POSTER_URL, title.posterUrl)
                    putExtra(PlayerActivity.EXTRA_TMDB_RATING, title.rating ?: Double.NaN)
                    putExtra(PlayerActivity.EXTRA_TMDB_GENRE_IDS, title.genreIds.toIntArray())
                    putExtra(PlayerActivity.EXTRA_TMDB_RESUME_PROGRESS, streamRequest.resumeProgress)
                    selectedTmdbSeason?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_SEASON, it)
                    }
                    selectedAnimeSeason?.let {
                        putExtra(PlayerActivity.EXTRA_ANIME_SEASON, it)
                    }
                    putExtra(PlayerActivity.EXTRA_TMDB_EPISODE, episodeNumber)
                    putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_AVAILABLE,
                        found?.voices?.getOrNull(voiceIndex)?.episodes?.any {
                            it.episodeNumber == episodeNumber + 1
                        } == true)
                    selectedRelease?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_ANITUBE_RELEASE, it)
                    }
                    found?.release?.pageUrl?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_ANITUBE_PAGE_URL, it)
                    }
                    found?.voices?.getOrNull(voiceIndex)?.name?.let {
                        putExtra(PlayerActivity.EXTRA_TMDB_ANITUBE_VOICE_NAME, it)
                    }
                    putStringArrayListExtra(PlayerActivity.EXTRA_BACKUP_EMBED_URLS,
                        ArrayList(streamRequest.backupStreams.map { it.embedUrl }))
                    putExtra(PlayerActivity.EXTRA_BACKUP_VOICE_INDICES,
                        streamRequest.backupStreams.map { it.voiceIndex }.toIntArray())
                    putExtra(PlayerActivity.EXTRA_BACKUP_EPISODE_INDICES,
                        streamRequest.backupStreams.map { it.episodeIndex }.toIntArray())
                }); playbackScenarioId?.let {
                    playbackCoordinator.playbackLaunched(it, requestId,
                        PlaybackSource.ANITUBE)
                } } catch (_: Exception) {
                    playbackCoordinator.launchFailed(requestId)
                    Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
                }
                if (playbackCoordinator.phase == PlaybackScenarioPhase.PLAYER_ACTIVE)
                    finishDetailsForWatchlistPlayback()
            },
        )
    }

    private fun finishDetailsForWatchlistPlayback() {
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_WATCHLIST, false)) {
            val result = playbackScenarioId?.let {
                playbackCoordinator.transition(PlaybackScenarioEvent.Finished(it))
            }
            if (result?.effects?.contains(PlaybackHostEffect.Finish) == true) finish()
        }
    }

    private fun launchExternalPlayer(
        playerIntent: Intent,
        packageName: String,
        resumePositionMs: Long,
        context: ExternalPlaybackContext?,
        requestId: PlaybackRequestId,
    ) {
        val players = ExternalVideoPlayers(this)
        if (players.isVimu(packageName)) {
            players.applyVimuResume(playerIntent, resumePositionMs)
            pendingVimuPlayback = context
            vimuPlayerLauncher.launch(playerIntent)
        } else {
            startActivity(playerIntent)
        }
        context?.title?.let { ua.any2b.tvplayer.data.TmdbWatchlistStore(this)
            .onPlaybackStarted(it) }
        playbackScenarioId?.let {
            playbackCoordinator.playbackLaunched(it, requestId, context?.source)
        }
    }

    private fun onVimuPlayerResult(result: ActivityResult) {
        playbackScenarioId?.let {
            playbackCoordinator.playerReturned(it)
        }
        val pending = pendingVimuPlayback ?: run {
            finishDetailsForWatchlistPlayback()
            return
        }
        pendingVimuPlayback = null
        val history = TmdbWatchHistoryStore(this)
        val data = result.data
        val effect = playbackCoordinator.resolveExternalPlayerReturn(pending,
            ExternalPlayerReturnEvent(result.resultCode,
                data?.getIntExtra("position", -1)?.toLong(),
                data?.getIntExtra("duration", -1)?.toLong()))
        when (effect) {
            ExternalPlaybackEffect.Finish -> Unit
            is ExternalPlaybackEffect.SaveTorrentProgress -> history.saveTorrentProgress(
                effect.title, effect.topicId, effect.fileId, effect.positionMs, effect.durationMs,
                effect.episodeNumber, effect.seasonNumber)
            is ExternalPlaybackEffect.CompleteTorrent -> history.completeTorrentPlayback(
                effect.title, effect.topicId, effect.fileId, effect.episodeNumber, effect.seasonNumber)
            is ExternalPlaybackEffect.SaveEpisodeProgress -> history.saveEpisodeProgress(
                effect.title, effect.voiceIndex, effect.episodeIndex, effect.episodeNumber,
                effect.progress, effect.seasonNumber, effect.positionMs, effect.durationMs)
            is ExternalPlaybackEffect.CompleteEpisode -> history.saveEpisodeProgress(
                effect.title, effect.voiceIndex, effect.episodeIndex, effect.episodeNumber,
                1f, effect.seasonNumber)
        }
        finishDetailsForWatchlistPlayback()
    }
}
