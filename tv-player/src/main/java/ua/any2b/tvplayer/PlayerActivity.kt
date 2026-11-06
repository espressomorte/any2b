package ua.any2b.tvplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
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
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.core.toloka.TolokaReleaseMetadataParser
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.tvplayer.data.AnitubeStreamFallback
import ua.any2b.tvplayer.databinding.ActivityPlayerBinding
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TvPlayer"
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
        const val EXTRA_TMDB_RESUME_PROGRESS = "TMDB_RESUME_PROGRESS"
        const val EXTRA_TORRENT_RESUME_POSITION_MS = "TORRENT_RESUME_POSITION_MS"
        const val EXTRA_BACKUP_EMBED_URLS = "BACKUP_EMBED_URLS"
        const val EXTRA_BACKUP_VOICE_INDICES = "BACKUP_VOICE_INDICES"
        const val EXTRA_BACKUP_EPISODE_INDICES = "BACKUP_EPISODE_INDICES"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

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

        val url = intent.getStringExtra(EXTRA_STREAM_URL)
        voiceIndex = intent.getIntExtra(EXTRA_VOICE_INDEX, 0)
        episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
        episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, episodeIndex + 1)
        isToloka = intent.getStringExtra(EXTRA_SOURCE_TYPE) == SOURCE_TOLOKA
        isTmdbAnitube = intent.getStringExtra(EXTRA_SOURCE_TYPE) == SOURCE_ANITUBE_TMDB
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
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSource = if (isToloka) {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
        } else {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    Log.d("Player", "state=$state") // 1 IDLE, 2 BUFFERING, 3 READY, 4 ENDED
                    when (state) {
                        Player.STATE_READY   -> {
                            showStatus("▶ Відтворення", if (isToloka) "" else url)
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
                        }
                        Player.STATE_BUFFERING -> showStatus("⏳ Буферизація…", if (isToloka) "" else url)
                        Player.STATE_ENDED   -> {
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

                override fun onPlayerError(error: PlaybackException) {
                    if (isToloka) {
                        Log.e("Player", "TorrServer playback: ${error.errorCodeName}")
                        showStatus("✗ Потік TorrServer недоступний (${error.errorCodeName}). Перевірте адресу сервера на цьому пристрої та стан роздачі.", "")
                    } else {
                        Log.e("Player", "ExoPlayer: ${error.errorCodeName} / ${error.message}", error)
                        if (retryingBackup) return
                        if (isTmdbAnitube && !tmdbAnitubeStarted && backupSources.isNotEmpty()) {
                            retryAnitubeBackup()
                            return
                        }
                        val msg = "✗ Помилка: ${error.errorCodeName} — ${error.message}"
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

            exo.setMediaSource(mediaSource)
            exo.playWhenReady = true
            exo.prepare()
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
        Log.i(TAG, status)
        binding.statusOverlay.visibility = View.VISIBLE
        binding.tvStatus.text = status
        binding.tvUrl.text = if (url.length > 80) "…${url.takeLast(60)}" else url
    }

    override fun onPause() {
        saveCurrentProgress()
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        saveCurrentProgress()
        progressJob?.cancel()
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
