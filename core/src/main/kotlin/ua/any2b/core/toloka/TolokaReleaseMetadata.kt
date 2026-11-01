package ua.any2b.core.toloka

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
)

data class TolokaSeasonEpisodeRange(
    val seasonNumber: Int,
    val episodeStart: Int,
    val episodeEnd: Int,
)

/** Extracts display and ranking hints without changing the original torrent title. */
object TolokaReleaseMetadataParser {
    private val seasonRange = Regex(
        """(?i)(?:\bseasons?[ ._-]*|\bсезон(?:и|ів)?[ ._-]*|(?<![A-Z0-9])S)(\d{1,2})[ ._-]*(?:-|–|to|до)[ ._-]*(?:S)?(\d{1,2})\b""",
    )
    private val ordinalSeason = Regex(
        """(?i)(?<!\d)(\d{1,2})(?:st|nd|rd|th)[ ._-]*season\b""",
    )
    private val absoluteEpisodePack = Regex(
        """(?i)(?<!\d)0*(\d{1,3})\s*(?:з|of)\s*0*(\d{1,4})(?!\d)""",
    )
    private val seasonEpisode = Regex(
        """(?i)(?<![A-Z0-9])S(\d{1,2})[ ._-]*E(\d{1,3})(?:[ ._-]*(?:-|–)[ ._-]*(?:E)?(\d{1,3}))?""",
    )
    private val seasonMarker = Regex(
        """(?i)(?:\bseason[ ._-]*|\bсезон[ ._-]*|\bсез[ ._-]*|(?<![A-Z0-9])S)(\d{1,2})\b""",
    )
    private val episodeRange = Regex(
        """(?i)(?<!\d)(\d{1,3})[ ._-]*(?:-|–|\.\.)[ ._-]*(\d{1,3})\s*(?:episodes?|eps?|сер(?:ії|ія|ий))\b""",
    )
    private val episodeMarker = Regex(
        """(?i)\b(?:episodes?|ep|сер(?:ії|ія|ий))[ ._-]*0*(\d{1,3})(?:[ ._-]*(?:-|–|\.\.)[ ._-]*(?:ep)?0*(\d{1,3}))?""",
    )
    private val rangeWithSeason = Regex(
        """(?i)(?<!\d)(\d{1,3})[ ._-]*(?:-|–|\.\.)[ ._-]*(\d{1,3})(?:\s*(?:of|з)\s*\d{1,3})?(?=\s*(?:\b(?:mkv|mp4|avi|1080p|720p|2160p)\b|[\]\)]|$))""",
    )
    private val episodeCount = Regex(
        """(?i)\b(\d{1,3})\s*(?:episodes?|eps?|сер(?:ії|ія|ий))\b""",
    )

    private data class TagRule(val regex: Regex, val label: String, val score: Int = 0)

