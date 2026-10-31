package ua.any2b.tvplayer

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel

/** Launcher with TMDB animation, while the AniList catalog lives in AniTubeHomeActivity. */
class MainActivity : ComponentActivity() {
    private val viewModel: TmdbLibraryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TmdbLibraryViewModel(application, animationOnly = true) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TmdbLibraryScreen(
                    state = viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(Intent(this, TmdbDetailsActivity::class.java).apply {
                            putExtra(TmdbDetailsActivity.EXTRA_TMDB_ID, title.id)
                            putExtra(TmdbDetailsActivity.EXTRA_TMDB_TYPE, title.type.path)
                        })
                    },
                    onRetry = viewModel::refreshPopular,
                    onMore = { category ->
                        startActivity(Intent(this, TmdbListActivity::class.java).apply {
                            putExtra(TmdbListActivity.EXTRA_CATEGORY, category)
                        })
                    },
                    onBack = {},
                    animationOnly = true,
                    onOpenAniTube = { startActivity(Intent(this, AniTubeHomeActivity::class.java)) },
                    onSearchTitle = { startActivity(Intent(this, TolokaSearchActivity::class.java)) },
                    onOpenAllTmdb = { startActivity(Intent(this, TmdbLibraryActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onRetryMal = viewModel::refreshMalTop,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalLists()
    }

}
