package ua.any2b.tvplayer

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTrailer
import ua.any2b.tvplayer.data.workerTmdbConnection

/** Plays TMDB's official YouTube trailer through the supported IFrame player. */
class TrailerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TYPE = "TRAILER_TYPE"
        const val EXTRA_ID = "TRAILER_ID"
        const val EXTRA_TITLE = "TRAILER_TITLE"
        const val EXTRA_YEAR = "TRAILER_YEAR"
        const val EXTRA_SEASON = "TRAILER_SEASON"
    }

    private lateinit var webView: WebView
    private var trailers: List<TmdbTrailer> = emptyList()
    private var trailerIndex = 0
    private var fallbackOpened = false
    private var usingEmbeddedPlayer = false
    private var fallbackSearchPage = false
    private var currentVideoId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val playbackTimeout = Runnable { fallbackToYouTube() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = TmdbMediaType.entries.firstOrNull { it.path == intent.getStringExtra(EXTRA_TYPE) }
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (type == null || id <= 0) { finish(); return }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
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
                            "ready" -> handler.removeCallbacks(playbackTimeout)
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
                    if (url.scheme == "http" || url.scheme == "https") {
                        return false
                    }
                    return true
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
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

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
        // A non-null HTTPS base gives YouTube's iframe an origin and HTTP referrer.
        webView.loadDataWithBaseURL("https://appassets.androidplatform.net/",
            playerHtml(videoId), "text/html", "UTF-8", null)
        webView.requestFocus()
        handler.removeCallbacks(playbackTimeout)
        handler.postDelayed(playbackTimeout, 20_000)
    }

    private fun fallbackToYouTube() {
        if (fallbackOpened || isFinishing) return
        fallbackOpened = true
        usingEmbeddedPlayer = false
        handler.removeCallbacks(playbackTimeout)
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
        if (event.action == KeyEvent.ACTION_DOWN && usingEmbeddedPlayer &&
            ::webView.isInitialized && webView.hasFocus()) {
            val seconds = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -10
                KeyEvent.KEYCODE_DPAD_RIGHT -> 10
                else -> null
            }
            if (seconds != null) {
                webView.evaluateJavascript(
                    "if(window.player){player.seekTo(Math.max(0,player.getCurrentTime()+$seconds),true)}",
                    null)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun youtubeSearchUrl(): Uri = Uri.parse("https://www.youtube.com/results?search_query=" +
        Uri.encode(listOfNotNull(intent.getStringExtra(EXTRA_TITLE),
            intent.getIntExtra(EXTRA_YEAR, 0).takeIf { it > 0 }?.toString(), "trailer")
            .joinToString(" ")))

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        handler.removeCallbacks(playbackTimeout)
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun playerHtml(videoId: String): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>
        </head><body><div id="player"></div><script>
        var player;
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('player',{
            width:'100%',height:'100%',videoId:'$videoId',
            playerVars:{autoplay:1,controls:1,playsinline:1,rel:0,origin:'https://appassets.androidplatform.net'},
            events:{
              onReady:function(e){location.href='any2b-trailer://ready';e.target.playVideo()},
              onError:function(e){location.href='any2b-trailer://error?code='+e.data},
              onAutoplayBlocked:function(){location.href='any2b-trailer://blocked'}
            }
          });
        }
        var script=document.createElement('script');script.src='https://www.youtube.com/iframe_api';
        document.head.appendChild(script);
        </script></body></html>
    """.trimIndent()
}