    private val qualityRules = listOf(
        TagRule(Regex("""(?i)\b(?:4320p|8k)\b"""), "4320p", 5),
        TagRule(Regex("""(?i)\b(?:2160p|4k|uhd)\b"""), "2160p", 4),
        TagRule(Regex("""(?i)\b1440p\b"""), "1440p", 3),
        TagRule(Regex("""(?i)\b1080i\b"""), "1080i", 2),
        TagRule(Regex("""(?i)\b1080p\b"""), "1080p", 3),
        TagRule(Regex("""(?i)\b720p\b"""), "720p", 2),
        TagRule(Regex("""(?i)\b(?:576p|480p)\b"""), "480p", 1),
        TagRule(Regex("""(?i)\bweb[ ._-]*dl\b"""), "WEB-DL", 1),
        TagRule(Regex("""(?i)\bweb[ ._-]*rip\b"""), "WEBRip", 1),
        TagRule(Regex("""(?i)\bweb\b"""), "WEB", 1),
        TagRule(Regex("""(?i)\b(?:blu[ ._-]*ray|bdrip|bdremux|remux)\b"""), "BluRay", 1),
        TagRule(Regex("""(?i)\b(?:hdtv|dvdrip)\b"""), "HDTV"),
        TagRule(Regex("""(?i)\b(?:x264|h[ ._-]?264|avc)\b"""), "H.264"),
        TagRule(Regex("""(?i)\b(?:x265|h[ ._-]?265|hevc)\b"""), "H.265", 1),
        TagRule(Regex("""(?i)\bav1\b"""), "AV1", 1),
        TagRule(Regex("""(?i)\b(?:hdr10\+?|hdr|dolby[ ._-]*vision|dv)\b"""), "HDR"),
        TagRule(Regex("""(?i)\b10[ ._-]*bit\b"""), "10-bit"),
        TagRule(Regex("""(?i)\baac\b"""), "AAC"),
        TagRule(Regex("""(?i)\b(?:ac3|eac3|ddp)\b"""), "E-AC-3"),
        TagRule(Regex("""(?i)\bdts(?:[ ._-]*hd(?:[ ._-]*ma)?)?\b"""), "DTS-HD"),
        TagRule(Regex("""(?i)\btruehd\b"""), "TrueHD"),
        TagRule(Regex("""(?i)\bflac\b"""), "FLAC"),
        TagRule(Regex("""(?i)\bopus\b"""), "Opus"),
        TagRule(Regex("""(?i)\b(?:2\.0|5\.1|7\.1)\b"""), "Аудіоканали"),
    )
    private val languageToken = "(?:ukrainian|ukr|english|eng|japanese|jap|jpn|russian|rus|chinese|chi|polish|pol|german|ger|french|fre|spanish|spa|italian|ita|korean|kor)"
    private val audioLanguageList = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:\d+\s*[xх×]\s*)?$languageToken(?:\s*[/+,&]\s*(?:\d+\s*[xх×]\s*)?$languageToken)+(?![\p{L}\p{N}])""",
    )
    private val audioLanguageItem = Regex("""(?i)(?:(\d+)\s*[xх×]\s*)?($languageToken)""")
    private val dualAudio = Regex("""(?i)(?<![\p{L}\p{N}])(?:dual[ ._-]*audio|multi[ ._-]*audio)(?![\p{L}\p{N}])""")
    private val subtitleLanguage = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:multi[ ._-]*subs?|multiple[ ._-]*subs?|subs?[ ._-]*(?:$languageToken)|(?:$languageToken)[ ._-]*subs?|subtitles?[ ._-]*(?:$languageToken)|(?:$languageToken)[ ._-]*subtitles?)(?![\p{L}\p{N}])""",
    )
    private val dubbedUkr = Regex(
        """(?i)(?:\b(?:ukr|ukrainian)[ ._-]*(?:dub|voice|audio)\b|\b(?:dub|voice|audio)[ ._-]*(?:ukr|ukrainian)\b|озвуч(?:ка|ення)\s*укр|укр(?:аїнськ[а-я]*)?\s*озвуч(?:ка|ення))""",
    )
    private val genericUkr = Regex("""(?i)(?<![\p{L}\p{N}])(?:ukrainian|ukr|укр)(?![\p{L}\p{N}])""")
    private val japanese = Regex("""(?i)(?<![\p{L}\p{N}])(?:japanese|jap|jpn|япон(?:ська|ськ)?)(?![\p{L}\p{N}])""")

    private val languageLabels = mapOf(
        "ukrainian" to "Ukr", "ukr" to "Ukr", "english" to "Eng", "eng" to "Eng",
        "japanese" to "Jap", "jap" to "Jap", "jpn" to "Jap", "russian" to "Rus", "rus" to "Rus",
        "chinese" to "Chi", "chi" to "Chi", "polish" to "Pol", "pol" to "Pol",
        "german" to "Ger", "ger" to "Ger", "french" to "Fre", "fre" to "Fre",
        "spanish" to "Spa", "spa" to "Spa", "italian" to "Ita", "ita" to "Ita",
        "korean" to "Kor", "kor" to "Kor",
    )

    fun parse(title: String): TolokaReleaseMetadata {
        var remaining = title
        var season: Int? = null
        var seasonEnd: Int? = null
        var episodeStart: Int? = null
        var episodeEnd: Int? = null
        var episodeTotal: Int? = null
        var isAbsoluteEpisodeRange = false

        seasonRange.find(remaining)?.let { match ->
            season = match.groupValues[1].toIntOrNull()
            seasonEnd = match.groupValues[2].toIntOrNull()
            remaining = remaining.replaceRange(match.range, " ")
        } ?: ordinalSeason.find(remaining)?.let { match ->
            season = match.groupValues[1].toIntOrNull()
            seasonEnd = season
            remaining = remaining.replaceRange(match.range, " ")
        }
        absoluteEpisodePack.find(remaining)?.let { match ->
            val end = match.groupValues[1].toIntOrNull()
            val total = match.groupValues[2].toIntOrNull()
            if (end != null && total != null && end <= total) {
                episodeStart = 1
                episodeEnd = end
                episodeTotal = total
                isAbsoluteEpisodeRange = true
                remaining = remaining.replaceRange(match.range, " ")
            }
        }
        seasonEpisode.find(remaining)?.let { match ->
            season = match.groupValues[1].toIntOrNull()
            seasonEnd = season
            episodeStart = match.groupValues[2].toIntOrNull()
            episodeEnd = match.groupValues[3].takeIf(String::isNotBlank)?.toIntOrNull()
            episodeTotal = null
            isAbsoluteEpisodeRange = false
            remaining = remaining.replaceRange(match.range, " ")
        }
        if (season == null) {
            seasonMarker.find(remaining)?.let { match ->
                season = match.groupValues[1].toIntOrNull()
                seasonEnd = season
                remaining = remaining.replaceRange(match.range, " ")
            }
        } else {
            seasonMarker.find(remaining)?.let { match -> remaining = remaining.replaceRange(match.range, " ") }
        }
        if (episodeStart == null) {
            val match = episodeRange.find(remaining) ?: episodeMarker.find(remaining)
                ?: if (season != null) rangeWithSeason.find(remaining) else null
            match?.let {
                val groups = it.groupValues
                episodeStart = groups.getOrNull(1)?.toIntOrNull()
                episodeEnd = groups.getOrNull(2)?.takeIf(String::isNotBlank)?.toIntOrNull()
                remaining = remaining.replaceRange(it.range, " ")
            } ?: if (season != null) {
                episodeCount.find(remaining)?.let {
                    episodeStart = 1
                    episodeEnd = it.groupValues[1].toIntOrNull()
                    remaining = remaining.replaceRange(it.range, " ")
                }
            } else Unit
        }

        var qualityScore = 0
        val foundQualityTags = qualityRules.mapNotNull { rule ->
            if (!rule.regex.containsMatchIn(remaining)) null else {
                qualityScore += rule.score
                remaining = rule.regex.replace(remaining, " ")
                rule.label
            }
        }.distinct()
        val qualityTags = if ("WEB-DL" in foundQualityTags) foundQualityTags - "BluRay"
            else foundQualityTags

        val audioTags = buildList {
            dualAudio.find(remaining)?.let { match ->
                add("")
                remaining = remaining.replaceRange(match.range, " ")
            }
            audioLanguageList.find(remaining)?.let { match ->
                val tracks = audioLanguageItem.findAll(match.value).map { item ->
                    val amount = item.groupValues[1].toIntOrNull()
                    val language = languageLabels[item.groupValues[2].lowercase()]
                        ?: item.groupValues[2]
                    (if (amount != null && amount > 1) "${amount}×" else "") + language
                }.joinToString("/")
                add(tracks)
                remaining = remaining.replaceRange(match.range, " ")
            }
            if (dubbedUkr.containsMatchIn(remaining)) {
                add("Ukr")
                remaining = dubbedUkr.replace(remaining, " ")
            }
        }.distinct()

        val subtitleMatches = subtitleLanguage.findAll(remaining).toList()
        val subtitleTags = buildList {
            subtitleMatches.forEach { match ->
                val label = when {
                    match.value.contains("multi", true) -> "Multi"
                    else -> {
                        val code = languageLabels.entries.firstOrNull { (long, short) ->
                            Regex("(?i)(?<![\\p{L}\\p{N}])(?:${Regex.escape(long)}|${Regex.escape(short)})(?![\\p{L}\\p{N}])")
                                .containsMatchIn(match.value)
                        }?.value ?: "?"
                        code
                    }
                }
                add(label)
            }
        }.distinct()
        subtitleMatches.asReversed().forEach { match ->
            remaining = remaining.replaceRange(match.range, " ")
        }

        val languageTags = buildList {
            if (genericUkr.containsMatchIn(remaining)) {
                add("Ukr")
                remaining = genericUkr.replace(remaining, " ")
            }
            if (japanese.containsMatchIn(remaining)) {
                add("Jap")
                remaining = japanese.replace(remaining, " ")
            }
        }.distinct()

        val seasonTags = listOfNotNull(season?.let {
            if (seasonEnd != null && seasonEnd != it) "S%02d–%02d".format(it, seasonEnd)
            else "S%02d".format(it)
        }, when {
            episodeStart != null && episodeEnd != null ->
                "E%02d–%02d".format(episodeStart, episodeEnd)
            episodeStart != null -> "E%02d".format(episodeStart)
            else -> null
        })
        val clean = remaining
            .replace(Regex("""[\[\](){}]"""), " ")
            .replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""^[\s\-–—]+|[\s\-–—]+$"""), "")
            .trim()
            .ifBlank { title.trim() }

        return TolokaReleaseMetadata(clean, qualityTags + seasonTags, audioTags, subtitleTags,
            languageTags, season, seasonEnd, episodeStart, episodeEnd, episodeTotal,
            isAbsoluteEpisodeRange, qualityScore)
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
