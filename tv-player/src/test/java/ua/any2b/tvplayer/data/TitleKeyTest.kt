package ua.any2b.tvplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.any2b.core.tmdb.TmdbMediaType

class TitleKeyTest {
    @Test fun legacyNumericAniListIdsRemainReadableAndStable() {
        val key = TitleKey.parse("1234")
        assertEquals(TitleKey.AniList(1234), key)
        assertEquals("1234", key?.storageKey)
    }

    @Test fun tmdbKeysIncludeMediaTypeToAvoidMovieTvIdCollisions() {
        val movie = TitleKey.Tmdb(TmdbMediaType.MOVIE, 25)
        val tv = TitleKey.Tmdb(TmdbMediaType.TV, 25)
        assertEquals("tmdb:movie:25", movie.storageKey)
        assertEquals("tmdb:tv:25", tv.storageKey)
        assertEquals(movie, TitleKey.parse(movie.storageKey))
        assertEquals(tv, TitleKey.parse(tv.storageKey))
    }

    @Test fun malformedKeysAreRejected() {
        assertNull(TitleKey.parse("tmdb:other:12"))
        assertNull(TitleKey.parse("0"))
        assertNull(TitleKey.parse("tmdb:movie:bad"))
    }
}
