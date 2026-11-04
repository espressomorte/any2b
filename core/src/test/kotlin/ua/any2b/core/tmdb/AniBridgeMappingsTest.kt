package ua.any2b.core.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AniBridgeMappingsTest {
    @Test
    fun keepsSeasonsAndEpisodeRangesInBothExplicitDirections() {
        val index = AniBridgeMappings.parse("""{
          "${'$'}meta":{"schema_version":"3.0.3"},
          "anilist:100":{"tmdb_show:42:s2":{"1-12":"13-24"},
                         "tmdb_show:42:s3":{"13-24":"1-12"}},
          "tmdb_show:42:s2":{"anilist:100":{"13-24":"1-12"}},
          "tmdb_show:42:s3":{"anilist:100":{"1-12":"13-24"}},
          "anilist:200":{"tmdb_movie:99":{"1":"1"}},
          "mal:300":{"tmdb_show:42:s2":{"1-12":"13-24"}}
        }""")
        assertEquals(2, index.byAniList.getValue(100).size)
        assertEquals(AniBridgeEpisodeRange("1-12", "13-24"),
            index.byAniList.getValue(100).single { it.season == 2 }.episodes.single())
        assertEquals(2, index.anilistFor(TmdbMediaType.TV, 42, 2).single().season)
        assertEquals(1, index.preferredFor(200, "MOVIE")?.episodes?.size)
        assertEquals(2, index.preferredForMal(300, "tv")?.season)
    }

    @Test
    fun reverseLookupRequiresExplicitReverseEntry() {
        val index = AniBridgeMappings.parse("""{
          "${'$'}meta":{"schema_version":"3.0.3"},
          "anilist:100":{"tmdb_show:42:s2":{"1-12":"1-12"}}
        }""")
        assertEquals(2, index.preferredFor(100, "TV")?.season)
        assertNull(index.anilistFor(TmdbMediaType.TV, 42).firstOrNull())
    }
}
