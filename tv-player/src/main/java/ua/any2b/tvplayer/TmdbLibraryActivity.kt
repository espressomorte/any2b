package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbLibraryScreen
import ua.any2b.tvplayer.ui.TmdbLibraryViewModel

class TmdbLibraryActivity : ComponentActivity() {
    private val viewModel: TmdbLibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TmdbLibraryScreen(viewModel.state.collectAsStateWithLifecycle().value,
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
                    onBack = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalLists()
    }
}
