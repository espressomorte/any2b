package ua.any2b.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.data.ExternalVideoPlayers
import ua.any2b.tvplayer.data.SettingsManager
import ua.any2b.tvplayer.data.TorrServerAuthStore
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TolokaSearchScreen
import ua.any2b.tvplayer.ui.TolokaSearchViewModel

class TolokaSearchActivity : ComponentActivity() {
    private val viewModel: TolokaSearchViewModel by viewModels()
    private var launchingPlayer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TolokaSearchScreen(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    onSearch = viewModel::search,
                    onSelect = viewModel::selectHit,
                    onRetryMetadata = viewModel::retryMetadata,
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
        val url = viewModel.playbackUrl(fileId)
        if (url == null) {
            viewModel.reportPlaybackStatus("Не вдалося сформувати URL потоку для вибраного файлу")
            Toast.makeText(this, "Відеофайл недоступний. Повторіть вибір роздачі", Toast.LENGTH_LONG).show()
            return
        }
        launchingPlayer = true
        viewModel.reportPlaybackStatus("URL потоку сформовано для індексу $fileId. Відкриваємо плеєр…")
        val selectedPackage = SettingsManager(this).selectedPlayerPackage()
        val serverRequiresAuth = TorrServerAuthStore(this).read() != null
        if (selectedPackage != null && !serverRequiresAuth) {
            val externalIntent = ExternalVideoPlayers(this).intentForVideo(url, selectedPackage)
            if (externalIntent != null) {
                try {
                    startActivity(externalIntent)
                    viewModel.reportPlaybackStatus("URL передано зовнішньому плеєру. Результат відтворення перевіряйте в ньому")
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
            viewModel.reportPlaybackStatus("URL передано вбудованому плеєру")
        } catch (_: Exception) {
            launchingPlayer = false
            viewModel.reportPlaybackStatus("Не вдалося запустити плеєр після формування URL потоку")
            Toast.makeText(this, "Не вдалося відкрити плеєр", Toast.LENGTH_LONG).show()
        }
    }
}
