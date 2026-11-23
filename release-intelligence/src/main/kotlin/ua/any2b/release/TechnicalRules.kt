package ua.any2b.release

/** Technical coverage informed by the pinned PTT/RTN audit; see RULES.md for provenance.
 * Patterns are JVM rules with Unicode token boundaries and original-input evidence.
 */
internal object TechnicalRules {
    data class Rule(val id: String, val regex: Regex, val value: String)
    private fun rule(id: String, body: String, value: String) = Rule(
        id, Regex("(?<![\\p{L}\\p{N}])(?:$body)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE), value,
    )
    val all = buildList {
        listOf("4320p", "2160p", "1440p", "1080p", "1080i", "720p", "576p", "576i", "480p", "480i", "360p", "240p").forEach {
            add(rule("resolution", it, it))
        }
        add(rule("resolution", "4k|uhd", "2160p"))
        add(rule("resolution", "8k", "4320p"))
        add(rule("source", "web[ ._-]*dl[ ._-]*rip", "WEBDLRip"))
        add(rule("source", "web[ ._-]*dl", "WEB-DL"))
        add(rule("source", "web[ ._-]*rip", "WEBRip"))
        add(rule("source", "dvd[ ._-]*remux", "DVDRemux"))
        add(rule("source", "bd[ ._-]*remux|blu[ ._-]*ray", "BluRay"))
        add(rule("source", "bd[ ._-]*rip", "BDRip"))
        add(rule("source", "dvd[ ._-]*rip", "DVDRip"))
        add(rule("source", "hdtv", "HDTV"))
        add(rule("codec", "hevc|x265|h[ ._-]?265", "H.265"))
        add(rule("codec", "avc|x264|h[ ._-]?264", "H.264"))
        add(rule("codec", "av1", "AV1"))
        add(rule("bitDepth", "10[ ._-]*bit", "10"))
        add(rule("hdr", "hdr10\\+", "HDR10+"))
        add(rule("hdr", "hdr10", "HDR10"))
        add(rule("hdr", "hdr", "HDR"))
        add(rule("hdr", "dolby[ ._-]*vision|dv(?:[ ._-]*p[ ._-]*[578])?", "DV"))
        add(rule("edition", "theatrical(?:[ ._-]+cut)?", "Theatrical Cut"))
        add(rule("note", "ai[ ._-]+rem", "AI Remaster"))
        add(rule("audioCodec", "e[ ._-]?ac[ ._-]?3|ddp", "E-AC-3"))
        add(rule("audioCodec", "ac[ ._-]?3", "AC-3"))
        add(rule("audioCodec", "dts[ ._-]*hd(?:[ ._-]*ma)?", "DTS-HD"))
        add(rule("audioCodec", "dts", "DTS"))
        listOf("AAC", "FLAC", "TrueHD", "Opus").forEach { add(rule("audioCodec", it, it)) }
        add(rule("channels", "2\\.0|5\\.1|7\\.1", ""))
        add(rule("container", "mkv|mp4|avi", ""))
    }
}
