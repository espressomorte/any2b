package ua.any2b.core.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FribbMappingsTest {
    @Test
    fun parsesTvMoviesAndDuplicateAniListEntries() {
        val index = FribbMappings.parseIndex("""[
            {"anilist_id": 10, "mal_id": 20, "themoviedb_id": {"tv": 42}},
            {"anilist_id": 10, "mal_id": 20, "themoviedb_id": {"movie": [71, 72]}},
            {"anilist_id": 10, "themoviedb_id": {"tv": 42}},
            {"anilist_id": 11, "themoviedb_id": {"movie": [99]}},
            {"anilist_id": 12},
            {"anilist_id": 0, "themoviedb_id": {"tv": 1}}
        ]""")
        val mappings = index.byAniList

        assertEquals(listOf(FribbTmdbId(TmdbMediaType.TV, 42),
            FribbTmdbId(TmdbMediaType.MOVIE, 71),
            FribbTmdbId(TmdbMediaType.MOVIE, 72)), mappings[10])
        assertEquals(FribbTmdbId(TmdbMediaType.MOVIE, 71),
            FribbMappings.preferred(mappings.getValue(10), "MOVIE"))
        assertEquals(FribbTmdbId(TmdbMediaType.TV, 42),
            FribbMappings.preferred(mappings.getValue(10), "TV"))
        assertEquals(FribbTmdbId(TmdbMediaType.MOVIE, 99),
            FribbMappings.preferred(mappings.getValue(11), "OVA"))
        assertNull(mappings[12])
        assertEquals(mappings, FribbMappings.parseCompact(FribbMappings.compact(mappings)))
        assertEquals(mappings[10], index.byMal[20])
        assertEquals(index, FribbMappings.parseCompactIndex(FribbMappings.compactIndex(index)))
    }
}
