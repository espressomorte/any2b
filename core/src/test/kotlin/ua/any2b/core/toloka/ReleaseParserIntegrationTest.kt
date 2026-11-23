package ua.any2b.core.toloka

import kotlin.test.*
import ua.any2b.core.tmdb.TmdbSeason

class ReleaseParserIntegrationTest {
    @Test fun adapterUsesStructuredMetadataForDisplayAndAbsolutePlayback() {
        val result = TolokaReleaseMetadataParser.parse(
            "Проводжальниця Фрірен / Sousou no Frieren [13-28 із 28] (2023-2024) WEB-DL 1080p H.265 2xUkr/Jap | Sub Ukr")
        assertEquals(listOf("Проводжальниця Фрірен", "Sousou no Frieren"), result.titleCandidates)
        assertEquals(listOf(2023..2024), result.yearRanges)
        assertEquals(listOf("2×Ukr/Jap"), result.audioTags)
        assertEquals(listOf("Ukr"), result.subtitleTags)
        assertEquals(13, result.episodeStart)
        assertEquals(28, result.episodeTotal)
        assertTrue(result.isAbsoluteEpisodeRange)
        assertEquals(listOf(TolokaSeasonEpisodeRange(1, 13, 28)), TolokaReleaseMetadataParser.mapAbsoluteEpisodes(
            result, listOf(TmdbSeason(number = 1, name = "Season 1", year = 2023, episodeCount = 28))))
    }

    @Test fun adapterDistinguishesSeasonEpisodesFromAbsolutePacks() {
        val result = TolokaReleaseMetadataParser.parse(
            "Назва (Сезон 3, серії 08 з 12) / Title (Season 3) (2026) WEBDLRip 1080p Ukr/Jap | Sub Ukr")
        assertEquals(3, result.season)
        assertEquals(8, result.episodeEnd)
        assertEquals(12, result.episodeTotal)
        assertFalse(result.isAbsoluteEpisodeRange)
        assertFalse(result.hasConflictingMetadata)
        assertEquals(listOf("Назва", "Title"), TolokaReleaseQueries.names(result.cleanTitle))
    }

    @Test fun queryCacheSeparatesSeasonsReleasesAndUpdatedTitles() {
        fun key(id: String, title: String) = TolokaReleaseQueries.cacheKey(id, title, true)
        assertNotEquals(key("1", "Title S01"), key("1", "Title S02"))
        assertNotEquals(key("1", "Title S01"), key("2", "Title S01"))
        assertEquals(key("1", "Title S01"), key("1", "Title S01"))
        assertEquals(listOf("1917"), TolokaReleaseQueries.names("1917 (2019) 1080p"))
        assertEquals(2019, TolokaReleaseQueries.year("1917 (2019) 1080p"))
        assertNull(TolokaReleaseQueries.year("Blade Runner 2049"))
    }

    @Test fun retainsLegacyEpisodeAndSeasonSpellings() {
        listOf("Title 2nd Season", "Title 2 сезон", "Title 2 сезон 2024 WEB-DL").forEach {
            assertEquals(2, TolokaReleaseMetadataParser.parse(it).season, it)
        }
        listOf("Title S01 01-12.mkv", "Title S01 12 episodes", "Title S01 episodes 01-12").forEach {
            val parsed = TolokaReleaseMetadataParser.parse(it)
            assertEquals(1, parsed.season, it)
            assertEquals(1, parsed.episodeStart, it)
            assertEquals(12, parsed.episodeEnd, it)
        }
    }
    @Test fun seasonQualifiedBareYearIsPreservedForFiltering() {
        assertEquals(listOf(2021..2021), TolokaReleaseMetadataParser.parse("Title 2 сезон 2021 WEB-DL").yearRanges)
        assertEquals(listOf(2024..2024), TolokaReleaseMetadataParser.parse("Title 2nd Season 2024 Ukrainian dub").yearRanges)
        val file = TolokaReleaseMetadataParser.parse("Episode 12.mkv")
        assertEquals(12, file.episodeStart)
        assertFalse(file.isAbsoluteEpisodeRange)
    }

}
