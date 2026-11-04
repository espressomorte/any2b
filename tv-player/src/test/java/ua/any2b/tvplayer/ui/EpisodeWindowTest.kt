package ua.any2b.tvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeWindowTest {
    private fun displayed(count: Int, selected: Int) = episodeWindowItems(count, selected)

    @Test fun secondEpisodeHasNoRedundantPreviousGap() {
        val window = displayed(26, 1)
        assertEquals(listOf(0, 1, 2, 25), window.filterIsInstance<EpisodeWindowItem.Episode>()
            .map { it.index })
        assertEquals(1, window.filterIsInstance<EpisodeWindowItem.Gap>().size)
    }

    @Test fun oneHiddenEpisodeIsShownInsteadOfEllipsisOnBothSides() {
        val nearStart = displayed(26, 3)
        assertEquals(listOf(0, 1, 2, 3, 4, 25),
            nearStart.filterIsInstance<EpisodeWindowItem.Episode>().map { it.index })
        val nearEnd = displayed(26, 22)
        assertEquals(listOf(0, 21, 22, 23, 24, 25),
            nearEnd.filterIsInstance<EpisodeWindowItem.Episode>().map { it.index })
    }

    @Test fun shortListsShowEveryEpisodeWithoutGaps() {
        for (count in 1..7) for (selected in 0 until count) {
            val window = displayed(count, selected)
            assertEquals((0 until count).toList(),
                window.filterIsInstance<EpisodeWindowItem.Episode>().map { it.index })
            assertTrue(window.none { it is EpisodeWindowItem.Gap })
        }
    }

    @Test fun gapKeepsItsSideWhileJumpTargetChanges() {
        val first = displayed(26, 1).filterIsInstance<EpisodeWindowItem.Gap>().single()
        val next = displayed(26, first.jumpTo).filterIsInstance<EpisodeWindowItem.Gap>()
            .first { it.side == EpisodeWindowItem.Side.NEXT }
        assertEquals(EpisodeWindowItem.Side.NEXT, first.side)
        assertEquals(first.side, next.side)
    }
}
