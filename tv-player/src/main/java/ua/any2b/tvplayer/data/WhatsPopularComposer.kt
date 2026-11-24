package ua.any2b.tvplayer.data

import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

enum class WhatsPopularKind { MOVIE, TV, ANIME, CARTOON }

data class RankedWhatsPopular(
    val title: TmdbTitle,
    val kind: WhatsPopularKind,
    val sourceCount: Int,
    val bestRank: Int,
    val score: Double,
) {
    val key: String get() = "${title.type.path}:${title.id}"
}

/** Merges TMDB lists by media identity and selects a balanced, Toloka-confirmed row. */
object WhatsPopularComposer {
    private data class Aggregate(
        var title: TmdbTitle,
        val sourceRanks: MutableMap<String, Pair<Int, Int>> = linkedMapOf(),
    )

    fun rank(sources: List<Pair<String, List<TmdbTitle>>>): List<RankedWhatsPopular> {
        val aggregated = linkedMapOf<String, Aggregate>()
        sources.forEach { (source, titles) ->
            titles.forEachIndexed { index, title ->
                val key = "${title.type.path}:${title.id}"
                val aggregate = aggregated.getOrPut(key) { Aggregate(title) }
                val current = aggregate.title
                val preferred = when {
                    title.englishTitle != null && current.englishTitle == null -> title
                    (title.voteCount ?: 0) > (current.voteCount ?: 0) -> title
                    else -> current
                }
                val englishBackdrop = listOf(current, title)
                    .firstOrNull { it.englishTitle != null }?.backdropUrl
                aggregate.title = preferred.copy(
                    ukrainianTitle = current.ukrainianTitle ?: title.ukrainianTitle,
                    englishTitle = current.englishTitle ?: title.englishTitle,
                    backdropUrl = englishBackdrop ?: preferred.backdropUrl,
                )
                val rank = index + 1
                val previous = aggregate.sourceRanks[source]
                if (previous == null || rank < previous.first)
                    aggregate.sourceRanks[source] = rank to titles.size.coerceAtLeast(1)
            }
        }
        if (aggregated.isEmpty()) return emptyList()
        val mean = aggregated.values.mapNotNull { it.title.rating }
            .filter { it > 0.0 }.average().takeIf(Double::isFinite) ?: 6.0
        val priorVotes = 50.0
        return aggregated.values.map { item ->
            val rating = item.title.rating?.takeIf { it > 0.0 } ?: mean
            val votes = (item.title.voteCount ?: 0).coerceAtLeast(0).toDouble()
            val weightedRating = (votes / (votes + priorVotes)) * rating +
                (priorVotes / (votes + priorVotes)) * mean
            val best = item.sourceRanks.values.minByOrNull { (rank, size) ->
                normalizedRank(rank, size)
            }
            val rankBonus = best?.let { (rank, size) -> 0.3 * (1.0 - normalizedRank(rank, size)) } ?: 0.0
            val score = weightedRating + (item.sourceRanks.size - 1).coerceAtLeast(0) * 0.8 + rankBonus
            RankedWhatsPopular(item.title, kind(item.title), item.sourceRanks.size,
                best?.first ?: Int.MAX_VALUE, score)
        }.sortedWith(compareByDescending<RankedWhatsPopular> { it.score }
            .thenByDescending { it.sourceCount }.thenBy { it.bestRank })
    }

    fun balanced(ranked: List<RankedWhatsPopular>, availableKeys: Set<String>, limit: Int = 24): List<TmdbTitle> {
        if (limit <= 0) return emptyList()
        val available = ranked.filter { it.key in availableKeys }
        val quota = limit / WhatsPopularKind.entries.size
        val chosen = linkedMapOf<String, RankedWhatsPopular>()
        WhatsPopularKind.entries.forEach { kind ->
            available.asSequence().filter { it.kind == kind }.take(quota)
                .forEach { chosen.putIfAbsent(it.key, it) }
        }
        available.forEach { if (chosen.size < limit) chosen.putIfAbsent(it.key, it) }
        return chosen.values.sortedWith(compareByDescending<RankedWhatsPopular> { it.score }
            .thenByDescending { it.sourceCount }.thenBy { it.bestRank })
            .take(limit).map(RankedWhatsPopular::title)
    }

    private fun kind(title: TmdbTitle): WhatsPopularKind {
        if (16 in title.genreIds) {
            return if (title.originalLanguage.equals("ja", ignoreCase = true) ||
                title.originCountries.any { it.equals("JP", ignoreCase = true) })
                WhatsPopularKind.ANIME else WhatsPopularKind.CARTOON
        }
        return if (title.type == TmdbMediaType.MOVIE) WhatsPopularKind.MOVIE else WhatsPopularKind.TV
    }

    private fun normalizedRank(rank: Int, size: Int): Double =
        if (size <= 1) 0.0 else ((rank - 1).toDouble() / (size - 1)).coerceIn(0.0, 1.0)
}
