package ua.any2b.release

enum class ReleaseSource { GENERIC, TOLOKA, ANITUBE }
data class ReleaseInput(val rawTitle: String, val source: ReleaseSource = ReleaseSource.GENERIC)
enum class ScriptHint { CYRILLIC, LATIN, JAPANESE, MIXED, UNKNOWN }
data class TitleCandidate(val text: String, val normalizedKey: String, val range: IntRange, val script: ScriptHint)
data class Evidence(val ruleId: String, val range: IntRange, val value: String)
enum class ParseWarning { EMPTY_TITLE, INPUT_TOO_LONG, INVALID_EPISODE_RANGE, CONFLICTING_METADATA, UNKNOWN_BRACKET, ASSUMED_EPISODE_START }
data class ReleaseYears(val start: Int, val end: Int = start)
sealed interface EpisodeCoverage {
    data object Unknown : EpisodeCoverage
    data class SeasonEpisodes(val season: Int, val start: Int, val end: Int = start, val total: Int? = null) : EpisodeCoverage
    data class AbsoluteEpisodes(val start: Int, val end: Int, val total: Int?) : EpisodeCoverage
    data class SeasonPack(val start: Int, val end: Int = start) : EpisodeCoverage
}
data class VideoMetadata(
    val resolution: String? = null,
    val source: String? = null,
    val codec: String? = null,
    val bitDepth: Int? = null,
    val hdr: Set<String> = emptySet(),
)
data class AudioLanguageTrack(val language: String, val count: Int? = null)
data class AudioMetadata(val languages: Set<String> = emptySet(), val codecs: Set<String> = emptySet(), val channels: Set<String> = emptySet(), val tracks: List<AudioLanguageTrack> = emptyList())
data class ParsedRelease(
    val rawTitle: String,
    val titleCandidates: List<TitleCandidate> = emptyList(),
    val years: List<ReleaseYears> = emptyList(),
    val episodeCoverage: EpisodeCoverage = EpisodeCoverage.Unknown,
    val video: VideoMetadata = VideoMetadata(),
    val audio: AudioMetadata = AudioMetadata(),
    val subtitleLanguages: Set<String> = emptySet(),
    val releaseGroups: List<String> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val warnings: Set<ParseWarning> = emptySet(),
    val parserVersion: Int = 1,
    val rulesVersion: Int = 2,
    val editions: Set<String> = emptySet(),
    val notes: Set<String> = emptySet(),
)
interface ReleaseParser {
    fun parse(input: ReleaseInput): ParsedRelease
}
