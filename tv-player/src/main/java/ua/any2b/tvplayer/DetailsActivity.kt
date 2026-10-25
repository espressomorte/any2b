package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.DetailsScreen
import ua.any2b.tvplayer.ui.DetailsViewModel

class DetailsActivity : ComponentActivity() {

    private val animeId: Int by lazy {
        intent.getIntExtra("ANIME_ID", 1)
    }

    private val viewModel: DetailsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DetailsViewModel(animeId, applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                DetailsScreen(
                    state = state,
                    onRetry = viewModel::loadDetails,
                    onRetryVideoSource = viewModel::retryVideoSource,
                    onSelectRelease = viewModel::selectRelease,
                    onSelectVoice = viewModel::selectVoice,
                    onSelectEpisode = viewModel::selectEpisode,
                    onWatchClick = {
                        viewModel.resolveSelectedStream(
                            onError = { errorMessage ->
                                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                            },
                            onSuccess = { streamUrl, voiceIndex, episodeIndex ->
                                val intent = Intent(this, PlayerActivity::class.java).apply {
                                    putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
                                    putExtra(PlayerActivity.EXTRA_ANIME_ID, animeId)
                                    putExtra(PlayerActivity.EXTRA_VOICE_INDEX, voiceIndex)
                                    putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, episodeIndex)
                                }
                                startActivity(intent)
                            }
                        )
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
}
