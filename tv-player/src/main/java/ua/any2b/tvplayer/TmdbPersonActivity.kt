package ua.any2b.tvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.any2b.tvplayer.ui.AppTheme
import ua.any2b.tvplayer.ui.TmdbPeopleViewModel
import ua.any2b.tvplayer.ui.TmdbPersonWorksScreen
import ua.any2b.core.tmdb.TmdbGeneralCatalog

class TmdbPersonActivity : ComponentActivity() {
    private val viewModel: TmdbPeopleViewModel by viewModels()
    private var returnToken by mutableIntStateOf(0)
    private var returningFromTitle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra(EXTRA_SOURCE)
        val id = intent.getIntExtra(EXTRA_PERSON_ID, 0)
        if (source != SOURCE_TMDB || id <= 0) { finish(); return }
        viewModel.loadFilmography(id)
        setContent {
            AppTheme {
                TmdbPersonWorksScreen(viewModel.filmography.collectAsStateWithLifecycle().value,
                    onBack = ::finish,
                    onRetry = { viewModel.loadFilmography(id, force = true) },
                    onOpen = { title ->
                        returningFromTitle = true
                        startActivity(TvNavigation.details(this, title,
                            torrentsOnly = TmdbGeneralCatalog.accepts(title)))
                    }, returnToken = returnToken)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (returningFromTitle) { returningFromTitle = false; returnToken++ }
    }

    companion object {
        const val EXTRA_SOURCE = "person_source"
        const val EXTRA_PERSON_ID = "person_id"
        const val SOURCE_TMDB = "tmdb"
    }
}
