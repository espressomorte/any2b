package ua.any2b.core.toloka

import ua.any2b.release.EpisodeCoverage
import ua.any2b.release.KotlinReleaseParser
import ua.any2b.release.ParseWarning
import ua.any2b.release.ReleaseInput
import ua.any2b.release.ReleaseSource

data class TolokaReleaseMetadata(
    val cleanTitle: String,
    val qualityTags: List<String>,
    val audioTags: List<String>,
    val subtitleTags: List<String>,
    val languageTags: List<String>,
    val season: Int?,
    val seasonEnd: Int?,
    val episodeStart: Int?,
    val episodeEnd: Int?,
    val episodeTotal: Int? = null,
    val isAbsoluteEpisodeRange: Boolean = false,
    val qualityScore: Int,
    val titleCandidates: List<String> = listOf(cleanTitle),
    val yearRanges: List<IntRange> = emptyList(),
    val hasConflictingMetadata: Boolean = false,
)

data class TolokaSeasonEpisodeRange(
    val seasonNumber: Int,
    val episodeStart: Int,
    val episodeEnd: Int,
)

/** Extracts display and ranking hints without changing the original torrent title. */
object TolokaReleaseMetadataParser {
    fun parse(title: String): TolokaReleaseMetadata {
        val parsed = KotlinReleaseParser.parse(
            ReleaseInput(title, ReleaseSource.TOLOKA))
        val coverage = parsed.episodeCoverage
        val season = when (coverage) {
            is EpisodeCoverage.SeasonEpisodes -> coverage.season
            is EpisodeCoverage.SeasonPack -> coverage.start
            else -> null
        }
        val seasonEnd = (coverage as? EpisodeCoverage.SeasonPack)?.end ?: season
        val start = when (coverage) {
            is EpisodeCoverage.SeasonEpisodes -> coverage.start
            is EpisodeCoverage.AbsoluteEpisodes -> coverage.start
            else -> null
        }
        val end = when (coverage) {
            is EpisodeCoverage.SeasonEpisodes -> coverage.end
            is EpisodeCoverage.AbsoluteEpisodes -> coverage.end
            else -> null
        }
        val total = when (coverage) {
            is EpisodeCoverage.SeasonEpisodes -> coverage.total
            is EpisodeCoverage.AbsoluteEpisodes -> coverage.total
            else -> null
        }
        val video = parsed.video
        val tags = listOfNotNull(video.resolution, video.source, video.codec,
            "HDR".takeIf { video.hdr.isNotEmpty() }, video.bitDepth?.let { "$it-bit" }) +
            parsed.audio.codecs + parsed.audio.channels + listOfNotNull(
                season?.let { if (seasonEnd != it) "S%02d–%02d".format(it, seasonEnd) else "S%02d".format(it) },
                start?.let { if (end != it) "E%02d–%02d".format(it, end) else "E%02d".format(it) })
        fun label(code: String) = mapOf("uk" to "Ukr", "en" to "Eng", "ja" to "Jap", "ru" to "Rus",
            "de" to "Ger", "nl" to "Dut", "fr" to "Fre", "pl" to "Pol")[code] ?: code
        val audio = parsed.audio.tracks.joinToString("/") { track ->
            (track.count?.takeIf { it > 1 }?.let { "$it×" } ?: "") + label(track.language)
        }
        val resolutionScore = when (video.resolution) {
            "4320p" -> 5; "2160p" -> 4; "1440p", "1080p" -> 3
            "1080i", "720p" -> 2; "576p", "576i", "480p", "480i" -> 1; else -> 0
        }
        val qualityScore = resolutionScore + (if (video.source in setOf("WEB-DL", "WEBDLRip", "WEBRip", "BluRay", "BDRip")) 1 else 0) +
            (if (video.codec in setOf("H.265", "AV1")) 1 else 0)
        return TolokaReleaseMetadata(
            cleanTitle = parsed.titleCandidates.joinToString(" / ") { it.text }.ifBlank { title.trim() },
            qualityTags = tags.distinct(), audioTags = listOf(audio).filter(String::isNotBlank),
            subtitleTags = parsed.subtitleLanguages.map(::label), languageTags = emptyList(),
            season = season, seasonEnd = seasonEnd, episodeStart = start, episodeEnd = end,
            episodeTotal = total, // An unqualified Episode 12 filename is not evidence of absolute series numbering.
            isAbsoluteEpisodeRange = coverage is EpisodeCoverage.AbsoluteEpisodes &&
                parsed.evidence.none { it.ruleId == "labelledEpisodes" },
            qualityScore = qualityScore, titleCandidates = parsed.titleCandidates.map { it.text },
            yearRanges = parsed.years.map { it.start..it.end },
            hasConflictingMetadata = ParseWarning.CONFLICTING_METADATA in parsed.warnings,
        )
    }

    /** Maps a 1-based absolute episode range onto TMDB's regular season episode ranges. */
    fun mapAbsoluteEpisodes(
        release: TolokaReleaseMetadata,
        seasons: List<ua.any2b.core.tmdb.TmdbSeason>,
    ): List<TolokaSeasonEpisodeRange>? {
        if (!release.isAbsoluteEpisodeRange) return null
        val start = release.episodeStart ?: return null
        val end = release.episodeEnd ?: return null
        val orderedSeasons = seasons.filter { it.number > 0 }
            .sortedBy { it.number }
        if (orderedSeasons.isEmpty() || orderedSeasons.any { (it.episodeCount ?: 0) <= 0 }) return null
        val totalEpisodes = orderedSeasons.sumOf { it.episodeCount ?: 0 }
        if (end > totalEpisodes || (release.episodeTotal != null && end > release.episodeTotal)) return null
        if (release.episodeTotal != null && release.episodeTotal != totalEpisodes) return null

        var absoluteSeasonStart = 1
        val mapped = mutableListOf<TolokaSeasonEpisodeRange>()
        orderedSeasons.forEach { season ->
            val count = season.episodeCount ?: return null
            val absoluteSeasonEnd = absoluteSeasonStart + count - 1
            val overlapStart = maxOf(start, absoluteSeasonStart)
            val overlapEnd = minOf(end, absoluteSeasonEnd)
            if (overlapStart <= overlapEnd) {
                mapped += TolokaSeasonEpisodeRange(
                    seasonNumber = season.number,
                    episodeStart = overlapStart - absoluteSeasonStart + 1,
                    episodeEnd = overlapEnd - absoluteSeasonStart + 1,
                )
            }
            absoluteSeasonStart = absoluteSeasonEnd + 1
        }
        return mapped.takeIf { it.isNotEmpty() }
    }
}
