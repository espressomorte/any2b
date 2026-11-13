package ua.any2b.tvplayer.data

import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

/** Sort one card per TMDB title using its newest Toloka release. */
internal object TolokaRssRanker {
    fun rank(releases: List<Pair<TmdbTitle, Long>>, trendingTvIds: Set<Int>,
        now: Long): List<TmdbTitle> = releases
        .groupBy { it.first.type to it.first.id }
        .values.map { group ->
            group.maxBy { it.second }
        }.sortedWith(compareByDescending<Pair<TmdbTitle, Long>> { (title, publishedAt) ->
            val rating = title.rating?.takeIf { it.isFinite() && it > 0.0 } ?: 6.5
            val hoursOld = (now - publishedAt).coerceAtLeast(0L).toDouble() / 3_600_000.0
            val freshness = (6.0 - hoursOld / 8.0).coerceAtLeast(0.0)
            val trending = if (title.type == TmdbMediaType.TV && title.id in trendingTvIds)
                4.0 else 0.0
            rating + freshness + trending
        }.thenByDescending { it.second }.thenBy { it.first.id })
        .map { it.first }
}
