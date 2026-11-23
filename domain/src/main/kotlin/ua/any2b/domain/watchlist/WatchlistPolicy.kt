package ua.any2b.domain.watchlist

object WatchlistPolicy {
    fun available(number: Int, aired: Boolean, requiresPlaylist: Boolean, playable: Set<Int>?): Boolean =
        if (requiresPlaylist) playable?.contains(number) == true else aired

    fun seasonComplete(finishedInTmdb: Boolean, expected: Set<Int>, playable: Set<Int>?, watched: Set<Int>): Boolean =
        finishedInTmdb && expected.isNotEmpty() && playable != null &&
            playable.containsAll(expected) && watched.containsAll(expected)

    fun savedFileForEpisode(savedFile: Int?, requestedEpisode: Int, matchedFile: Int?): Int? =
        savedFile.takeIf { requestedEpisode <= 0 || it == matchedFile }

    fun episodeSubtitle(name: String): String? {
        val text = name.trim()
        if (text.isBlank()) return null
        val value = text.lowercase().replace(Regex("[.№#:_-]+"), " ").replace(Regex("\\s+"), " ").trim()
        val ordinal = "(?:перша|друга|третя|четверта|п.?ята|шоста|сьома|восьма|дев.?ята|десята|одинадцята|дванадцята|тринадцята|чотирнадцята|п.?ятнадцята|шістнадцята|сімнадцята|вісімнадцята|дев.?ятнадцята|двадцята|first|second|third|fourth|fifth)"
        val generic = Regex("(?:e\\s*\\d+|\\d+|(?:episode|ep|серія|серии|серия|епізод)\\s*\\d+|(?:\\d+|$ordinal)\\s*(?:серія|серия|епізод|episode)|(?:серія|серия|епізод|episode)\\s*$ordinal)")
        return text.takeUnless { generic.matches(value) }
    }
}
