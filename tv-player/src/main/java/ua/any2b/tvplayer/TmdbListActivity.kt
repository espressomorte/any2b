package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbListScreen
import ua.any2b.tvplayer.ui.TmdbListViewModel

class TmdbListActivity : ComponentActivity() {
    private val viewModel: TmdbListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.open(intent.getStringExtra(EXTRA_CATEGORY).orEmpty())
        setContent {
            AppTheme {
                TmdbListScreen(viewModel.state.collectAsStateWithLifecycle().value,
                    onOpen = { title ->
                        startActivity(Intent(this, TmdbDetailsActivity::class.java).apply {
                            putExtra(TmdbDetailsActivity.EXTRA_TMDB_ID, title.id)
                            putExtra(TmdbDetailsActivity.EXTRA_TMDB_TYPE, title.type.path)
                        })
                    },
                    onSort = viewModel::changeSort,
                    onYearRange = viewModel::changeYearRange,
                    onLoadMore = viewModel::loadMore,
                    onBack = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPersonalList()
    }

    companion object { const val EXTRA_CATEGORY = "category" }
}
