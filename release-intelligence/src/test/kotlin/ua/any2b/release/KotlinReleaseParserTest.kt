package ua.any2b.release

import kotlin.test.*

class KotlinReleaseParserTest {
    private fun parse(title: String, source: ReleaseSource = ReleaseSource.GENERIC) =
        KotlinReleaseParser.parse(ReleaseInput(title, source))

    @Test fun parsesTolokaBilingualRelease() {
        val result = parse("Проводжальниця Фрірен / Sousou no Frieren [01-28 з 28] (2023-2024) WEB-DL 1080p Ukr/Jap | Sub Ukr [Amanogawa & FanVoxUA]")
        assertEquals(listOf("Проводжальниця Фрірен", "Sousou no Frieren"), result.titleCandidates.map { it.text })
        assertEquals(listOf(ReleaseYears(2023, 2024)), result.years)
        assertEquals(EpisodeCoverage.AbsoluteEpisodes(1, 28, 28), result.episodeCoverage)
        assertEquals(VideoMetadata("1080p", "WEB-DL"), result.video)
        assertEquals(setOf("uk", "ja"), result.audio.languages)
        assertEquals(setOf("uk"), result.subtitleLanguages)
        assertEquals(listOf("Amanogawa", "FanVoxUA"), result.releaseGroups)
    }

    @Test fun preservesExplicitPackStartAndSpecialSeason() {
        assertEquals(EpisodeCoverage.AbsoluteEpisodes(13, 28, 28), parse("Frieren [13-28 із 28]").episodeCoverage)
        assertEquals(EpisodeCoverage.SeasonEpisodes(0, 1, 2), parse("Title S00E01-02").episodeCoverage)
        assertEquals(EpisodeCoverage.SeasonPack(1, 3), parse("Title Сезони 1–3").episodeCoverage)
    }

    @Test fun parsesSceneTitleAndPrefixGroup() {
        val result = parse("[SubsPlease] Frieren.Beyond.Journeys.End.S01E01-12.1080p.WEB-DL.x265")
        assertEquals("Frieren Beyond Journeys End", result.titleCandidates.single().text)
        assertEquals(EpisodeCoverage.SeasonEpisodes(1, 1, 12), result.episodeCoverage)
        assertEquals(VideoMetadata("1080p", "WEB-DL", "H.265"), result.video)
        assertEquals(listOf("SubsPlease"), result.releaseGroups)
    }

    @Test fun doesNotInferAbsoluteCoverageWithoutSourceContext() {
        assertEquals(EpisodeCoverage.Unknown, parse("Title 12 з 24").episodeCoverage)
        assertEquals(EpisodeCoverage.AbsoluteEpisodes(1, 12, 24), parse("Title 12 з 24", ReleaseSource.TOLOKA).episodeCoverage)
        assertEquals(EpisodeCoverage.Unknown, parse("Title 01-24").episodeCoverage)
        val invalid = parse("Title [28-13 з 28]")
        assertEquals(EpisodeCoverage.Unknown, invalid.episodeCoverage)
        assertTrue(ParseWarning.INVALID_EPISODE_RANGE in invalid.warnings)
    }

    @Test fun preservesTitlesAndUnknownBrackets() {
        listOf("1917", "1984", "2001: A Space Odyssey", "Blade Runner 2049", "Mr. Robot", "Fate/stay night", "[Oshi no Ko]", "Movie (Director's Cut)", "The WEB").forEach { title ->
            assertEquals(title, parse(title).titleCandidates.single().text)
            assertTrue(parse(title).years.isEmpty())
        }
    }

    @Test fun distinguishesAudioCodecsAndSubtitles() {
        val result = parse("Title (2024) 1080p H.264 E-AC-3 DTS-HD 5.1 Sub Ukr")
        assertEquals("H.264", result.video.codec)
        assertEquals(setOf("E-AC-3", "DTS-HD"), result.audio.codecs)
        assertEquals(setOf("5.1"), result.audio.channels)
        assertTrue(result.audio.languages.isEmpty())
        assertEquals(setOf("uk"), result.subtitleLanguages)
        assertEquals("Title", result.titleCandidates.single().text)
    }

    @Test fun reportsConflictingMetadata() {
        val result = parse("Title 720p 1080p S01E01 S02E01")
        assertNull(result.video.resolution)
        assertTrue(ParseWarning.CONFLICTING_METADATA in result.warnings)
    }

    @Test fun evidenceUsesOriginalUtf16Positions() {
        val input = "🎬 Фільм (2024) WEB-DL 1080p"
        val result = parse(input)
        assertEquals("(2024)", input.substring(result.evidence.first { it.ruleId == "year" }.range))
        assertEquals("WEB-DL", input.substring(result.evidence.first { it.ruleId == "source" }.range))
        assertEquals(result, parse(input))
    }

    @Test fun handlesEmptyAndOversizedInput() {
        assertEquals(setOf(ParseWarning.EMPTY_TITLE), parse("  ").warnings)
        val raw = "a".repeat(4097)
        val result = parse(raw)
        assertEquals(raw, result.rawTitle)
        assertEquals(setOf(ParseWarning.INPUT_TOO_LONG), result.warnings)
        assertTrue(result.titleCandidates.isEmpty())
    }
}
