package ua.any2b.release

import java.text.Normalizer
import java.util.Locale

/** Stateless, offline parser. All evidence positions refer to the unmodified input (UTF-16). */
object KotlinReleaseParser : ReleaseParser {
    private fun pattern(body: String) = Regex(body, RegexOption.IGNORE_CASE)
    private val brackets = Regex("\\[[^]\\r\\n]*]|\\([^()\\r\\n]*\\)")
    private val year = pattern("^((?:19|20)\\d{2})(?:\\s*[-–—]\\s*((?:19|20)\\d{2}))?$")
    private val episodePack = pattern("(?<!\\d)(\\d{1,3})(?:\\s*[-–—]\\s*(\\d{1,3}))?\\s*(?:із|з|of)\\s*(\\d{1,4})(?:\\s*(?:серій|серії|епізодів))?(?![\\p{L}\\p{N}])")
    private val seasonEpisodes = pattern("(?<![\\p{L}\\p{N}])S(\\d{1,2})[ ._-]*E(\\d{1,3})(?:\\s*[-–—]\\s*E?(\\d{1,3}))?(?!\\d)")
    private val seasons = pattern("(?<![\\p{L}\\p{N}])(?:S|сезон(?:и|ів)?[ ._-]*|seasons?[ ._-]*)(\\d{1,2})(?:\\s*[-–—]\\s*S?(\\d{1,2}))?(?![\\p{L}\\p{N}])")
    private val trailingOrdinalSeason = pattern("(?<![\\p{L}\\p{N}])(\\d{1,2})\\s+сезон(?![\\p{L}\\p{N}])")
    private val ordinalSeason = pattern("(?<![\\p{L}\\p{N}])(\\d{1,2})(?:st|nd|rd|th)[ ._-]+season(?![\\p{L}\\p{N}])")
    private val namedEpisodes = listOf(
        pattern("(?<!\\d)(\\d{1,3})\\s*[-–—]\\s*(\\d{1,3})\\s*(?:episodes?|eps?|серії)(?![\\p{L}])"),
        pattern("(?<![\\p{L}\\p{N}])(?:episodes?|ep|серії|серія|епізод)[ ._-]*(\\d{1,3})(?:[ ._-]*[-–—][ ._-]*(\\d{1,3}))?(?!\\d)"),
    )
    private val seasonEpisodeCount = pattern("(?<!\\d)(\\d{1,3})\\s*(?:episodes?|eps?|серії)(?![\\p{L}])")
    private val packRange = pattern("(?<!\\d)(\\d{1,3})[ ._-]*[-–—][ ._-]*(\\d{1,3})(?=[ ._-]*(?:mkv|mp4|avi|1080p|720p|2160p|[)\\]]|$))")
    private val knownGroups = setOf("amanogawa", "fanvoxua", "anitube", "subsplease", "erai-raws")
    private val languageCodes = mapOf(
        "ukr" to "uk", "ukrainian" to "uk", "укр" to "uk", "eng" to "en", "english" to "en",
        "jap" to "ja", "jpn" to "ja", "japanese" to "ja", "rus" to "ru", "russian" to "ru",
        "ger" to "de", "deu" to "de", "german" to "de", "dut" to "nl", "nld" to "nl", "dutch" to "nl",
        "fre" to "fr", "fra" to "fr", "french" to "fr", "pol" to "pl", "polish" to "pl",
    )
    private val language = "(?:" + languageCodes.keys.joinToString("|") { Regex.escape(it) } + ")"
    private val countedLanguage = "(?:(\\d{1,2})\\s*[xх×]\\s*)?($language)"
    private val languageItem = pattern(countedLanguage)
    private val subtitles = pattern("(?<![\\p{L}\\p{N}])(?:subs?|subtitles?)[ ._-]+$language(?:\\s*[/+,&]\\s*$language)*(?![\\p{L}\\p{N}])")
    private val audioLanguages = pattern("(?<![\\p{L}\\p{N}])$countedLanguage(?:\\s*[/+,&]\\s*$countedLanguage)*(?![\\p{L}\\p{N}])")
    private val localEpisodeBlock = pattern(
        "^(?:сезон\\s+(\\d{1,2})\\s*[,;]\\s*)?серії\\s+(\\d{1,3})(?:\\s*[-–—]\\s*(\\d{1,3}))?\\s*(?:із|з|of)\\s*(\\d{1,4}|[xх?]+)$"
    )

