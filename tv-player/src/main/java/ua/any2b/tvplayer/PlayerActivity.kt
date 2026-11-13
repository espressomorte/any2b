package ua.any2b.tvplayer

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil.load
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.data.TolokaPlaybackStore
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.PlayerMediaCache
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.tmdb.IntroDbClient
import ua.any2b.core.tmdb.IntroDbSegments
import ua.any2b.core.toloka.TolokaReleaseMetadataParser
import ua.any2b.core.toloka.TorrServerBasicAuth
import ua.any2b.core.toloka.TorrServerProbe
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.tvplayer.data.AnitubeStreamFallback
import ua.any2b.tvplayer.ui.Media3PlayerTransport
import ua.any2b.tvplayer.ui.PlayerControlsController
import ua.any2b.tvplayer.ui.PlayerTrackSelectionMenu
import ua.any2b.tvplayer.databinding.ActivityPlayerBinding
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TvPlayer"
        private const val MIN_BUFFER_MS = 50_000
        private const val MAX_BUFFER_MS = 120_000
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
        private const val NEXT_EPISODE_PRELOAD_LEAD_MS = 20_000L
        private const val NEXT_EPISODE_FALLBACK_LEAD_MS = 45_000L
        private const val NEXT_EPISODE_PREFETCH_BYTES = 2L * 1024L * 1024L
        private const val PLAYER_PROMPT_TIMEOUT_MS = 13_000L
        private const val TORRENT_BUFFER_TIMEOUT_MS = 30_000L
        private const val TORRENT_SEEK_BUFFER_TIMEOUT_MS = 45_000L
        private const val TORRENT_RETRY_BUFFER_TIMEOUT_MS = 60_000L
        private const val MAX_STREAM_RETRIES = 4
        const val EXTRA_STREAM_URL = "STREAM_URL"
        const val EXTRA_VOICE_INDEX = "VOICE_INDEX"
        const val EXTRA_EPISODE_INDEX = "EPISODE_INDEX"
        const val EXTRA_EPISODE_NUMBER = "EPISODE_NUMBER"
        const val EXTRA_SOURCE_TYPE = "SOURCE_TYPE"
        const val SOURCE_TOLOKA = "toloka"
        const val SOURCE_ANITUBE_TMDB = "anitube_tmdb"
        const val EXTRA_TOLOKA_TOPIC_ID = "TOLOKA_TOPIC_ID"
        const val EXTRA_TOLOKA_FILE_ID = "TOLOKA_FILE_ID"
        const val EXTRA_TOLOKA_FILE_NAME = "TOLOKA_FILE_NAME"
        const val EXTRA_TMDB_ID = "TMDB_ID"
        const val EXTRA_TMDB_TYPE = "TMDB_TYPE"
        const val EXTRA_TMDB_TITLE = "TMDB_TITLE"
        const val EXTRA_TMDB_LOGO_URL = "TMDB_LOGO_URL"
        const val EXTRA_TMDB_ORIGINAL_TITLE = "TMDB_ORIGINAL_TITLE"
        const val EXTRA_TMDB_YEAR = "TMDB_YEAR"
        const val EXTRA_TMDB_POSTER_URL = "TMDB_POSTER_URL"
        const val EXTRA_TMDB_RATING = "TMDB_RATING"
        const val EXTRA_TMDB_GENRE_IDS = "TMDB_GENRE_IDS"
        const val EXTRA_TMDB_SEASON = "TMDB_SEASON"
        const val EXTRA_TMDB_EPISODE = "TMDB_EPISODE"
        const val EXTRA_NEXT_STREAM_URL = "NEXT_STREAM_URL"
        const val EXTRA_NEXT_EPISODE_AVAILABLE = "NEXT_EPISODE_AVAILABLE"
        const val EXTRA_TMDB_ANITUBE_RELEASE = "TMDB_ANITUBE_RELEASE"
        const val EXTRA_TMDB_ANITUBE_VOICE_NAME = "TMDB_ANITUBE_VOICE_NAME"
        const val EXTRA_TMDB_RESUME_PROGRESS = "TMDB_RESUME_PROGRESS"
        const val EXTRA_TORRENT_RESUME_POSITION_MS = "TORRENT_RESUME_POSITION_MS"
        const val EXTRA_BACKUP_EMBED_URLS = "BACKUP_EMBED_URLS"
        const val EXTRA_BACKUP_VOICE_INDICES = "BACKUP_VOICE_INDICES"
        const val EXTRA_BACKUP_EPISODE_INDICES = "BACKUP_EPISODE_INDICES"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var controlsController: PlayerControlsController? = null
    private lateinit var trackSelectionMenu: PlayerTrackSelectionMenu
    private val introDbClient = IntroDbClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var introSegments: IntroDbSegments = IntroDbSegments(emptyList(), emptyList())
    private var introLookupStarted = false
    private var introPromptDismissed = false
    private var nextEpisodePromptDismissed = false
    private var nextEpisodeTransitioning = false
    private var nextEpisodePreloadStarted = false
    private var activePlayerPrompt: PlayerPrompt? = null
    private var promptDismissRunnable: Runnable? = null
    private var nextEpisodePreloadJob: Job? = null
    private var nextEpisodeCacheWriter: CacheWriter? = null
    private val mediaDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(PlayerMediaCache.get(this))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
    }
    private val updateIntroSkip = object : Runnable {
        override fun run() {
            updateSkipIntroButton()
            if (!isFinishing && !isDestroyed) mainHandler.postDelayed(this, 200L)
        }
    }

    private enum class PlayerPrompt { SKIP_INTRO, NEXT_EPISODE }

    private var voiceIndex: Int = 0
    private var episodeIndex: Int = 0
    private var episodeNumber: Int = 1
    private var isToloka: Boolean = false
    private var isTmdbAnitube: Boolean = false
    private var tmdbAnitubeStarted: Boolean = false
    private var tmdbResumeProgress: Float = 0f
    private var tmdbResumeApplied: Boolean = false
    private var torrentResumePositionMs: Long = 0L
    private var torrentResumeApplied: Boolean = false
    private var tolokaPlaybackMarked: Boolean = false
    private val backupSources = ArrayDeque<AnitubeStreamFallback>()
    private var retryingBackup = false
    private var torrentBufferingSinceMs = 0L
    private var torrentLastSeekAtMs = 0L
    private var torrentRecoveryAttempts = 0
    private var torrentRetrySeekPending = false
    private var torrentBufferPromptShown = false
    private var torrentBufferingWatch: Runnable? = null
    private var torrentRecoveryDialog: AlertDialog? = null
    private var streamRetryJob: Job? = null
    private var stablePlaybackJob: Job? = null
    private var networkCheckJob: Job? = null
    private var networkInterrupted = false
    private var resumeAfterNetwork = false
    private var streamRetryCount = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val torrServerProbe = TorrServerProbe()
    private val streamResolver = StreamResolver()

    private var progressJob: Job? = null
    private val tolokaServerBase by lazy { SettingsManager(this).torrServerUrl().toHttpUrlOrNull() }
    private val tolokaBasicHeader by lazy {
        TorrServerAuthStore(this).read()?.let { Credentials.basic(it.username, it.password) }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host
            val req = original.newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                )
                .apply {
                    if (isToloka) {
                        val server = tolokaServerBase
                        val auth = tolokaBasicHeader
                        if (server != null && auth != null &&
                            original.url.host == server.host && original.url.port == server.port &&
                            original.url.scheme == server.scheme) {
                            header("Authorization", auth)
                        }
                    }
                    if (host.contains("ashdi")) {
                        header("Referer", "https://ashdi.vip/")
                    } else if (host.contains("fenixplay")) {
                        header("Referer", "https://fenixplay.xyz/")
                        header("Origin", "https://fenixplay.xyz")
                    }
                }
                .build()
            chain.proceed(req)
        }
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        controlsController = PlayerControlsController(binding.playerControls)
        trackSelectionMenu = PlayerTrackSelectionMenu(this)
        binding.btnSkipIntro.setOnClickListener { confirmActivePrompt() }

        val url = intent.getStringExtra(EXTRA_STREAM_URL)
        voiceIndex = intent.getIntExtra(EXTRA_VOICE_INDEX, 0)
        episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
        episodeNumber = intent.getIntExtra(EXTRA_TMDB_EPISODE,
            intent.getIntExtra(EXTRA_EPISODE_NUMBER, episodeIndex + 1))
        isToloka = intent.getStringExtra(EXTRA_SOURCE_TYPE) == SOURCE_TOLOKA
        isTmdbAnitube = intent.getStringExtra(EXTRA_SOURCE_TYPE) == SOURCE_ANITUBE_TMDB
        val loadingArtwork = intent.getStringExtra(EXTRA_TMDB_LOGO_URL)
            ?.takeIf(String::isNotBlank)
            ?: intent.getStringExtra(EXTRA_TMDB_POSTER_URL)?.takeIf(String::isNotBlank)
        if (loadingArtwork != null) binding.playerLoadingLogo.load(loadingArtwork)
        configureNowPlayingLabel()
        tmdbResumeProgress = intent.getFloatExtra(EXTRA_TMDB_RESUME_PROGRESS, 0f)
        torrentResumePositionMs = intent.getLongExtra(EXTRA_TORRENT_RESUME_POSITION_MS, 0L)
        if (isTmdbAnitube) {
            val urls = intent.getStringArrayListExtra(EXTRA_BACKUP_EMBED_URLS).orEmpty()
            val voiceIndices = intent.getIntArrayExtra(EXTRA_BACKUP_VOICE_INDICES) ?: intArrayOf()
            val episodeIndices = intent.getIntArrayExtra(EXTRA_BACKUP_EPISODE_INDICES) ?: intArrayOf()
            urls.forEachIndexed { index, embedUrl ->
                if (index in voiceIndices.indices && index in episodeIndices.indices)
                    backupSources.addLast(AnitubeStreamFallback(embedUrl,
                        voiceIndices[index], episodeIndices[index]))
            }
        }

        if (url.isNullOrBlank()) {
            Log.w("NextEp", "finish(): url is null or blank")
            showStatus("⚠ Не вказано URL потоку", "")
            finish()
            return
        }

        binding.btnNextEpisode.visibility = View.GONE
        showBufferingIndicator()
        initPlayer(url)
        observeNetwork()
    }

    private fun initPlayer(url: String) {
        val mediaSource = if (isToloka) {
            val extractors = DefaultExtractorsFactory()
                .setSubtitleParserFactory(TolerantSubtitleParserFactory())
            ProgressiveMediaSource.Factory(mediaDataSourceFactory, extractors)
                .createMediaSource(MediaItem.fromUri(url))
        } else {
            HlsMediaSource.Factory(mediaDataSourceFactory).createMediaSource(MediaItem.fromUri(url))
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()

        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this))
            .setLoadControl(loadControl)
            .build().also { exo ->
            binding.playerView.player = exo
            controlsController?.attach(Media3PlayerTransport(exo))
            trackSelectionMenu.bind(exo, binding.playerControls) {
                controlsController?.show()
                binding.playerControls.collapseTrackActions()
            }

            exo.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    trackSelectionMenu.update(tracks)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    Log.d("Player", "state=$state") // 1 IDLE, 2 BUFFERING, 3 READY, 4 ENDED
                    when (state) {
                        Player.STATE_READY   -> {
                            streamRetryJob?.cancel()
                            if (networkInterrupted) {
                                exo.pause()
                                showStatus("Відсутнє підключення до ${if (isToloka) "TorrServe" else "інтернету"}", "")
                                return
                            }
                            torrentBufferingSinceMs = 0L
                            torrentBufferPromptShown = false
                            torrentRetrySeekPending = false
                            torrentBufferingWatch?.let(mainHandler::removeCallbacks)
                            torrentBufferingWatch = null
                            torrentRecoveryDialog?.dismiss()
                            hideBufferingIndicator()
                            binding.statusOverlay.visibility = View.GONE
                            if (isToloka) {
                                if (!torrentResumeApplied && torrentResumePositionMs > 0 && exo.duration > 0) {
                                    exo.seekTo(torrentResumePositionMs.coerceAtMost(exo.duration - 1))
                                    torrentResumeApplied = true
                                }
                            } else {
                                if (isTmdbAnitube && !tmdbResumeApplied && tmdbResumeProgress in 0.01f..0.98f && exo.duration > 0) {
                                    exo.seekTo((exo.duration * tmdbResumeProgress).toLong())
                                    tmdbResumeApplied = true
                                }
                            }
                            startProgressReporting()
                            lookupIntroSegments(exo)
                            mainHandler.removeCallbacks(updateIntroSkip)
                            mainHandler.post(updateIntroSkip)
                        }
                        Player.STATE_BUFFERING -> {
                            showBufferingIndicator()
                            if (isToloka) watchTorrentBuffering(exo)
                        }
                        Player.STATE_ENDED   -> {
                            setKeepScreenOn(false)
                            hideBufferingIndicator()
                            val current = exo.currentPosition
                            val duration = exo.duration
                            if (duration > 0 && current >= duration - 3000) {
                                if (isTmdbAnitube) saveTmdbAnitubeProgress(1f, current, duration)
                                else if (isToloka) saveTolokaProgress(current, duration)
                                if (!canGoNextEpisode() || !launchNextEpisode()) {
                                    showStatus("✓ Завершено", if (isToloka) "" else url)
                                    finish()
                                }
                            } else {
                                Log.w(TAG, "Ignored premature STATE_ENDED at current=$current, duration=$duration")
                            }
                        }
                        Player.STATE_IDLE    -> {}
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    setKeepScreenOn(playWhenReady && exo.playbackState != Player.STATE_ENDED)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (!isToloka || reason != Player.DISCONTINUITY_REASON_SEEK) return
                    if (torrentRetrySeekPending) {
                        torrentRetrySeekPending = false
                        return
                    }
                    torrentLastSeekAtMs = android.os.SystemClock.elapsedRealtime()
                    torrentBufferingSinceMs = 0L
                    torrentRecoveryAttempts = 0
                    torrentBufferingWatch?.let(mainHandler::removeCallbacks)
                    torrentBufferingWatch = null
                    Log.i(TAG, "Torrent seek from=${oldPosition.positionMs} to=${newPosition.positionMs}")
                    if (exo.playbackState == Player.STATE_BUFFERING) watchTorrentBuffering(exo)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (networkInterrupted) return
                    val failure = classifyPlaybackError(error)
                    Log.e(TAG, "Playback failure=$failure code=${error.errorCodeName}", error)
                    if (failure == StreamFailure.TRANSIENT && scheduleStreamRetry(exo)) return
                    if (isToloka) {
                        Log.e("Player", "Torrent playback: ${error.errorCodeName} / ${error.message}", error)
                        val matroskaReadFailed = generateSequence(error as Throwable) { it.cause }
                            .any { it is IllegalStateException &&
                                it.message?.contains("No valid varint length mask found") == true }
                        if (failure == StreamFailure.DECODER) {
                            showStatus("✗ Пристрій не підтримує відеоформат цієї роздачі (можливо, HEVC). Спробуйте іншу роздачу з H.264 або зовнішній плеєр.", "")
                        } else if (matroskaReadFailed && torrentRecoveryAttempts == 0) {
                            Log.w(TAG, "Matroska read failed after seek; retrying from an earlier position")
                            retryTorrentAtPosition(exo, rewindMs = 5_000L)
                        } else {
                            torrentBufferPromptShown = true
                            showTorrentRecoveryDialog(exo, failureMessage(failure))
                        }
                    } else {
                        Log.e("Player", "ExoPlayer: ${error.errorCodeName} / ${error.message}", error)
                        if (retryingBackup) return
                        if (isTmdbAnitube && !tmdbAnitubeStarted && backupSources.isNotEmpty()) {
                            retryAnitubeBackup()
                            return
                        }
                        val msg = "✗ ${failureMessage(failure)}"
                        setKeepScreenOn(false)
                        showStatus(msg, url)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    stablePlaybackJob?.cancel()
                    if (isPlaying) {
                        stablePlaybackJob = lifecycleScope.launch {
                            delay(10_000)
                            if (player === exo && exo.isPlaying) streamRetryCount = 0
                        }
                        if (isTmdbAnitube && !tmdbAnitubeStarted) {
                            saveTmdbAnitubeProgress(tmdbResumeProgress,
                                exo.currentPosition, exo.duration)
                            tmdbAnitubeStarted = true
                        }
                        if (isToloka && !tolokaPlaybackMarked) {
                            val topicId = intent.getStringExtra(EXTRA_TOLOKA_TOPIC_ID)
                            val fileId = intent.getIntExtra(EXTRA_TOLOKA_FILE_ID, -1)
                            val type = TmdbMediaType.entries.firstOrNull {
                                it.path == intent.getStringExtra(EXTRA_TMDB_TYPE)
                            }
                            val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1)
                            val tmdbTitle = intent.getStringExtra(EXTRA_TMDB_TITLE)
                            if (type != null && tmdbId > 0 && !tmdbTitle.isNullOrBlank() &&
                                topicId?.matches(Regex("[0-9]+")) == true && fileId > 0) {
                                TolokaPlaybackStore(this@PlayerActivity)
                                    .markTmdbOpened(type.path, tmdbId, topicId, fileId)
                                val history = TmdbWatchHistoryStore(this@PlayerActivity)
                                history.record(TmdbTitle(
                                    tmdbId, type, tmdbTitle,
                                    intent.getStringExtra(EXTRA_TMDB_ORIGINAL_TITLE),
                                    intent.getIntExtra(EXTRA_TMDB_YEAR, 0).takeIf { it > 0 },
                                    intent.getStringExtra(EXTRA_TMDB_POSTER_URL), null,
                                    intent.getDoubleExtra(EXTRA_TMDB_RATING, Double.NaN)
                                        .takeIf(Double::isFinite),
                                    genreIds = intent.getIntArrayExtra(EXTRA_TMDB_GENRE_IDS)
                                        ?.toSet().orEmpty(),
                                ))
                                history.savePlaybackSelection(type, tmdbId,
                                    ua.any2b.tvplayer.data.TmdbPlaybackSelection(
                                        ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT,
                                        topicId = topicId, fileId = fileId,
                                        fileName = intent.getStringExtra(EXTRA_TOLOKA_FILE_NAME)))
                                tolokaPlaybackMarked = true
                            }
                        }
                        binding.statusOverlay.postDelayed({
                            binding.statusOverlay.visibility = View.GONE
                        }, 3000)
                    }
                }
            })

            exo.setMediaSource(mediaSource)
            exo.playWhenReady = true
            exo.prepare()
        }
    }

    private fun watchTorrentBuffering(exo: ExoPlayer) {
        if (torrentBufferingSinceMs == 0L) torrentBufferingSinceMs = android.os.SystemClock.elapsedRealtime()
        if (torrentBufferPromptShown || torrentBufferingWatch != null) return
        val startedAt = torrentBufferingSinceMs
        val watch = object : Runnable {
            override fun run() {
                torrentBufferingWatch = null
                if (isFinishing || isDestroyed || player !== exo || exo.playbackState != Player.STATE_BUFFERING) return
                val now = android.os.SystemClock.elapsedRealtime()
                val elapsed = now - startedAt
                val threshold = when {
                    torrentRecoveryAttempts > 0 -> TORRENT_RETRY_BUFFER_TIMEOUT_MS
                    torrentLastSeekAtMs > 0L &&
                        now - torrentLastSeekAtMs < TORRENT_SEEK_BUFFER_TIMEOUT_MS ->
                        TORRENT_SEEK_BUFFER_TIMEOUT_MS
                    else -> TORRENT_BUFFER_TIMEOUT_MS
                }
                if (elapsed >= threshold) {
                    Log.w(TAG, "Torrent buffering prolonged ${elapsed}ms " +
                        "afterSeek=${torrentLastSeekAtMs > 0L} retry=$torrentRecoveryAttempts " +
                        "position=${exo.currentPosition} buffered=${exo.bufferedPosition}")
                    if (scheduleStreamRetry(exo)) {
                        torrentBufferingSinceMs = 0L
                    } else {
                        torrentBufferPromptShown = true
                        showTorrentRecoveryDialog(exo, "Потік довго буферизується")
                    }
                } else {
                    torrentBufferingWatch = this
                    mainHandler.postDelayed(this, threshold - elapsed)
                }
            }
        }
        torrentBufferingWatch = watch
        mainHandler.postDelayed(watch, 5_000L)
    }

    private fun retryTorrentAtPosition(exo: ExoPlayer, rewindMs: Long = 0L) {
        val position = (exo.currentPosition - rewindMs).coerceAtLeast(0L)
        torrentRecoveryAttempts++
        torrentBufferingSinceMs = 0L
        torrentRetrySeekPending = true
        Log.i(TAG, "Retrying torrent stream at position=$position")
        exo.stop()
        exo.prepare()
        exo.seekTo(position)
        exo.playWhenReady = true
    }

    private enum class StreamFailure { TRANSIENT, AUTH, MISSING, PARSING, SUBTITLE, DECODER, OTHER }

    private fun classifyPlaybackError(error: PlaybackException): StreamFailure {
        if (generateSequence(error as Throwable) { it.cause }.any { cause ->
                cause.stackTrace.any { it.className == "androidx.media3.extractor.text.ssa.SsaParser" }
            }) return StreamFailure.SUBTITLE
        val http = generateSequence(error as Throwable) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (http != null) return when (http.responseCode) {
            401, 403 -> StreamFailure.AUTH
            404, 410, 416 -> StreamFailure.MISSING
            in 500..599, 408, 429 -> StreamFailure.TRANSIENT
            else -> StreamFailure.OTHER
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> StreamFailure.TRANSIENT
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> StreamFailure.MISSING
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> StreamFailure.PARSING
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> StreamFailure.DECODER
            else -> StreamFailure.OTHER
        }
    }

    private fun failureMessage(failure: StreamFailure): String = when (failure) {
        StreamFailure.AUTH -> "Джерело відхилило авторизацію"
        StreamFailure.MISSING -> "Файл або вибрана позиція недоступні в роздачі"
        StreamFailure.PARSING -> "Пошкоджений або непідтримуваний медіаконтейнер"
        StreamFailure.SUBTITLE -> "Помилка вбудованих субтитрів цієї роздачі"
        StreamFailure.DECODER -> "Пристрій не підтримує формат цієї роздачі"
        StreamFailure.TRANSIENT -> "Не вдалося відновити з’єднання з потоком"
        StreamFailure.OTHER -> "Помилка відтворення потоку"
    }

    private fun scheduleStreamRetry(exo: ExoPlayer): Boolean {
        if (streamRetryCount >= MAX_STREAM_RETRIES || streamRetryJob?.isActive == true) return false
        val position = exo.currentPosition.coerceAtLeast(0L)
        val shouldPlay = exo.playWhenReady
        val base = (1_000L shl streamRetryCount).coerceAtMost(8_000L)
        val waitMs = Random.nextLong(base / 2, base + base / 2 + 1)
        streamRetryCount++
        Log.i(TAG, "Stream retry $streamRetryCount/$MAX_STREAM_RETRIES in ${waitMs}ms at $position")
        streamRetryJob = lifecycleScope.launch {
            delay(waitMs)
            if (isFinishing || isDestroyed || player !== exo || networkInterrupted) return@launch
            exo.stop()
            exo.prepare()
            exo.seekTo(position)
            exo.playWhenReady = shouldPlay
        }
        return true
    }

    private fun observeNetwork() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { checkStreamNetwork() }
            override fun onAvailable(network: Network) { checkStreamNetwork() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                checkStreamNetwork()
            }
        }
        networkCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } else {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback,
            )
        }
    }

    private fun checkStreamNetwork() {
        networkCheckJob?.cancel()
        networkCheckJob = lifecycleScope.launch {
            delay(700)
            val reachable = if (isToloka) {
                val base = SettingsManager(this@PlayerActivity).torrServerUrl()
                val auth = TorrServerAuthStore(this@PlayerActivity).read()
                    ?.let { TorrServerBasicAuth(it.username, it.password) }
                try {
                    torrServerProbe.version(base, auth, timeoutSeconds = 2)
                    true
                } catch (_: Exception) { false }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                } else {
                    @Suppress("DEPRECATION")
                    connectivityManager.activeNetworkInfo?.isConnected == true
                }
            }
            val exo = player ?: return@launch
            if (!reachable && !networkInterrupted) {
                networkInterrupted = true
                resumeAfterNetwork = exo.playWhenReady
                streamRetryJob?.cancel()
                exo.pause()
                showStatus("Відсутнє підключення до ${if (isToloka) "TorrServe" else "інтернету"}", "")
            } else if (reachable && networkInterrupted) {
                networkInterrupted = false
                val position = exo.currentPosition.coerceAtLeast(0L)
                streamRetryCount = 0
                exo.stop()
                exo.prepare()
                exo.seekTo(position)
                exo.playWhenReady = resumeAfterNetwork
                showBufferingIndicator()
            }
            if (!reachable && networkInterrupted && !isFinishing && !isDestroyed) {
                delay(3_000)
                checkStreamNetwork()
            }
        }
    }

    private fun showTorrentRecoveryDialog(exo: ExoPlayer, reason: String) {
        if (isFinishing || isDestroyed || torrentRecoveryDialog?.isShowing == true) return
        hideBufferingIndicator()
        torrentRecoveryDialog = AlertDialog.Builder(this)
            .setTitle("Не вдається відтворити")
            .setMessage("$reason. Можна повторити підключення або повернутися до списку й вибрати іншу роздачу.")
            .setPositiveButton("Повторити") { _, _ ->
                torrentBufferingWatch?.let(mainHandler::removeCallbacks)
                torrentBufferingWatch = null
                torrentBufferingSinceMs = 0L
                torrentBufferPromptShown = false
                retryTorrentAtPosition(exo)
            }
            .setNegativeButton("Змінити роздачу") { _, _ -> finish() }
            .setOnCancelListener {
                torrentBufferPromptShown = false
                torrentBufferingSinceMs = 0L
                if (exo.playbackState == Player.STATE_BUFFERING) watchTorrentBuffering(exo)
            }
            .create().also { dialog ->
                dialog.setOnDismissListener { if (torrentRecoveryDialog === dialog) torrentRecoveryDialog = null }
                dialog.show()
            }
    }

    private fun retryAnitubeBackup() {
        if (retryingBackup) return
        retryingBackup = true
        lifecycleScope.launch {
            while (backupSources.isNotEmpty()) {
                val source = backupSources.removeFirst()
                showStatus("⏳ Пробую резервний варіант озвучення…", "")
                try {
                    val stream = withContext(Dispatchers.IO) {
                        streamResolver.resolve(source.embedUrl)
                    }
                    progressJob?.cancel()
                    player?.release()
                    player = null
                    voiceIndex = source.voiceIndex
                    episodeIndex = source.episodeIndex
                    tmdbResumeApplied = false
                    retryingBackup = false
                    initPlayer(stream.streamUrl)
                    return@launch
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "AniTube backup failed", error)
                }
            }
            retryingBackup = false
            showStatus("✗ Жоден варіант цієї студії не запустився", "")
        }
    }

    private fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(4000)
                val exo = player ?: continue
                val current = exo.currentPosition
                val duration = exo.duration
                if (isTmdbAnitube && duration > 0) {
                    val progress = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    saveTmdbAnitubeProgress(progress, current, duration)
                } else if (isToloka && duration > 0) {
                    saveTolokaProgress(current, duration)
                }
            }
        }
    }

    private fun lookupIntroSegments(exo: ExoPlayer) {
        if (introLookupStarted) return
        val type = TmdbMediaType.entries.firstOrNull {
            it.path == intent.getStringExtra(EXTRA_TMDB_TYPE)
        } ?: return
        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it > 0 } ?: return
        val durationMs = exo.duration.takeIf { it > 0L } ?: return
        val season = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it >= 0 }
        val episode = intent.getIntExtra(EXTRA_TMDB_EPISODE, -1).takeIf { it >= 0 }
        if (type == TmdbMediaType.TV && (season == null || episode == null)) return

        introLookupStarted = true
        lifecycleScope.launch {
            introSegments = introDbClient.findSegments(tmdbId, type, durationMs, season, episode)
            updateSkipIntroButton()
        }
    }

    private fun configureNowPlayingLabel() {
        val type = intent.getStringExtra(EXTRA_TMDB_TYPE)
        if (type != TmdbMediaType.TV.path) return
        val title = intent.getStringExtra(EXTRA_TMDB_TITLE)?.takeIf(String::isNotBlank) ?: return
        val season = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it >= 0 }
        val episode = intent.getIntExtra(EXTRA_TMDB_EPISODE, episodeNumber)
        val episodeLabel = if (season != null) {
            "$season сезон · $episode серія"
        } else {
            "$episode серія"
        }
        binding.playerControls.nowPlayingLabel.text = "$title  •  $episodeLabel"
        binding.playerControls.nowPlayingRow.visibility = View.VISIBLE
        if (canGoNextEpisode()) {
            binding.playerControls.nextEpisodeButton.text = "${episode + 1} серія"
            binding.playerControls.nextEpisodeButton.contentDescription =
                "Перейти до ${episode + 1} серії"
            binding.playerControls.nextEpisodeButton.visibility = View.VISIBLE
            binding.playerControls.nextEpisodeButton.setOnClickListener { launchNextEpisode() }
        }
    }

    private fun updateSkipIntroButton() {
        val exo = player ?: return
        val position = exo.currentPosition
        val isTv = intent.getStringExtra(EXTRA_TMDB_TYPE) == TmdbMediaType.TV.path
        val creditsStartMs = if (isTv) introSegments.credits.minOfOrNull { it.startMs }
            ?.takeIf { it > 0L && (exo.duration <= 0L || it < exo.duration) } else null
        val nextStartMs = creditsStartMs ?: exo.duration.takeIf { it > 0L }
            ?.let { (it - NEXT_EPISODE_FALLBACK_LEAD_MS).coerceAtLeast(0L) }
        val canGoNext = canGoNextEpisode()
        if (!nextEpisodePreloadStarted && canGoNext && nextStartMs != null &&
            position >= (nextStartMs - NEXT_EPISODE_PRELOAD_LEAD_MS).coerceAtLeast(0L)) {
            nextEpisodePreloadStarted = true
            prefetchNextEpisodeChunk()
        }

        val intro = introSegments.intros.firstOrNull {
            position >= it.startMs && position < it.endMs
        }
        when {
            intro != null && !introPromptDismissed -> showPlayerPrompt(PlayerPrompt.SKIP_INTRO)
            nextStartMs != null && position >= nextStartMs && canGoNext &&
                !nextEpisodePromptDismissed -> showPlayerPrompt(PlayerPrompt.NEXT_EPISODE)
            else -> hidePlayerPrompt()
        }
    }

    private fun canGoNextEpisode(): Boolean =
        intent.getStringExtra(EXTRA_TMDB_TYPE) == TmdbMediaType.TV.path &&
            intent.getIntExtra(EXTRA_TMDB_SEASON, -1) > 0 &&
            intent.getIntExtra(EXTRA_TMDB_EPISODE, -1) > 0 &&
            when {
                isToloka -> !intent.getStringExtra(EXTRA_TOLOKA_TOPIC_ID).isNullOrBlank() &&
                    intent.getBooleanExtra(EXTRA_NEXT_EPISODE_AVAILABLE, false)
                isTmdbAnitube -> intent.getBooleanExtra(EXTRA_NEXT_EPISODE_AVAILABLE, false)
                else -> false
            }

    private fun showPlayerPrompt(prompt: PlayerPrompt) {
        if (activePlayerPrompt == prompt) return
        hidePlayerPrompt()
        activePlayerPrompt = prompt
        binding.btnSkipIntro.text = when (prompt) {
            PlayerPrompt.SKIP_INTRO -> "ПРОПУСТИТИ ІНТРО"
            PlayerPrompt.NEXT_EPISODE -> "Перейти до наступної"
        }
        binding.btnSkipIntro.visibility = View.VISIBLE
        binding.btnSkipIntro.requestFocus()
        val timeout = Runnable {
            when (activePlayerPrompt) {
                PlayerPrompt.SKIP_INTRO -> introPromptDismissed = true
                PlayerPrompt.NEXT_EPISODE -> nextEpisodePromptDismissed = true
                null -> Unit
            }
            hidePlayerPrompt()
        }
        promptDismissRunnable = timeout
        mainHandler.postDelayed(timeout, PLAYER_PROMPT_TIMEOUT_MS)
    }

    private fun hidePlayerPrompt() {
        promptDismissRunnable?.let(mainHandler::removeCallbacks)
        promptDismissRunnable = null
        if (binding.btnSkipIntro.visibility == View.VISIBLE) binding.playerView.requestFocus()
        binding.btnSkipIntro.visibility = View.GONE
        activePlayerPrompt = null
    }

    private fun confirmActivePrompt() {
        when (activePlayerPrompt) {
            PlayerPrompt.SKIP_INTRO -> {
                val position = player?.currentPosition ?: return
                val segment = introSegments.intros.firstOrNull {
                    position >= it.startMs && position < it.endMs
                } ?: return
                introPromptDismissed = true
                hidePlayerPrompt()
                player?.seekTo(segment.endMs)
            }
            PlayerPrompt.NEXT_EPISODE -> launchNextEpisode()
            null -> Unit
        }
    }

    private fun prefetchNextEpisodeChunk() {
        if (!isToloka) return
        val url = intent.getStringExtra(EXTRA_NEXT_STREAM_URL)?.takeIf(String::isNotBlank) ?: return
        nextEpisodePreloadJob = lifecycleScope.launch(Dispatchers.IO) {
            val writer = CacheWriter(
                mediaDataSourceFactory.createDataSource(),
                DataSpec.Builder().setUri(url).setPosition(0L).setLength(NEXT_EPISODE_PREFETCH_BYTES).build(),
                ByteArray(64 * 1024),
                null,
            )
            nextEpisodeCacheWriter = writer
            try {
                writer.cache()
            } catch (error: Exception) {
                if (error !is CancellationException)
                    Log.d(TAG, "Next episode prefetch unavailable", error)
            } finally {
                nextEpisodeCacheWriter = null
            }
        }
    }

    private fun launchNextEpisode(): Boolean {
        if (nextEpisodeTransitioning) return true
        if (!canGoNextEpisode()) return false
        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it > 0 } ?: return false
        val type = intent.getStringExtra(EXTRA_TMDB_TYPE)
            ?.takeIf { it == TmdbMediaType.TV.path } ?: return false
        val nextEpisode = (intent.getIntExtra(EXTRA_TMDB_EPISODE, episodeNumber) + 1)
        val season = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it > 0 } ?: return false
        val source = when {
            isToloka -> ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT
            isTmdbAnitube -> ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE
            else -> return false
        }
        val nextIntent = android.content.Intent(this, TmdbDetailsActivity::class.java).apply {
            putExtra(TmdbDetailsActivity.EXTRA_TMDB_ID, tmdbId)
            putExtra(TmdbDetailsActivity.EXTRA_TMDB_TYPE, type)
            putExtra(TmdbDetailsActivity.EXTRA_TMDB_SEASON, season)
            putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_EPISODE, nextEpisode)
            putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_SOURCE, source.name)
            putExtra(TmdbDetailsActivity.EXTRA_RETURN_TO_WATCHLIST, true)
            putExtra(TmdbDetailsActivity.EXTRA_PLAYBACK_ONLY, true)
            if (source == ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT) {
                intent.getStringExtra(EXTRA_TOLOKA_TOPIC_ID)?.let {
                    putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_TORRENT_TOPIC, it)
                }
            } else {
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_SEASON, season)
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_RELEASE,
                    intent.getIntExtra(EXTRA_TMDB_ANITUBE_RELEASE, -1))
                putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_VOICE, voiceIndex)
                intent.getStringExtra(EXTRA_TMDB_ANITUBE_VOICE_NAME)?.let {
                    putExtra(TmdbDetailsActivity.EXTRA_AUTO_PLAY_ANITUBE_VOICE_NAME, it)
                }
            }
        }
        try {
            nextEpisodeTransitioning = true
            startActivity(nextIntent)
            finish()
            return true
        } catch (error: Exception) {
            nextEpisodeTransitioning = false
            Log.e(TAG, "Could not open next episode", error)
            nextEpisodePromptDismissed = true
            hidePlayerPrompt()
            return false
        }
    }

    private fun saveTmdbAnitubeProgress(
        progress: Float,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        val type = TmdbMediaType.entries.firstOrNull {
            it.path == intent.getStringExtra(EXTRA_TMDB_TYPE)
        } ?: return
        val id = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it > 0 } ?: return
        val title = intent.getStringExtra(EXTRA_TMDB_TITLE)?.takeIf(String::isNotBlank) ?: return
        TmdbWatchHistoryStore(this).saveEpisodeProgress(
            TmdbTitle(
                id, type, title,
                intent.getStringExtra(EXTRA_TMDB_ORIGINAL_TITLE),
                intent.getIntExtra(EXTRA_TMDB_YEAR, 0).takeIf { it > 0 },
                intent.getStringExtra(EXTRA_TMDB_POSTER_URL), null,
                intent.getDoubleExtra(EXTRA_TMDB_RATING, Double.NaN).takeIf(Double::isFinite),
                genreIds = intent.getIntArrayExtra(EXTRA_TMDB_GENRE_IDS)
                    ?.toSet().orEmpty(),
            ),
            voiceIndex, episodeIndex, episodeNumber, progress,
            seasonNumber = intent.getIntExtra(EXTRA_TMDB_SEASON, 1).coerceAtLeast(1),
            positionMs = positionMs, durationMs = durationMs,
        )
    }

    private fun saveTolokaProgress(positionMs: Long, durationMs: Long) {
        val title = tmdbTitleFromIntent() ?: return
        val topicId = intent.getStringExtra(EXTRA_TOLOKA_TOPIC_ID) ?: return
        val fileId = intent.getIntExtra(EXTRA_TOLOKA_FILE_ID, -1)
        val episode = intent.getStringExtra(EXTRA_TOLOKA_FILE_NAME)?.let {
            TolokaReleaseMetadataParser.parse(it)
        }?.takeIf { it.episodeStart != null &&
            (it.episodeEnd == null || it.episodeEnd == it.episodeStart) }
        TmdbWatchHistoryStore(this).saveTorrentProgress(title, topicId, fileId,
            positionMs, durationMs, episode?.episodeStart,
            episode?.season ?: intent.getIntExtra(EXTRA_TMDB_SEASON, 0).takeIf { it > 0 }
            ?: title.matchedSeasonNumber)
    }

    private fun tmdbTitleFromIntent(): TmdbTitle? {
        val type = TmdbMediaType.entries.firstOrNull {
            it.path == intent.getStringExtra(EXTRA_TMDB_TYPE)
        } ?: return null
        val id = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it > 0 } ?: return null
        val title = intent.getStringExtra(EXTRA_TMDB_TITLE)?.takeIf(String::isNotBlank) ?: return null
        return TmdbTitle(
            id, type, title,
            intent.getStringExtra(EXTRA_TMDB_ORIGINAL_TITLE),
            intent.getIntExtra(EXTRA_TMDB_YEAR, 0).takeIf { it > 0 },
            intent.getStringExtra(EXTRA_TMDB_POSTER_URL), null,
            intent.getDoubleExtra(EXTRA_TMDB_RATING, Double.NaN).takeIf(Double::isFinite),
            genreIds = intent.getIntArrayExtra(EXTRA_TMDB_GENRE_IDS)?.toSet().orEmpty(),
        )
    }

    private fun showStatus(status: String, url: String) {
        hideBufferingIndicator()
        Log.i(TAG, status)
        binding.statusOverlay.visibility = View.VISIBLE
        binding.tvStatus.text = status
        binding.tvUrl.text = if (url.length > 80) "…${url.takeLast(60)}" else url
    }

    private fun setKeepScreenOn(keepOn: Boolean) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (keepOn) window.addFlags(flag) else window.clearFlags(flag)
    }

    private fun showBufferingIndicator() {
        binding.statusOverlay.visibility = View.GONE
        binding.playerLoadingLogo.visibility = View.VISIBLE
    }

    private fun hideBufferingIndicator() {
        if (::binding.isInitialized) binding.playerLoadingLogo.visibility = View.GONE
    }

    override fun onPause() {
        setKeepScreenOn(false)
        mainHandler.removeCallbacks(updateIntroSkip)
        saveCurrentProgress()
        super.onPause()
        player?.pause()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (binding.playerControls.nextEpisodeButton.hasFocus() &&
            event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            binding.playerControls.nextEpisodeButton.performClick()
            return true
        }
        val audioButton = binding.playerControls.findViewById<View>(R.id.btnAudioTracks)
        val subtitleButton = binding.playerControls.findViewById<View>(R.id.btnSubtitleTracks)
        val focusedTrackAction = audioButton.hasFocus() || subtitleButton.hasFocus()
        if (focusedTrackAction && event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            (if (audioButton.hasFocus()) audioButton else subtitleButton).performClick()
            return true
        }
        if (binding.btnSkipIntro.visibility == View.VISIBLE && binding.btnSkipIntro.hasFocus() &&
            event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            binding.btnSkipIntro.performClick()
            return true
        }
        if (controlsController?.onRemoteKey(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (!networkInterrupted) player?.play()
        setKeepScreenOn(player?.let {
            it.playWhenReady && it.playbackState != Player.STATE_ENDED
        } == true)
        if (introSegments.intros.isNotEmpty() || introSegments.credits.isNotEmpty()) {
            mainHandler.removeCallbacks(updateIntroSkip)
            mainHandler.post(updateIntroSkip)
        }
    }

    override fun onDestroy() {
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCheckJob?.cancel()
        streamRetryJob?.cancel()
        stablePlaybackJob?.cancel()
        setKeepScreenOn(false)
        mainHandler.removeCallbacks(updateIntroSkip)
        promptDismissRunnable?.let(mainHandler::removeCallbacks)
        nextEpisodeCacheWriter?.cancel()
        nextEpisodePreloadJob?.cancel()
        saveCurrentProgress()
        progressJob?.cancel()
        controlsController?.release()
        controlsController = null
        super.onDestroy()
        player?.release()
        player = null
        okHttpClient.dispatcher.executorService.shutdown()
    }

    private fun saveCurrentProgress() {
        val exo = player ?: return
        if (exo.duration <= 0) return
        if (isToloka) saveTolokaProgress(exo.currentPosition, exo.duration)
        else if (isTmdbAnitube) {
            saveTmdbAnitubeProgress((exo.currentPosition.toFloat() / exo.duration)
                .coerceIn(0f, 1f), exo.currentPosition, exo.duration)
        }
    }
}
