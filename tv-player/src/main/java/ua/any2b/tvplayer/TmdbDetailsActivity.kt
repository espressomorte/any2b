package ua.any2b.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaPlaybackStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbDetailsScreen
import ua.any2b.tvplayer.ui.TmdbDetailsViewModel
import ua.any2b.tvplayer.ui.TolokaSearchViewModel
import ua.any2b.tvplayer.ui.TolokaSearchState
import ua.any2b.tvplayer.ui.TmdbDetailsState
import ua.any2b.tvplayer.ui.tmdbTorrentQueryOptions
import ua.any2b.core.matcher.SeriesTitleSearch
import ua.any2b.tvplayer.ui.FanartDayTheme
import ua.any2b.tvplayer.ui.FanartArtworkKey
import ua.any2b.tvplayer.ui.FanartArtworkState
import ua.any2b.tvplayer.ui.FanartArtworkViewModel

class TmdbDetailsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TMDB_ID = "TMDB_ID"
        const val EXTRA_TMDB_TYPE = "TMDB_TYPE"
        const val EXTRA_TMDB_SEASON = "TMDB_SEASON"
        const val EXTRA_SEARCH_QUERY = "SEARCH_QUERY"
        const val EXTRA_PREFERRED_TITLE = "PREFERRED_TITLE"
        const val EXTRA_ORIGINAL_LANGUAGE = "ORIGINAL_LANGUAGE"
        const val EXTRA_ORIGINAL_TITLE = "ORIGINAL_TITLE"
        const val EXTRA_FAVORITES_LAYOUT = "FAVORITES_LAYOUT"
        const val EXTRA_TORRENTS_ONLY = "TORRENTS_ONLY"
    }

    private val detailsViewModel: TmdbDetailsViewModel by viewModels()
    private val tolokaViewModel: TolokaSearchViewModel by viewModels()
    private val artworkViewModel: FanartArtworkViewModel by viewModels()
    private var launchingPlayer = false
    private var returningFromAnitubePlayer = false
    private var openedTopics by mutableStateOf(emptySet<String>())
    private var openedFiles by mutableStateOf(emptySet<Int>())
    private var selectedTopicId: String? = null
    private var tmdbId = -1
    private var tmdbType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val favoritesLayout = intent.getBooleanExtra(EXTRA_FAVORITES_LAYOUT, false)
        val torrentsOnly = intent.getBooleanExtra(EXTRA_TORRENTS_ONLY, false)
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
                LaunchedEffect(tolokaState.search) { refreshPlaybackMarks() }
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
                TmdbDetailsScreen(
                    state = detailsState,
                    favoritesLayout = favoritesLayout,
                    torrentsOnly = torrentsOnly,
                    experimentalPalette = activePalette,
                    experimentalArtwork = if (experimental) tmdbArtwork else null,
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
                    onPlayFile = ::openFile,
                    onPanelSelected = detailsViewModel::setPanel,
                    onRetryAnitube = detailsViewModel::retryAnitube,
                    onSelectAnitubeRelease = detailsViewModel::selectAnitubeRelease,
                    onSelectAnitubeSeason = detailsViewModel::selectAnitubeSeason,
                    onSelectAnitubeVoice = detailsViewModel::selectAnitubeVoice,
                    onSelectAnitubeEpisode = detailsViewModel::selectAnitubeEpisode,
                    onPlayAnitube = ::playAnitube,
                    onToggleFavorite = detailsViewModel::toggleFavorite,
                    onTrailer = {
                        val details = (detailsViewModel.state.value as?
                            ua.any2b.tvplayer.ui.TmdbDetailsState.Ready)?.details
                        if (details != null) {
                            startActivity(Intent(this, TrailerActivity::class.java).apply {
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
                        startActivity(TvNavigation.details(this, related, torrentsOnly = torrentsOnly))
                    },
                    onBack = ::finish,
                )
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

    private fun openFile(fileId: Int) {
        if (launchingPlayer) return
        val url = tolokaViewModel.playbackUrl(fileId)
        if (url == null) {
            tolokaViewModel.reportPlaybackStatus("Не вдалося сформувати URL потоку для вибраного файлу")
            Toast.makeText(this, "Відеофайл недоступний. Повторіть вибір роздачі", Toast.LENGTH_LONG).show()
            return
        }
        launchingPlayer = true
        tolokaViewModel.reportPlaybackStatus("URL потоку сформовано для індексу $fileId. Відкриваємо плеєр…")
        val title = (detailsViewModel.state.value as? TmdbDetailsState.Ready)?.details?.title
        val selectedPackage = SettingsManager(this).selectedPlayerPackage()
        val serverRequiresAuth = TorrServerAuthStore(this).read() != null
        if (selectedPackage != null && !serverRequiresAuth) {
            val externalIntent = ExternalVideoPlayers(this).intentForVideo(url, selectedPackage)
            if (externalIntent != null) {
                try {
                    startActivity(externalIntent)
                    selectedTopicId?.let { topicId ->
                        TolokaPlaybackStore(this).markTmdbOpened(tmdbType, tmdbId, topicId, fileId)
                    }
                    title?.let { TmdbWatchHistoryStore(this).record(it) }
                    refreshPlaybackMarks()
                    tolokaViewModel.reportPlaybackStatus("URL передано зовнішньому плеєру. Результат відтворення перевіряйте в ньому")
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
                selectedTopicId?.let { putExtra(PlayerActivity.EXTRA_TOLOKA_TOPIC_ID, it) }
                putExtra(PlayerActivity.EXTRA_TOLOKA_FILE_ID, fileId)
                title?.let {
                    val resumePosition = selectedTopicId?.let { topicId ->
                        TmdbWatchHistoryStore(this@TmdbDetailsActivity)
                            .torrentProgress(it.type, it.id, topicId, fileId)
                    }?.positionMs ?: 0L
                    putExtra(PlayerActivity.EXTRA_TORRENT_RESUME_POSITION_MS, resumePosition)
                    putExtra(PlayerActivity.EXTRA_TMDB_ID, it.id)
                    putExtra(PlayerActivity.EXTRA_TMDB_TYPE, it.type.path)
                    putExtra(PlayerActivity.EXTRA_TMDB_TITLE, it.title)
                    putExtra(PlayerActivity.EXTRA_TMDB_ORIGINAL_TITLE, it.originalTitle)
                    putExtra(PlayerActivity.EXTRA_TMDB_YEAR, it.year ?: 0)
                    putExtra(PlayerActivity.EXTRA_TMDB_POSTER_URL, it.posterUrl)
                    putExtra(PlayerActivity.EXTRA_TMDB_RATING, it.rating ?: Double.NaN)
                    putExtra(PlayerActivity.EXTRA_TMDB_GENRE_IDS, it.genreIds.toIntArray())
                }
            })
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
                if (launchingPlayer) return@playAnitube
                launchingPlayer = true
                val selectedPackage = SettingsManager(this).selectedPlayerPackage()
                if (selectedPackage != null) {
                    val externalIntent = ExternalVideoPlayers(this).intentFor(url, selectedPackage)
                    if (externalIntent != null) {
                        try {
                            startActivity(externalIntent)
                            TmdbWatchHistoryStore(this).saveEpisodeProgress(title,
                                voiceIndex, episodeIndex, episodeNumber, resumeProgress)
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
                    putExtra(PlayerActivity.EXTRA_TMDB_ORIGINAL_TITLE, title.originalTitle)
                    putExtra(PlayerActivity.EXTRA_TMDB_YEAR, title.year ?: 0)
                    putExtra(PlayerActivity.EXTRA_TMDB_POSTER_URL, title.posterUrl)
                    putExtra(PlayerActivity.EXTRA_TMDB_RATING, title.rating ?: Double.NaN)
                    putExtra(PlayerActivity.EXTRA_TMDB_GENRE_IDS, title.genreIds.toIntArray())
                    putExtra(PlayerActivity.EXTRA_TMDB_RESUME_PROGRESS, resumeProgress)
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
            },
        )
    }
}
