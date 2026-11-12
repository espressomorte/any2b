package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TolokaEpisodeFileIndexTest {
    @Test fun resolvesNamedEpisodesAndRespectsSeason() {
        val files = listOf(
            TorrServerFile(2, "Show/S04E12.mkv", 100),
            TorrServerFile(3, "Show/S04E13.mkv", 100),
        )
        assertEquals(3, TolokaEpisodeFileIndex.find(files, 4, 13)?.id)
        assertNull(TolokaEpisodeFileIndex.find(files, 3, 13))
    }

    @Test fun resolvesNumberedFilesInsideSeasonFolder() {
        val files = listOf(
            TorrServerFile(2, "Show/Season 4/12 - Part.mkv", 100),
            TorrServerFile(3, "Show/Season 4/13 - Part.mkv", 100),
        )
        assertEquals(3, TolokaEpisodeFileIndex.find(files, 4, 13)?.id)
        assertNull(TolokaEpisodeFileIndex.find(files, 1, 13))
    }

    @Test fun resolvesOneDirectoryPlaylistOnlyForMatchingSeason() {
        val files = listOf(
            TorrServerFile(2, "Show/Part A.mkv", 100),
            TorrServerFile(3, "Show/Part B.mkv", 100),
        )
        assertEquals(3, TolokaEpisodeFileIndex.find(files, 1, 2)?.id)
        assertNull(TolokaEpisodeFileIndex.find(files, 2, 2))
        assertNull(TolokaEpisodeFileIndex.find(files, 1, 3))
    }
}