    override fun parse(input: ReleaseInput): ParsedRelease {
        val raw = input.rawTitle
        if (raw.length > 4096) return ParsedRelease(raw, warnings = setOf(ParseWarning.INPUT_TOO_LONG))
        if (raw.isBlank()) return ParsedRelease(raw, warnings = setOf(ParseWarning.EMPTY_TITLE))
        val removed = BooleanArray(raw.length)
        val evidence = mutableListOf<Evidence>()
        val warnings = linkedSetOf<ParseWarning>()
        val years = mutableListOf<ReleaseYears>()
        val groups = mutableListOf<String>()
        var coverage: EpisodeCoverage = EpisodeCoverage.Unknown
        fun consume(range: IntRange, id: String, value: String = raw.substring(range)) {
            range.forEach { removed[it] = true }
            evidence += Evidence(id, range, value)
        }
        fun available(range: IntRange) = range.none { removed[it] }
        fun setCoverage(value: EpisodeCoverage, match: MatchResult, id: String) {
            coverage = when {
                coverage == EpisodeCoverage.Unknown || coverage == value -> value
                coverage is EpisodeCoverage.SeasonEpisodes && value is EpisodeCoverage.SeasonPack &&
                    (coverage as EpisodeCoverage.SeasonEpisodes).season == value.start && value.start == value.end -> coverage
                coverage is EpisodeCoverage.SeasonPack && value is EpisodeCoverage.SeasonEpisodes &&
                    (coverage as EpisodeCoverage.SeasonPack).start == value.season &&
                    (coverage as EpisodeCoverage.SeasonPack).end == value.season -> value
                else -> { warnings += ParseWarning.CONFLICTING_METADATA; coverage }
            }
            consume(match.range, id)
        }
        val blocks = brackets.findAll(raw).toList()
        blocks.forEach { block ->
            year.matchEntire(block.value.drop(1).dropLast(1))?.let { match ->
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toIntOrNull() ?: start
                if (end >= start) { years += ReleaseYears(start, end); consume(block.range, "year") }
                else warnings += ParseWarning.CONFLICTING_METADATA
            }
            val inner = block.value.drop(1).dropLast(1).trim()
            localEpisodeBlock.matchEntire(inner)?.let { match ->
                val season = match.groupValues[1].toIntOrNull()
                val first = match.groupValues[2].toInt()
                val explicitEnd = match.groupValues[3].toIntOrNull()
                val total = match.groupValues[4].toIntOrNull()
                val canInfer = explicitEnd != null || input.source != ReleaseSource.GENERIC
                val start = if (explicitEnd != null) first else 1
                val end = explicitEnd ?: first
                if (start <= 0 || end < start || (total != null && end > total)) {
                    warnings += ParseWarning.INVALID_EPISODE_RANGE
                } else if (canInfer) {
                    if (explicitEnd == null) warnings += ParseWarning.ASSUMED_EPISODE_START
                    val value = if (season != null) EpisodeCoverage.SeasonEpisodes(season, start, end, total)
                        else EpisodeCoverage.AbsoluteEpisodes(start, end, total)
                    setCoverage(value, block, "localizedEpisodes")
                }
            }
            if (inner.equals("FRA Transfer", ignoreCase = true) &&
                evidence.any { it.ruleId == "year" && it.range.last < block.range.first }) {
                consume(block.range, "note", "FRA Transfer")
            }
            val names = block.value.drop(1).dropLast(1).split('&').map(String::trim)
            if (names.isNotEmpty() && names.all { it.lowercase(Locale.ROOT) in knownGroups }) {
                groups += names
                consume(block.range, "releaseGroup")
            }
        }
        seasonEpisodes.findAll(raw).filter { available(it.range) }.forEach { match ->
            val start = match.groupValues[2].toInt()
            val end = match.groupValues[3].toIntOrNull() ?: start
            if (start > 0 && end >= start) setCoverage(EpisodeCoverage.SeasonEpisodes(match.groupValues[1].toInt(), start, end), match, "seasonEpisodes")
            else warnings += ParseWarning.INVALID_EPISODE_RANGE
        }
        ordinalSeason.findAll(raw).filter { available(it.range) }.forEach { match ->
            setCoverage(EpisodeCoverage.SeasonPack(match.groupValues[1].toInt()), match, "seasonPack")
        }
        seasons.findAll(raw).filter { available(it.range) }.forEach { match ->
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: start
            if (end >= start) setCoverage(EpisodeCoverage.SeasonPack(start, end), match, "seasonPack")
            else warnings += ParseWarning.INVALID_EPISODE_RANGE
        }
        trailingOrdinalSeason.findAll(raw).filter { available(it.range) }.forEach { match ->
            setCoverage(EpisodeCoverage.SeasonPack(match.groupValues[1].toInt()), match, "seasonPack")
        }
        episodePack.findAll(raw).filter { available(it.range) }.forEach { match ->
            val first = match.groupValues[1].toInt()
            val explicitEnd = match.groupValues[2].toIntOrNull()
            val total = match.groupValues[3].toInt()
            // A count alone is not proof that all preceding episodes are present.
            if (explicitEnd == null && input.source == ReleaseSource.GENERIC) return@forEach
            val start = if (explicitEnd == null) 1 else first
            val end = explicitEnd ?: first
            if (start > 0 && end >= start && end <= total) {
                if (explicitEnd == null) warnings += ParseWarning.ASSUMED_EPISODE_START
                setCoverage(EpisodeCoverage.AbsoluteEpisodes(start, end, total), match, "absoluteEpisodes")
            } else warnings += ParseWarning.INVALID_EPISODE_RANGE
        }
        // Compatibility with episode-labelled files and season packs used by playback.
        val seasonPack = coverage as? EpisodeCoverage.SeasonPack
        if (coverage == EpisodeCoverage.Unknown || (seasonPack != null && seasonPack.start == seasonPack.end)) {
            val match = namedEpisodes.firstNotNullOfOrNull { regex -> regex.findAll(raw).firstOrNull { available(it.range) } }
                ?: if (seasonPack != null) packRange.findAll(raw).firstOrNull { available(it.range) } else null
            val countMatch = if (match == null && seasonPack != null)
                seasonEpisodeCount.findAll(raw).firstOrNull { available(it.range) } else null
            val selected = match ?: countMatch
            if (selected != null) {
                val start = if (countMatch != null) 1 else selected.groupValues[1].toInt()
                val end = if (countMatch != null) selected.groupValues[1].toInt()
                    else selected.groupValues.getOrNull(2)?.toIntOrNull() ?: start
                if (start > 0 && end >= start) {
                    val value = if (seasonPack != null) EpisodeCoverage.SeasonEpisodes(seasonPack.start, start, end)
                        else EpisodeCoverage.AbsoluteEpisodes(start, end, null)
                    setCoverage(value, selected, "labelledEpisodes")
                } else warnings += ParseWarning.INVALID_EPISODE_RANGE
            }
        }
        // A bare year after an explicit season is release metadata, unlike a number in a film title.
        if (years.isEmpty()) {
            val seasonEnd = evidence.filter { it.ruleId in setOf("seasonPack", "seasonEpisodes", "localizedEpisodes") }
                .maxOfOrNull { it.range.last }
            if (seasonEnd != null) {
                Regex("(?<![\\p{L}\\p{N}])(?:19|20)\\d{2}(?![\\p{L}\\p{N}])").findAll(raw)
                    .firstOrNull { it.range.first > seasonEnd && available(it.range) }?.let {
                        years += ReleaseYears(it.value.toInt())
                        consume(it.range, "year")
                    }
            }
        }
        val facts = mutableMapOf<String, MutableSet<String>>()
        val lastYearEnd = evidence.filter { it.ruleId == "year" }.maxOfOrNull { it.range.last }
        TechnicalRules.all.forEach { rule ->
            rule.regex.findAll(raw).filter { available(it.range) }.forEach { match ->
                // A bracketed year anchors the metadata suffix; technical words before it belong to the title.
                if (lastYearEnd != null && match.range.first < lastYearEnd) return@forEach
                // Require a preceding title or a bracketed technical block.
                if (!raw.take(match.range.first).any(Char::isLetterOrDigit) && blocks.none { match.range.first in it.range }) return@forEach
                val value = rule.value.ifEmpty { match.value }
                facts.getOrPut(rule.id) { linkedSetOf() } += value
                consume(match.range, rule.id, value)
            }
        }
        val subtitleLanguages = linkedSetOf<String>()
        val audio = linkedSetOf<String>()
        val tracks = mutableListOf<AudioLanguageTrack>()
        val technicalStart = evidence.filter { it.ruleId != "releaseGroup" }.minOfOrNull { it.range.first }
        fun languages(regex: Regex, destination: MutableSet<String>, id: String) {
            regex.findAll(raw).filter { available(it.range) }.forEach { match ->
                if (technicalStart == null || match.range.first < technicalStart) return@forEach
                languageItem.findAll(match.value).forEach { item ->
                    val code = languageCodes.getValue(item.groupValues[2].lowercase(Locale.ROOT))
                    destination += code
                    if (id == "audioLanguages") tracks += AudioLanguageTrack(code, item.groupValues[1].toIntOrNull())
                }
                consume(match.range, id)
            }
        }
        languages(subtitles, subtitleLanguages, "subtitles")
        languages(audioLanguages, audio, "audioLanguages")
        // Only remove empty wrappers after extracting facts; unknown text is preserved.
        blocks.forEach { block ->
            val inner = (block.range.first + 1 until block.range.last)
            if (inner.all { removed[it] || raw[it].isWhitespace() }) {
                removed[block.range.first] = true; removed[block.range.last] = true
            } else if (available(block.range)) warnings += ParseWarning.UNKNOWN_BRACKET
        }
        fun single(id: String): String? {
            val values = facts[id].orEmpty()
            if (values.size > 1) warnings += ParseWarning.CONFLICTING_METADATA
            return values.singleOrNull()
        }
        val candidates = mutableListOf<TitleCandidate>()
        val masked = raw.mapIndexed { index, char -> if (removed[index]) ' ' else char }.joinToString("")
        val sceneStyle = evidence.any {
            it.ruleId in setOf("seasonEpisodes", "seasonPack", "resolution", "source", "codec") &&
                it.range.first > 0 && raw[it.range.first - 1] == '.'
        }
        // Spaced title delimiters do not split Fate/stay night or language lists.
        val separators = Regex("\\s+[|/]\\s+").findAll(masked).toList()
        var from = 0
        fun addCandidate(start: Int, end: Int) {
            if (start >= end) return
            val part = masked.substring(start, end)
            val left = part.indexOfFirst { !it.isWhitespace() && it !in "|/._-" }
            val right = part.indexOfLast { !it.isWhitespace() && it !in "|/._-" }
            if (left < 0 || right < left) return
            val alias = Regex("\\(([^()]*)\\)\\s*$").find(part)
            if (alias != null) {
                val baseScript = script(part.substring(0, alias.range.first))
                val aliasScript = script(alias.groupValues[1])
                if (baseScript in setOf(ScriptHint.CYRILLIC, ScriptHint.LATIN, ScriptHint.JAPANESE) &&
                    aliasScript in setOf(ScriptHint.CYRILLIC, ScriptHint.LATIN, ScriptHint.JAPANESE) && baseScript != aliasScript) {
                    addCandidate(start, start + alias.range.first)
                    addCandidate(start + alias.groups[1]!!.range.first, start + alias.groups[1]!!.range.last + 1)
                    return
                }
            }
            val text = part.substring(left, right + 1).replace('_', ' ')
                .let { if (sceneStyle) it.replace(Regex("(?<=\\p{L})\\.(?=\\p{L})"), " ") else it }
                .replace(Regex("\\s+"), " ").trim()
            if (text.none(Char::isLetter) && text.none(Char::isDigit)) return
            candidates += TitleCandidate(text, normalize(text), (start + left)..(start + right), script(text))
        }
        separators.forEach { addCandidate(from, it.range.first); from = it.range.last + 1 }
        addCandidate(from, raw.length)
        val video = VideoMetadata(single("resolution"), single("source"), single("codec"), single("bitDepth")?.toInt(), facts["hdr"].orEmpty())
        return ParsedRelease(raw, candidates.distinctBy { it.normalizedKey }, years.distinct(), coverage,
            video, AudioMetadata(audio, facts["audioCodec"].orEmpty(), facts["channels"].orEmpty(), tracks),
            subtitleLanguages, groups.distinct(), evidence.sortedBy { it.range.first }, warnings, editions = facts["edition"].orEmpty(),
            notes = facts["note"].orEmpty() + evidence.filter { it.ruleId == "note" }.map { it.value })
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun script(value: String): ScriptHint {
        val scripts = value.filter(Char::isLetter).map { char -> when (char) {
            in '\u0400'..'\u052f' -> ScriptHint.CYRILLIC
            in 'A'..'Z', in 'a'..'z', in '\u00c0'..'\u024f' -> ScriptHint.LATIN
            in '\u3040'..'\u30ff', in '\u3400'..'\u9fff' -> ScriptHint.JAPANESE
            else -> ScriptHint.UNKNOWN
        } }.toSet()
        return scripts.singleOrNull() ?: if (scripts.isEmpty()) ScriptHint.UNKNOWN else ScriptHint.MIXED
    }
}
