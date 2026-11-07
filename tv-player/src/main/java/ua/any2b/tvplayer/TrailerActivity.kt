package ua.any2b.tvplayer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import org.json.JSONTokener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTrailer
import ua.any2b.tvplayer.data.workerTmdbConnection
import ua.any2b.tvplayer.databinding.ActivityTrailerBinding
import ua.any2b.tvplayer.ui.PlayerControlsController
import ua.any2b.tvplayer.ui.PlayerTransport

/** Plays official TMDB trailers through YouTube's supported IFrame API in our player shell. */
class TrailerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TYPE = "TRAILER_TYPE"
        const val EXTRA_ID = "TRAILER_ID"
        const val EXTRA_TITLE = "TRAILER_TITLE"
        const val EXTRA_YEAR = "TRAILER_YEAR"
        const val EXTRA_SEASON = "TRAILER_SEASON"
    }

    private lateinit var binding: ActivityTrailerBinding
    private lateinit var webView: WebView
    private lateinit var trailerTransport: YouTubeIframeTransport
    private var controlsController: PlayerControlsController? = null
    private var trailers: List<TmdbTrailer> = emptyList()
    private var trailerIndex = 0
    private var fallbackOpened = false
    private var usingEmbeddedPlayer = false
    private var fallbackSearchPage = false
    private var currentVideoId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val playbackTimeout = Runnable { fallbackToYouTube() }
    private val progressPoll = object : Runnable {
        override fun run() {
            if (!usingEmbeddedPlayer || !::webView.isInitialized) return
            trailerTransport.poll {
                controlsController?.refresh()
                binding.trailerBufferingIndicator.visibility =
                    if (trailerTransport.playerState == 3) View.VISIBLE else View.GONE
            }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = TmdbMediaType.entries.firstOrNull { it.path == intent.getStringExtra(EXTRA_TYPE) }
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (type == null || id <= 0) { finish(); return }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        binding = ActivityTrailerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        webView = binding.trailerWebView
        trailerTransport = YouTubeIframeTransport(webView)
        controlsController = PlayerControlsController(binding.trailerPlayerControls) {
            webView.requestFocus()
        }
        controlsController?.attach(trailerTransport)

        webView.apply {
            setBackgroundColor(android.graphics.Color.rgb(1, 1, 10))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            isFocusable = true
            isFocusableInTouchMode = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val url = request.url
                    if (url.scheme == "any2b-trailer") {
                        when (url.host) {
                            "ready" -> {
                                handler.removeCallbacks(playbackTimeout)
                                binding.trailerBufferingIndicator.visibility = View.GONE
                            }
                            "state" -> {
                                val state = url.getQueryParameter("value")?.toIntOrNull() ?: -1
                                trailerTransport.updatePlayerState(state)
                                binding.trailerBufferingIndicator.visibility =
                                    if (state == 3) View.VISIBLE else View.GONE
                                controlsController?.refresh()
                            }
                            "error", "blocked" -> fallbackToYouTube()
                            "search-result" -> {
                                val videoId = url.getQueryParameter("id")
                                    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                                if (videoId != null) {
                                    currentVideoId = videoId
                                    fallbackSearchPage = false
                                    fallbackOpened = false
                                    loadPlayer(videoId)
                                }
                            }
                        }
                        return true
                    }
                    return url.scheme == "http" || url.scheme == "https"
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (fallbackSearchPage) view.evaluateJavascript(
                        """(function(){
                          var attempts=0;
                          var timer=setInterval(function(){
                            var links=Array.from(document.querySelectorAll('a#video-title[href],a[href*="/watch?v="]'));
                            var link=links.find(function(a){return /\/watch\?v=[A-Za-z0-9_-]{11}/.test(a.href)});
                            var id=link?new URL(link.href).searchParams.get('v'):null;
                            if(id){clearInterval(timer);location.href='any2b-trailer://search-result?id='+encodeURIComponent(id)}
                            else if(++attempts>=15)clearInterval(timer);
                          },700);
                        })()""", null)
                }
            }
        }

        lifecycleScope.launch {
            try {
                val connection = workerTmdbConnection(this@TrailerActivity)
                trailers = connection?.client?.trailers(type, id, connection.accessKey,
                    intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it >= 0 }).orEmpty()
                if (trailers.isEmpty()) {
                    fallbackToYouTube()
                } else loadTrailer()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fallbackToYouTube()
            }
        }
    }

    private fun loadTrailer() {
        val video = trailers[trailerIndex]
        currentVideoId = video.videoId
        loadPlayer(video.videoId)
    }

    private fun loadPlayer(videoId: String) {
        usingEmbeddedPlayer = true
        trailerTransport.reset()
        controlsController?.attach(trailerTransport)
        binding.trailerBufferingIndicator.visibility = View.VISIBLE
        // A non-null HTTPS base gives YouTube's iframe an origin and HTTP referrer.
        webView.loadDataWithBaseURL("https://appassets.androidplatform.net/",
            playerHtml(videoId), "text/html", "UTF-8", null)
        webView.requestFocus()
        handler.removeCallbacks(playbackTimeout)
        handler.removeCallbacks(progressPoll)
        handler.postDelayed(playbackTimeout, 20_000)
        handler.post(progressPoll)
    }

    private fun fallbackToYouTube() {
        if (fallbackOpened || isFinishing) return
        fallbackOpened = true
        usingEmbeddedPlayer = false
        handler.removeCallbacks(playbackTimeout)
        handler.removeCallbacks(progressPoll)
        controlsController?.attach(null)
        controlsController?.hideImmediately()
        val url = currentVideoId?.let { "https://www.youtube.com/watch?v=$it" }
        if (url != null) {
            // Keep playback in our WebView. ACTION_VIEW may route to the unsupported TV app.
            webView.loadUrl(url)
        } else {
            fallbackSearchPage = true
            webView.loadUrl(youtubeSearchUrl().toString())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (usingEmbeddedPlayer && controlsController?.onRemoteKey(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    private fun youtubeSearchUrl(): Uri = Uri.parse("https://www.youtube.com/results?search_query=" +
        Uri.encode(listOfNotNull(intent.getStringExtra(EXTRA_TITLE),
            intent.getIntExtra(EXTRA_YEAR, 0).takeIf { it > 0 }?.toString(), "trailer")
            .joinToString(" ")))

    override fun onPause() {
        handler.removeCallbacks(progressPoll)
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        if (usingEmbeddedPlayer) handler.post(progressPoll)
    }

    override fun onDestroy() {
        handler.removeCallbacks(playbackTimeout)
        handler.removeCallbacks(progressPoll)
        controlsController?.release()
        controlsController = null
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun playerHtml(videoId: String): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body,#player{margin:0;width:100%;height:100%;background:#01010A;overflow:hidden}</style>
        </head><body><div id="player"></div><script>
        var player;
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('player',{
            width:'100%',height:'100%',videoId:'$videoId',
            playerVars:{autoplay:1,controls:0,disablekb:1,playsinline:1,rel:0,fs:0,origin:'https://appassets.androidplatform.net'},
            events:{
              onReady:function(e){location.href='any2b-trailer://ready';e.target.playVideo()},
              onStateChange:function(e){location.href='any2b-trailer://state?value='+e.data},
              onError:function(e){location.href='any2b-trailer://error?code='+e.data},
              onAutoplayBlocked:function(){location.href='any2b-trailer://blocked'}
            }
          });
        }
        var script=document.createElement('script');script.src='https://www.youtube.com/iframe_api';
        document.head.appendChild(script);
        </script></body></html>
    """.trimIndent()

    private class YouTubeIframeTransport(private val webView: WebView) : PlayerTransport {
        var playerState: Int = -1
            private set
        private var positionMs = 0L
        private var durationMs = 0L

        override fun currentPositionMs() = positionMs
        override fun durationMs() = durationMs
        override fun isPlaying() = playerState == 1

        override fun play() = evaluate("if(window.player){player.playVideo()}")
        override fun pause() = evaluate("if(window.player){player.pauseVideo()}")
        override fun togglePlayback() {
            if (isPlaying()) pause() else play()
        }

        override fun seekTo(positionMs: Long) {
            val seconds = (positionMs.coerceAtLeast(0L) / 1_000f)
            evaluate("if(window.player){player.seekTo($seconds,true)}")
        }

        fun updatePlayerState(state: Int) {
            playerState = state
        }

        fun reset() {
            playerState = -1
            positionMs = 0L
            durationMs = 0L
        }

        fun poll(onUpdated: () -> Unit) {
            webView.evaluateJavascript(
                "if(window.player){[player.getCurrentTime(),player.getDuration(),player.getPlayerState()].join(',')}else''"
            ) { result ->
                val state = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                val values = state?.split(',') ?: return@evaluateJavascript
                if (values.size == 3) {
                    positionMs = ((values[0].toDoubleOrNull() ?: 0.0) * 1_000).toLong().coerceAtLeast(0L)
                    durationMs = ((values[1].toDoubleOrNull() ?: 0.0) * 1_000).toLong().coerceAtLeast(0L)
                    playerState = values[2].toIntOrNull() ?: playerState
                    onUpdated()
                }
            }
        }

        private fun evaluate(script: String) {
            webView.evaluateJavascript(script, null)
        }
    }
}
