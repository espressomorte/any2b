package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.SearchScreen
import ua.any2b.tvplayer.ui.SearchViewModel

class SearchActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val query = viewModel.query.collectAsStateWithLifecycle().value
                val results = viewModel.searchResults.collectAsStateWithLifecycle().value
                val isLoading = viewModel.isLoading.collectAsStateWithLifecycle().value

                SearchScreen(
                    query = query,
                    searchResults = results,
                    isLoading = isLoading,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearchSubmit = { viewModel.searchNow() },
                    onOpen = { card ->
                        val intent = Intent(this, DetailsActivity::class.java).apply {
                            putExtra("ANIME_ID", card.id)
                        }
                        startActivity(intent)
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
