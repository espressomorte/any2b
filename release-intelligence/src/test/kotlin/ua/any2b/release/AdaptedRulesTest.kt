package ua.any2b.release

import kotlin.test.*

class AdaptedRulesTest {
    private fun parse(title: String) = KotlinReleaseParser.parse(ReleaseInput(title, ReleaseSource.TOLOKA))

    @Test fun preservesCountsAndSeparatesSubtitleContext() {
        val result = parse("Фільм / Movie (2024) BDRemux 1080p 2xUkr/Eng | Sub Dut [FRA Transfer]")
        assertEquals(listOf("Фільм", "Movie"), result.titleCandidates.map { it.text })
        assertEquals(listOf(AudioLanguageTrack("uk", 2), AudioLanguageTrack("en")), result.audio.tracks)
        assertEquals(setOf("nl"), result.subtitleLanguages)
        assertFalse("ru" in result.audio.languages)
        assertFalse("fr" in result.audio.languages)
        assertEquals(setOf("FRA Transfer"), result.notes)
    }

    @Test fun keepsUnknownTotalAndExplicitStart() {
        val result = parse("Назва (Сезон 2, серії 13-19 із ХХ) / Title (Season 2) (2026) WEBRip 1080p")
        assertEquals(EpisodeCoverage.SeasonEpisodes(2, 13, 19), result.episodeCoverage)
        assertEquals(listOf("Назва", "Title"), result.titleCandidates.map { it.text })
        assertFalse(ParseWarning.CONFLICTING_METADATA in result.warnings)
    }

    @Test fun preservesKnownTotalAndFlagsCountAssumption() {
        val result = parse("Назва (Сезон 3, серії 08 з 12) / Title (Season 3) (2026) WEBDLRip 1080p")
        assertEquals(EpisodeCoverage.SeasonEpisodes(3, 1, 8, 12), result.episodeCoverage)
        assertTrue(ParseWarning.ASSUMED_EPISODE_START in result.warnings)
        assertFalse(ParseWarning.CONFLICTING_METADATA in result.warnings)
    }

    @Test fun doesNotInferMonoOrLanguageFromTitle() {
        val result = parse("Дядечко / Ojisan wa Kawaii Mono ga Osuki (2026) 1080p Jap | Sub Ukr")
        assertTrue(result.audio.channels.isEmpty())
        assertEquals(setOf("ja"), result.audio.languages)
        assertEquals(setOf("uk"), result.subtitleLanguages)
    }

    @Test fun retainsHdrEditionsAndEvidence() {
        val raw = "Фільм / Movie (2024) WEB-DL 2160p HDR DV P8 H.265 [Theatrical Cut]"
        val result = parse(raw)
        assertEquals(setOf("HDR", "DV"), result.video.hdr)
        assertEquals(setOf("Theatrical Cut"), result.editions)
        assertEquals(listOf("Фільм", "Movie"), result.titleCandidates.map { it.text })
        assertEquals("DV P8", raw.substring(result.evidence.first { it.value == "DV" }.range))
    }

    @Test fun preservesUnknownNotesAndTitleTechnicalWords() {
        val result = parse("The HDR Story / Історія AVC (2024) WEB-DL 1080p [Unknown Cut]")
        assertEquals(listOf("The HDR Story", "Історія AVC [Unknown Cut]"), result.titleCandidates.map { it.text })
        assertTrue(result.video.hdr.isEmpty())
        assertNull(result.video.codec)
        assertTrue(ParseWarning.UNKNOWN_BRACKET in result.warnings)
    }

    @Test fun invalidLocalizedRangeIsNeverConfirmed() {
        val result = parse("Title (Сезон 1, серії 13-9 з 12) (2026) 1080p")
        assertTrue(ParseWarning.INVALID_EPISODE_RANGE in result.warnings)
        assertFalse(result.episodeCoverage is EpisodeCoverage.SeasonEpisodes)
    }

    @Test fun conflictingAliasSeasonIsReported() {
        val result = parse("Назва (Сезон 1, серії 1-9 з 12) / Title (Season 2) (2026) WEBRip 1080p")
        assertTrue(ParseWarning.CONFLICTING_METADATA in result.warnings)
    }
}
