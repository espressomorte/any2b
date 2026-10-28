package ua.any2b.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbDetailsScreen
import ua.any2b.tvplayer.ui.TmdbDetailsViewModel
import ua.any2b.tvplayer.ui.TolokaSearchViewModel

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
    private var launchingPlayer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(EXTRA_TMDB_ID, -1)
        val type = TmdbMediaType.entries.firstOrNull { it.path == intent.getStringExtra(EXTRA_TMDB_TYPE) }
        if (id <= 0 || type == null) {
            finish()
            return
        }
        val query = intent.getStringExtra(EXTRA_SEARCH_QUERY).orEmpty()
        val preferredTitle = intent.getStringExtra(EXTRA_PREFERRED_TITLE)
        val originalLanguage = intent.getStringExtra(EXTRA_ORIGINAL_LANGUAGE)
        val originalTitle = intent.getStringExtra(EXTRA_ORIGINAL_TITLE)
        detailsViewModel.load(type, id, query, preferredTitle, originalLanguage, originalTitle)
        setContent {
            AppTheme {
                TmdbDetailsScreen(
                    state = detailsViewModel.state.collectAsStateWithLifecycle().value,
                    toloka = tolokaViewModel.state.collectAsStateWithLifecycle().value,
                    onRetryDetails = {
                        detailsViewModel.load(type, id, query, preferredTitle,
                            originalLanguage, originalTitle, force = true)
                    },
                    onSearchTorrent = tolokaViewModel::search,
                    onSelectTorrent = tolokaViewModel::selectHit,
                    onRetryMetadata = tolokaViewModel::retryMetadata,
                    onPlayFile = ::openFile,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchingPlayer = false
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
        val selectedPackage = SettingsManager(this).selectedPlayerPackage()
        val serverRequiresAuth = TorrServerAuthStore(this).read() != null
        if (selectedPackage != null && !serverRequiresAuth) {
            val externalIntent = ExternalVideoPlayers(this).intentForVideo(url, selectedPackage)
            if (externalIntent != null) {
                try {
                    startActivity(externalIntent)
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
            })
            tolokaViewModel.reportPlaybackStatus("URL передано вбудованому плеєру")
        } catch (_: Exception) {
            launchingPlayer = false
            tolokaViewModel.reportPlaybackStatus("Не вдалося запустити плеєр після формування URL потоку")
            Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
        }
    }
}
