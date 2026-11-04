package ua.any2b.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
    private lateinit var status: TextView
    private lateinit var externalButton: Button
    private var trailers: List<TmdbTrailer> = emptyList()
    private var trailerIndex = 0
    private var allFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = TmdbMediaType.entries.firstOrNull { it.path == intent.getStringExtra(EXTRA_TYPE) }
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (type == null || id <= 0) { finish(); return }

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
                            "ready" -> status.text = "Готово до відтворення"
                            "error" -> nextTrailerOrError(url.getQueryParameter("code"))
                            "blocked" -> status.text = "Автозапуск заблоковано. Натисніть «Відтворити»."
                        }
                        return true
                    }
                    if (url.scheme == "http" || url.scheme == "https") {
                        openUrl(url)
                        return true
                    }
                    return true
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            text = "Шукаємо трейлер у TMDB…"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(16.dp, 8.dp, 16.dp, 8.dp)
        }
        root.addView(status)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dp, 0, 16.dp, 12.dp)
        }
        controls.addView(Button(this).apply {
            text = "Назад"
            setOnClickListener { finish() }
        })
        controls.addView(Button(this).apply {
            text = "Відтворити / пауза"
            setOnClickListener { togglePlayback() }
        })
        externalButton = Button(this).apply {
            text = "Відкрити в YouTube"
            setOnClickListener { openExternal() }
        }
        controls.addView(externalButton)
        root.addView(controls)
        setContentView(root)

        lifecycleScope.launch {
            try {
                val connection = workerTmdbConnection(this@TrailerActivity)
                trailers = connection?.client?.trailers(type, id, connection.accessKey,
                    intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it >= 0 }).orEmpty()
                if (trailers.isEmpty()) {
                    allFailed = true
                    status.text = "Офіційного трейлера в TMDB не знайдено. Можна відкрити пошук YouTube."
                    externalButton.text = "Пошук на YouTube"
                } else loadTrailer()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                allFailed = true
                status.text = "Не вдалося отримати трейлер із TMDB. Можна відкрити пошук YouTube."
                externalButton.text = "Пошук на YouTube"
            }
        }
    }

    private fun loadTrailer() {
        val video = trailers[trailerIndex]
        status.text = "Завантаження: ${video.name}"
        // A non-null HTTPS base gives YouTube's iframe an origin and HTTP referrer.
        webView.loadDataWithBaseURL("https://appassets.androidplatform.net/",
            playerHtml(video.videoId), "text/html", "UTF-8", null)
        webView.requestFocus()
    }

    private fun nextTrailerOrError(code: String?) {
        if (trailerIndex + 1 < trailers.size) {
            trailerIndex++
            status.text = "Трейлер недоступний ($code). Пробуємо наступний…"
            loadTrailer()
        } else {
            allFailed = true
            status.text = "Вбудовані трейлери недоступні ($code). Відкрийте пошук YouTube."
            externalButton.text = "Пошук на YouTube"
        }
    }

    private fun togglePlayback() {
        webView.evaluateJavascript(
            "if(window.player){var s=player.getPlayerState();if(s===1){player.pauseVideo()}else{player.playVideo()}}",
            null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::webView.isInitialized && webView.hasFocus()) {
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
            if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                togglePlayback()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openExternal() {
        val url = if (!allFailed && trailers.isNotEmpty()) trailers[trailerIndex].youtubeUrl
            else "https://www.youtube.com/results?search_query=" + Uri.encode(
                listOfNotNull(intent.getStringExtra(EXTRA_TITLE),
                    intent.getIntExtra(EXTRA_YEAR, 0).takeIf { it > 0 }?.toString(), "trailer")
                    .joinToString(" "))
        openUrl(Uri.parse(url))
    }

    private fun openUrl(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: ActivityNotFoundException) {
            status.text = "Немає застосунку для відкриття YouTube"
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
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
