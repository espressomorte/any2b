package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.HistoryScreen
import ua.any2b.tvplayer.ui.HistoryViewModel

class HistoryActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val history = viewModel.history.collectAsStateWithLifecycle().value
                HistoryScreen(
                    history = history,
                    onOpen = { animeId ->
                        val intent = Intent(this, DetailsActivity::class.java).apply {
                            putExtra("ANIME_ID", animeId)
                        }
                        startActivity(intent)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHistory()
    }
}
