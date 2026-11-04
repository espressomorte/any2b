package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel

class TmdbLibraryActivity : ComponentActivity() {
    private val viewModel: TmdbLibraryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TmdbLibraryViewModel(application, animationOnly = false) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TmdbLibraryScreen(viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(TvNavigation.details(this, title, torrentsOnly = true))
                    },
                    onRetry = viewModel::refreshPopular,
                    onMore = { category ->
                        startActivity(Intent(this, TmdbListActivity::class.java).apply {
                            putExtra(TmdbListActivity.EXTRA_CATEGORY, category)
                            putExtra(TmdbListActivity.EXTRA_GENERAL_CATALOG, true)
                        })
                    },
                    onSearchTitle = {
                        startActivity(Intent(this, TolokaSearchActivity::class.java).apply {
                            putExtra(TolokaSearchActivity.EXTRA_GENERAL_CATALOG, true)
                        })
                    },
                    onSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onBack = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalLists()
    }
}
