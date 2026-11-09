package ua.any2b.tvplayer

import android.app.AlertDialog
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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.tvplayer.data.AnitubeStreamFallback
import ua.any2b.tvplayer.ui.Media3PlayerTransport
import ua.any2b.tvplayer.ui.PlayerControlsController
import ua.any2b.tvplayer.ui.PlayerTrackSelectionMenu
import ua.any2b.tvplayer.databinding.ActivityPlayerBinding
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TvPlayer"
        private const val MIN_BUFFER_MS = 50_000
        private const val MAX_BUFFER_MS = 120_000
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
        private const val NEXT_EPISODE_PRELOAD_LEAD_MS = 20_000L
        private const val NEXT_EPISODE_PREFETCH_BYTES = 2L * 1024L * 1024L
        private const val PLAYER_PROMPT_TIMEOUT_MS = 13_000L
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
        const val EXTRA_TMDB_ORIGINAL_TITLE = "TMDB_ORIGINAL_TITLE"
        const val EXTRA_TMDB_YEAR = "TMDB_YEAR"
        const val EXTRA_TMDB_POSTER_URL = "TMDB_POSTER_URL"
        const val EXTRA_TMDB_RATING = "TMDB_RATING"
        const val EXTRA_TMDB_GENRE_IDS = "TMDB_GENRE_IDS"
        const val EXTRA_TMDB_SEASON = "TMDB_SEASON"
        const val EXTRA_TMDB_EPISODE = "TMDB_EPISODE"
        const val EXTRA_NEXT_STREAM_URL = "NEXT_STREAM_URL"
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
    private var torrentBufferPromptShown = false
    private var torrentBufferingWatch: Runnable? = null
    private var estimatedBandwidthBps = 0L
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
        showStatus("⏳ Підключення до потоку…", if (isToloka) "" else url)
        initPlayer(url)
    }

    private fun initPlayer(url: String) {
        val mediaSource = if (isToloka) {
            ProgressiveMediaSource.Factory(mediaDataSourceFactory).createMediaSource(MediaItem.fromUri(url))
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

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().also { exo ->
            binding.playerView.player = exo
            controlsController?.attach(Media3PlayerTransport(exo))
            trackSelectionMenu.bind(exo, binding.playerControls)

            exo.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    trackSelectionMenu.update(tracks)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    Log.d("Player", "state=$state") // 1 IDLE, 2 BUFFERING, 3 READY, 4 ENDED
                    when (state) {
                        Player.STATE_READY   -> {
                            torrentBufferingSinceMs = 0L
                            torrentBufferingWatch?.let(mainHandler::removeCallbacks)
                            torrentBufferingWatch = null
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
                                showStatus("✓ Завершено", if (isToloka) "" else url)
                                if (isTmdbAnitube) saveTmdbAnitubeProgress(1f, current, duration)
                                finish()
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

                override fun onPlayerError(error: PlaybackException) {
                    if (isToloka) {
                        Log.e("Player", "Torrent playback: ${error.errorCodeName} / ${error.message}", error)
                        if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES) {
                            showStatus("✗ Пристрій не підтримує відеоформат цієї роздачі (можливо, HEVC). Спробуйте іншу роздачу з H.264 або зовнішній плеєр.", "")
                        } else {
                            torrentBufferPromptShown = true
                            showTorrentRecoveryDialog(exo, "Помилка потоку: ${error.errorCodeName}")
                        }
                    } else {
                        Log.e("Player", "ExoPlayer: ${error.errorCodeName} / ${error.message}", error)
                        if (retryingBackup) return
                        if (isTmdbAnitube && !tmdbAnitubeStarted && backupSources.isNotEmpty()) {
                            retryAnitubeBackup()
                            return
                        }
                        val msg = "✗ Помилка: ${error.errorCodeName} — ${error.message}"
                        setKeepScreenOn(false)
                        showStatus(msg, url)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
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
                                TmdbWatchHistoryStore(this@PlayerActivity).record(TmdbTitle(
                                    tmdbId, type, tmdbTitle,
                                    intent.getStringExtra(EXTRA_TMDB_ORIGINAL_TITLE),
                                    intent.getIntExtra(EXTRA_TMDB_YEAR, 0).takeIf { it > 0 },
                                    intent.getStringExtra(EXTRA_TMDB_POSTER_URL), null,
                                    intent.getDoubleExtra(EXTRA_TMDB_RATING, Double.NaN)
                                        .takeIf(Double::isFinite),
                                    genreIds = intent.getIntArrayExtra(EXTRA_TMDB_GENRE_IDS)
                                        ?.toSet().orEmpty(),
                                ))
                                tolokaPlaybackMarked = true
                            }
                        }
                        binding.statusOverlay.postDelayed({
                            binding.statusOverlay.visibility = View.GONE
                        }, 3000)
                    }
                }
            })

            exo.addAnalyticsListener(object : AnalyticsListener {
                override fun onBandwidthEstimate(
                    eventTime: AnalyticsListener.EventTime,
                    totalLoadTimeMs: Int,
                    totalBytesTransferred: Long,
                    bitrateEstimate: Long,
                ) {
                    estimatedBandwidthBps = bitrateEstimate.coerceAtLeast(0L)
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
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                val lowBandwidth = estimatedBandwidthBps in 1 until 1_500_000L
                val threshold = if (lowBandwidth || estimatedBandwidthBps == 0L) 15_000L else 30_000L
                if (elapsed >= threshold) {
                    torrentBufferPromptShown = true
                    Log.w(TAG, "Torrent buffering prolonged ${elapsed}ms estimatedBandwidth=${estimatedBandwidthBps}bps")
                    showTorrentRecoveryDialog(exo, if (lowBandwidth) "Низька швидкість потоку" else "Потік довго буферизується")
                } else {
                    torrentBufferingWatch = this
                    mainHandler.postDelayed(this, threshold - elapsed)
                }
            }
        }
        torrentBufferingWatch = watch
        mainHandler.postDelayed(watch, 15_000L)
    }

    private fun showTorrentRecoveryDialog(exo: ExoPlayer, reason: String) {
        if (isFinishing || isDestroyed) return
        hideBufferingIndicator()
        val speed = estimatedBandwidthBps.takeIf { it > 0L }
            ?.let { "\nОцінена швидкість: %.1f Мбіт/с".format(it / 1_000_000.0) }.orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Не вдається відтворити")
            .setMessage("$reason.$speed\n\nМожна повторити підключення або повернутися до списку й вибрати іншу роздачу.")
            .setPositiveButton("Повторити") { _, _ ->
                torrentBufferingWatch?.let(mainHandler::removeCallbacks)
                torrentBufferingWatch = null
                torrentBufferingSinceMs = 0L
                torrentBufferPromptShown = false
                estimatedBandwidthBps = 0L
                exo.stop()
                exo.prepare()
                exo.playWhenReady = true
            }
            .setNegativeButton("Змінити роздачу") { _, _ -> finish() }
            .setOnCancelListener {
                torrentBufferPromptShown = false
                if (exo.playbackState == Player.STATE_BUFFERING) watchTorrentBuffering(exo)
            }
            .show()
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
        binding.playerControls.nowPlayingLabel.visibility = View.VISIBLE
    }

    private fun updateSkipIntroButton() {
        val position = player?.currentPosition ?: return
        val isTv = intent.getStringExtra(EXTRA_TMDB_TYPE) == TmdbMediaType.TV.path
        val creditsStartMs = if (isTv) introSegments.credits.minOfOrNull { it.startMs }
            ?.takeIf { it > 0L } else null
        if (!nextEpisodePreloadStarted && creditsStartMs != null &&
            position >= (creditsStartMs - NEXT_EPISODE_PRELOAD_LEAD_MS).coerceAtLeast(0L)) {
            nextEpisodePreloadStarted = true
            prefetchNextEpisodeChunk()
        }

        val intro = introSegments.intros.firstOrNull {
            position >= it.startMs && position < it.endMs
        }
        val nextSeason = intent.getIntExtra(EXTRA_TMDB_SEASON, -1)
        val nextEpisode = intent.getIntExtra(EXTRA_TMDB_EPISODE, -1)
        val canGoNext = isTv && nextSeason > 0 && nextEpisode > 0 &&
            (isToloka && !intent.getStringExtra(EXTRA_TOLOKA_TOPIC_ID).isNullOrBlank() ||
                isTmdbAnitube)
        when {
            intro != null && !introPromptDismissed -> showPlayerPrompt(PlayerPrompt.SKIP_INTRO)
            creditsStartMs != null && position >= creditsStartMs && canGoNext &&
                !nextEpisodePromptDismissed -> showPlayerPrompt(PlayerPrompt.NEXT_EPISODE)
            else -> hidePlayerPrompt()
        }
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

    private fun launchNextEpisode() {
        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it > 0 } ?: return
        val type = intent.getStringExtra(EXTRA_TMDB_TYPE)
            ?.takeIf { it == TmdbMediaType.TV.path } ?: return
        val nextEpisode = (intent.getIntExtra(EXTRA_TMDB_EPISODE, episodeNumber) + 1)
        val season = intent.getIntExtra(EXTRA_TMDB_SEASON, -1).takeIf { it > 0 } ?: return
        val source = when {
            isToloka -> ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.TORRENT
            isTmdbAnitube -> ua.any2b.tvplayer.data.TmdbPlaybackSelection.Source.ANITUBE
            else -> return
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
            startActivity(nextIntent)
            finish()
        } catch (error: Exception) {
            Log.e(TAG, "Could not open next episode", error)
            nextEpisodePromptDismissed = true
            hidePlayerPrompt()
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
        binding.bufferingIndicator.visibility = View.VISIBLE
    }

    private fun hideBufferingIndicator() {
        if (::binding.isInitialized) binding.bufferingIndicator.visibility = View.GONE
    }

    override fun onPause() {
        setKeepScreenOn(false)
        mainHandler.removeCallbacks(updateIntroSkip)
        saveCurrentProgress()
        super.onPause()
        player?.pause()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
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
        player?.play()
        setKeepScreenOn(player?.let {
            it.playWhenReady && it.playbackState != Player.STATE_ENDED
        } == true)
        if (introSegments.intros.isNotEmpty() || introSegments.credits.isNotEmpty()) {
            mainHandler.removeCallbacks(updateIntroSkip)
            mainHandler.post(updateIntroSkip)
        }
    }

    override fun onDestroy() {
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
