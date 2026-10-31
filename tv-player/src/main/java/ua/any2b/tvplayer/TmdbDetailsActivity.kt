package ua.any2b.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.trailerSearchUrl
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.FanartKeyStore
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
import ua.any2b.tvplayer.ui.FanartArtworkKey
import ua.any2b.tvplayer.ui.FanartArtworkState
import ua.any2b.tvplayer.ui.FanartArtworkViewModel
import ua.any2b.tvplayer.ui.FanartDayTheme
import ua.any2b.tvplayer.ui.AppColors

class TmdbDetailsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TMDB_ID = "TMDB_ID"
        const val EXTRA_TMDB_TYPE = "TMDB_TYPE"
        const val EXTRA_SEARCH_QUERY = "SEARCH_QUERY"
        const val EXTRA_PREFERRED_TITLE = "PREFERRED_TITLE"
        const val EXTRA_ORIGINAL_LANGUAGE = "ORIGINAL_LANGUAGE"
        const val EXTRA_ORIGINAL_TITLE = "ORIGINAL_TITLE"
    }

    private val detailsViewModel: TmdbDetailsViewModel by viewModels()
    private val tolokaViewModel: TolokaSearchViewModel by viewModels()
    private val fanartViewModel: FanartArtworkViewModel by viewModels()
    private var launchingPlayer = false
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
        val experimental = SettingsManager(this).isExperimentalAppearanceEnabled() &&
            !FanartKeyStore(this).read().isNullOrBlank()
        detailsViewModel.load(type, id, query, preferredTitle, originalLanguage, originalTitle)
        setContent {
            AppTheme {
                val artworkKey = FanartArtworkKey(type, id)
                LaunchedEffect(experimental, artworkKey) {
                    if (experimental) fanartViewModel.load(type, id)
                }
                val artworkState = fanartViewModel.state.collectAsStateWithLifecycle().value
                val detailsState = detailsViewModel.state.collectAsStateWithLifecycle().value
                val tolokaState = tolokaViewModel.state.collectAsStateWithLifecycle().value
                val readyDetails = (detailsState as? TmdbDetailsState.Ready)?.details
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
                    val english = details.title.englishTitle
                        ?: details.title.originalTitle?.takeIf {
                            details.title.originalLanguage == "en"
                        }
                    tolokaViewModel.searchAutomatically(details, initial, english)
                }
                if (experimental && detailsState is ua.any2b.tvplayer.ui.TmdbDetailsState.Ready &&
                    (artworkState as? FanartArtworkState.Ready)?.key != artworkKey) {
                    Box(Modifier.fillMaxSize().background(AppColors.BackgroundDark),
                        contentAlignment = Alignment.Center) { Text("Готуємо вигляд деталей…") }
                    return@AppTheme
                }
                val activePalette = if (experimental)
                    (artworkState as? FanartArtworkState.Ready)
                        ?.takeIf { it.key == artworkKey }?.palette else null
                FanartDayTheme(activePalette?.light == true) {
                TmdbDetailsScreen(
                    state = detailsState,
                    experimentalPalette = activePalette,
                    experimentalArtwork = if (experimental)
                        (artworkState as? FanartArtworkState.Ready)
                            ?.takeIf { it.key == artworkKey }?.artwork else null,
                    fanartNotice = if (experimental)
                        (artworkState as? FanartArtworkState.Ready)
                            ?.takeIf { it.key == artworkKey }?.reason?.let { "fanart.tv: $it" }
                        else null,
                    toloka = tolokaState,
                    openedTopics = openedTopics,
                    openedFiles = openedFiles,
                    onRetryDetails = {
                        detailsViewModel.load(type, id, query, preferredTitle,
                            originalLanguage, originalTitle, force = true)
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
                    onRetryMetadata = tolokaViewModel::retryMetadata,
                    onPlayFile = ::openFile,
                    onToggleFavorite = detailsViewModel::toggleFavorite,
                    onTrailer = {
                        val details = (detailsViewModel.state.value as?
                            ua.any2b.tvplayer.ui.TmdbDetailsState.Ready)?.details
                        if (details != null) try {
                            startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse(details.trailerSearchUrl())))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(this, "Немає застосунку для відкриття трейлера",
                                Toast.LENGTH_LONG).show()
                        }
                    },
                    onRetryRelated = detailsViewModel::retryRelated,
                    onOpenRelated = { related ->
                        startActivity(Intent(this, TmdbDetailsActivity::class.java).apply {
                            putExtra(EXTRA_TMDB_ID, related.id)
                            putExtra(EXTRA_TMDB_TYPE, related.type.path)
                        })
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
                    putExtra(PlayerActivity.EXTRA_TMDB_ID, it.id)
                    putExtra(PlayerActivity.EXTRA_TMDB_TYPE, it.type.path)
                    putExtra(PlayerActivity.EXTRA_TMDB_TITLE, it.title)
                    putExtra(PlayerActivity.EXTRA_TMDB_ORIGINAL_TITLE, it.originalTitle)
                    putExtra(PlayerActivity.EXTRA_TMDB_YEAR, it.year ?: 0)
                    putExtra(PlayerActivity.EXTRA_TMDB_POSTER_URL, it.posterUrl)
                    putExtra(PlayerActivity.EXTRA_TMDB_RATING, it.rating ?: Double.NaN)
                }
            })
            tolokaViewModel.reportPlaybackStatus("URL передано вбудованому плеєру")
        } catch (_: Exception) {
            launchingPlayer = false
            tolokaViewModel.reportPlaybackStatus("Не вдалося запустити плеєр після формування URL потоку")
            Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
        }
    }
}
