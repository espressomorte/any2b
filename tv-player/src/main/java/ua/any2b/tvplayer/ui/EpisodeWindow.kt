package ua.any2b.tvplayer.ui

internal sealed interface EpisodeWindowItem {
    data class Episode(val index: Int) : EpisodeWindowItem
    enum class Side { PREVIOUS, NEXT }
    data class Gap(val side: Side, val jumpTo: Int) : EpisodeWindowItem
}

/** Show every short list; for long lists, ellipses represent only genuinely hidden ranges. */
internal fun episodeWindowItems(count: Int, selectedIndex: Int): List<EpisodeWindowItem> {
    if (count <= 0) return emptyList()
    if (count <= 7) return (0 until count).map(EpisodeWindowItem::Episode)

    val selected = selectedIndex.coerceIn(0, count - 1)
    val visible = buildSet {
        add(0)
        add(count - 1)
        for (index in selected - 1..selected + 1) {
            if (index in 0 until count) add(index)
        }
    }.sorted()
    return buildList {
        visible.forEachIndexed { position, index ->
            if (position > 0) {
                val previous = visible[position - 1]
                when (index - previous) {
                    2 -> add(EpisodeWindowItem.Episode(previous + 1))
                    in 3..Int.MAX_VALUE -> {
                        val jump = if (index <= selected)
                            (selected - 3).coerceIn(previous + 1, index - 1)
                        else (selected + 3).coerceIn(previous + 1, index - 1)
                        add(EpisodeWindowItem.Gap(
                            if (index <= selected) EpisodeWindowItem.Side.PREVIOUS
                            else EpisodeWindowItem.Side.NEXT,
                            jump,
                        ))
                    }
                }
            }
            add(EpisodeWindowItem.Episode(index))
        }
    }
}
