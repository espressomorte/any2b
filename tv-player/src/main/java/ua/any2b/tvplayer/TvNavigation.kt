package ua.any2b.tvplayer

import android.content.Context
import android.content.Intent
import ua.any2b.core.tmdb.TmdbTitle

/** Builds intents for the TMDB based screens so identity and optional search context travel together. */
object TvNavigation {
    fun details(
        context: Context,
        title: TmdbTitle,
        searchQuery: String? = null,
        preferredTitle: String? = null,
        originalLanguage: String? = title.originalLanguage,
        originalTitle: String? = title.originalTitle,
        torrentsOnly: Boolean = false,
    ): Intent = Intent(context, TmdbDetailsActivity::class.java).apply {
        putExtra(TmdbDetailsActivity.EXTRA_TMDB_ID, title.id)
        putExtra(TmdbDetailsActivity.EXTRA_TMDB_TYPE, title.type.path)
        title.matchedSeasonNumber?.let { putExtra(TmdbDetailsActivity.EXTRA_TMDB_SEASON, it) }
        searchQuery?.let { putExtra(TmdbDetailsActivity.EXTRA_SEARCH_QUERY, it) }
        preferredTitle?.let { putExtra(TmdbDetailsActivity.EXTRA_PREFERRED_TITLE, it) }
        originalLanguage?.let { putExtra(TmdbDetailsActivity.EXTRA_ORIGINAL_LANGUAGE, it) }
        originalTitle?.let { putExtra(TmdbDetailsActivity.EXTRA_ORIGINAL_TITLE, it) }
        if (torrentsOnly) putExtra(TmdbDetailsActivity.EXTRA_TORRENTS_ONLY, true)
    }

    fun details(
        context: Context,
        id: Int,
        typePath: String,
        searchQuery: String? = null,
        preferredTitle: String? = null,
        originalLanguage: String? = null,
        originalTitle: String? = null,
        torrentsOnly: Boolean = false,
    ): Intent = Intent(context, TmdbDetailsActivity::class.java).apply {
        putExtra(TmdbDetailsActivity.EXTRA_TMDB_ID, id)
        putExtra(TmdbDetailsActivity.EXTRA_TMDB_TYPE, typePath)
        searchQuery?.let { putExtra(TmdbDetailsActivity.EXTRA_SEARCH_QUERY, it) }
        preferredTitle?.let { putExtra(TmdbDetailsActivity.EXTRA_PREFERRED_TITLE, it) }
        originalLanguage?.let { putExtra(TmdbDetailsActivity.EXTRA_ORIGINAL_LANGUAGE, it) }
        originalTitle?.let { putExtra(TmdbDetailsActivity.EXTRA_ORIGINAL_TITLE, it) }
        if (torrentsOnly) putExtra(TmdbDetailsActivity.EXTRA_TORRENTS_ONLY, true)
    }
}
