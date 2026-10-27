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
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import ua.any2b.core.anilist.AniListClient
import ua.any2b.core.anitube.AnitubeClient
import ua.any2b.core.anitube.AnitubeVoice
import ua.any2b.core.anitube.playableVoices
import ua.any2b.core.extractor.StreamResolver
import ua.any2b.core.matcher.TitleMatcher
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.VoiceCache
import ua.any2b.tvplayer.data.WatchHistoryManager
import ua.any2b.tvplayer.databinding.ActivityPlayerBinding
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TvPlayer"
        const val EXTRA_STREAM_URL = "STREAM_URL"
        const val EXTRA_ANIME_ID = "ANIME_ID"
        const val EXTRA_VOICE_INDEX = "VOICE_INDEX"
        const val EXTRA_EPISODE_INDEX = "EPISODE_INDEX"
        const val EXTRA_EPISODE_NUMBER = "EPISODE_NUMBER"
        const val EXTRA_IS_MOVIE = "IS_MOVIE"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private var animeId: Int = -1
    private var voiceIndex: Int = 0
    private var episodeIndex: Int = 0
    private var episodeNumber: Int = 1
    private var isMovie: Boolean = false

    private var nextEpisodeUrl: String? = null
    private var nextEpisodeIndex: Int = -1
    private var nextEpisodeNumber: Int = -1
    private var progressJob: Job? = null
    private var prefetchJob: Job? = null

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
        animeId = intent.getIntExtra(EXTRA_ANIME_ID, -1)
        voiceIndex = intent.getIntExtra(EXTRA_VOICE_INDEX, 0)
        episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 0)
        episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, episodeIndex + 1)
        isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)

        if (url.isNullOrBlank()) {
            Log.w("NextEp", "finish(): url is null or blank")
            showStatus("⚠ Не вказано URL потоку", "")
            finish()
            return
        }

        binding.btnNextEpisode.setOnClickListener {
            if (nextEpisodeUrl != null) {
                playNextEpisodeInstant()
            } else {
                playNextEpisodeFallback()
            }
        }

        showStatus("⏳ Підключення до потоку…", url)
        initPlayer(url)
    }

    private fun initPlayer(url: String) {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    Log.d("Player", "state=$state") // 1 IDLE, 2 BUFFERING, 3 READY, 4 ENDED
                    when (state) {
                        Player.STATE_READY   -> {
                            showStatus("▶ Відтворення", url)
                            startProgressReporting()
                            startPreFetchingNextEpisode()
                        }
                        Player.STATE_BUFFERING -> showStatus("⏳ Буферизація…", url)
                        Player.STATE_ENDED   -> {
                            val current = exo.currentPosition
                            val duration = exo.duration
                            if (duration > 0 && current >= duration - 3000) {
                                showStatus("✓ Завершено", url)
                                handlePlaybackEnded()
                            } else {
                                Log.w(TAG, "Ignored premature STATE_ENDED at current=$current, duration=$duration")
                            }
                        }
                        Player.STATE_IDLE    -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("Player", "ExoPlayer: ${error.errorCodeName} / ${error.message}", error)
                    val msg = "✗ Помилка: ${error.errorCodeName} — ${error.message}"
                    showStatus(msg, url)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        binding.statusOverlay.postDelayed({
                            binding.statusOverlay.visibility = View.GONE
                        }, 3000)
                    }
                }
            })

            exo.setMediaSource(hlsMediaSource)
            exo.playWhenReady = true
            exo.prepare()
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
                if (duration > 0 && animeId > 0) {
                    val progress = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    withContext(Dispatchers.IO) {
                        try {
                            val aniListClient = AniListClient()
                            val details = aniListClient.animeDetails(animeId)
                            val historyManager = WatchHistoryManager(this@PlayerActivity)
                            historyManager.saveProgress(details, episodeIndex, voiceIndex, progress, episodeNumber)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private suspend fun getCachedOrFetchVoices(): List<AnitubeVoice>? {
        if (animeId <= 0) return null
        VoiceCache.get(animeId)?.let { return it }

        try {
            val aniListClient = AniListClient()
            val anitubeClient = AnitubeClient()
            val details = aniListClient.animeDetails(animeId)
            val query = details.title.romaji ?: details.title.english ?: ""
            val searchResults = anitubeClient.search(query)
            val best = TitleMatcher.confidentBest(TitleMatcher.match(details, searchResults))
                ?: return null
            val voices = playableVoices(anitubeClient.getVoices(best.pageUrl), details.format)
            VoiceCache.put(animeId, voices)
            return voices
        } catch (_: Exception) {
            return null
        }
    }

    private fun startPreFetchingNextEpisode() {
        if (animeId <= 0 || isMovie) return
        prefetchJob?.cancel()
        prefetchJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val voices = getCachedOrFetchVoices() ?: return@launch
                val voice = voices.getOrNull(voiceIndex) ?: voices.firstOrNull() ?: return@launch
                val targetNextIndex = episodeIndex + 1
                val nextEp = voice.episodes.getOrNull(targetNextIndex) ?: return@launch

                val streamResolver = StreamResolver()
                val extracted = streamResolver.resolve(nextEp.fileUrl)
                nextEpisodeUrl = extracted.streamUrl
                nextEpisodeIndex = targetNextIndex
                nextEpisodeNumber = nextEp.episodeNumber

                withContext(Dispatchers.Main) {
                    binding.btnNextEpisode.visibility = View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    private fun playNextEpisodeInstant() {
        val url = nextEpisodeUrl
        val nextIdx = nextEpisodeIndex
        val nextNumber = nextEpisodeNumber
        if (url.isNullOrBlank() || nextIdx < 0 || nextNumber < 1 || animeId <= 0) {
            playNextEpisodeFallback()
            return
        }

        binding.btnNextEpisode.visibility = View.GONE
        showStatus("▶ Запуск серії $nextNumber…", url)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val aniListClient = AniListClient()
                val details = aniListClient.animeDetails(animeId)
                val historyManager = WatchHistoryManager(this@PlayerActivity)
                historyManager.saveProgress(details, nextIdx, voiceIndex, 0.0f, nextNumber)

                withContext(Dispatchers.Main) {
                    episodeIndex = nextIdx
                    episodeNumber = nextNumber
                    intent.putExtra(EXTRA_EPISODE_INDEX, episodeIndex)
                    intent.putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)

                    val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                    val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(url))

                    player?.apply {
                        setMediaSource(hlsMediaSource)
                        prepare()
                        playWhenReady = true
                    }

                    nextEpisodeUrl = null
                    nextEpisodeIndex = -1
                    nextEpisodeNumber = -1
                    startPreFetchingNextEpisode()
                }
            } catch (e: Exception) {
                Log.e("NextEp", "finish(): виняток у ланцюжку (instant)", e)
                withContext(Dispatchers.Main) {
                    showStatus("⚠ Не вдалося запустити наступну серію", "")
                    binding.statusOverlay.postDelayed({
                        binding.statusOverlay.visibility = View.GONE
                    }, 3000)
                }
            }
        }
    }

    private fun playNextEpisodeFallback() {
        if (animeId <= 0) {
            Log.w("NextEp", "finish(): animeId<=0")
            showStatus("✓ Більше немає серій", "")
            binding.statusOverlay.postDelayed({
                finish()
            }, 2000)
            return
        }

        binding.btnNextEpisode.visibility = View.GONE
        showStatus("⏳ Завантаження наступної серії…", "")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val voices = getCachedOrFetchVoices()
                val voice = voices?.getOrNull(voiceIndex) ?: voices?.firstOrNull()
                val targetNextIndex = episodeIndex + 1
                val nextEp = voice?.episodes?.getOrNull(targetNextIndex)

                if (nextEp != null) {
                    val streamResolver = StreamResolver()
                    val extracted = streamResolver.resolve(nextEp.fileUrl)
                    val historyManager = WatchHistoryManager(this@PlayerActivity)
                    val aniListClient = AniListClient()
                    val details = aniListClient.animeDetails(animeId)
                    historyManager.saveProgress(details, targetNextIndex, voiceIndex, 0.0f, nextEp.episodeNumber)

                    withContext(Dispatchers.Main) {
                        episodeIndex = targetNextIndex
                        episodeNumber = nextEp.episodeNumber
                        intent.putExtra(EXTRA_EPISODE_INDEX, episodeIndex)
                        intent.putExtra(EXTRA_EPISODE_NUMBER, episodeNumber)

                        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(extracted.streamUrl))

                        player?.apply {
                            setMediaSource(hlsMediaSource)
                            prepare()
                            playWhenReady = true
                        }
                        showStatus("▶ Серія $episodeNumber", extracted.streamUrl)

                        nextEpisodeUrl = null
                        nextEpisodeIndex = -1
                        nextEpisodeNumber = -1
                        startPreFetchingNextEpisode()
                    }
                } else {
                    Log.w("NextEp", "finish(): наступну серію не знайдено, episodeIndex=$episodeIndex voiceIndex=$voiceIndex")
                    withContext(Dispatchers.Main) {
                        showStatus("✓ Більше немає серій", "")
                        binding.statusOverlay.postDelayed({
                            finish()
                        }, 2000)
                    }
                }
            } catch (e: Exception) {
                Log.e("NextEp", "finish(): виняток у ланцюжку", e)
                withContext(Dispatchers.Main) {
                    showStatus("⚠ Не вдалося завантажити наступну серію", "")
                    binding.statusOverlay.postDelayed({
                        binding.statusOverlay.visibility = View.GONE
                    }, 3000)
                }
            }
        }
    }

    private fun handlePlaybackEnded() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (animeId > 0) {
                    val aniListClient = AniListClient()
                    val details = aniListClient.animeDetails(animeId)
                    val historyManager = WatchHistoryManager(this@PlayerActivity)
                    historyManager.saveProgress(details, episodeIndex, voiceIndex, 1.0f, episodeNumber)
                }
            } catch (_: Exception) {}
        }

        if (isMovie) {
            finish()
            return
        }
        val settingsManager = SettingsManager(this)
        val autoPlay = settingsManager.isAutoPlayNextEnabled()

        if (nextEpisodeUrl != null && nextEpisodeIndex >= 0) {
            playNextEpisodeInstant()
        } else if (autoPlay) {
            playNextEpisodeFallback()
        } else {
            finish()
        }
    }

    private fun showStatus(status: String, url: String) {
        Log.i(TAG, status)
        binding.statusOverlay.visibility = View.VISIBLE
        binding.tvStatus.text = status
        binding.tvUrl.text = if (url.length > 80) "…${url.takeLast(60)}" else url
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        okHttpClient.dispatcher.executorService.shutdown()
    }
}
