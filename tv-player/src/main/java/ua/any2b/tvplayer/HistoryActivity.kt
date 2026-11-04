package ua.any2b.tvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Redirects the legacy AniList history entry point to the shared TMDB history list. */
class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TmdbListActivity::class.java).apply {
            putExtra(TmdbListActivity.EXTRA_CATEGORY, "history")
        })
        finish()
    }
}
