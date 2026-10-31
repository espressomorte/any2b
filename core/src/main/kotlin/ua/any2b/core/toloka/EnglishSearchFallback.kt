package ua.any2b.core.toloka

/** Runs at most one additional search when the default query has no usable matches. */
suspend fun <T> searchWithEnglishFallback(
    primary: String,
    english: String?,
    search: suspend (String) -> T,
    hasMatches: (T) -> Boolean,
): Pair<String, T> {
    val first = search(primary)
    val fallback = english?.trim()?.takeIf { it.isNotBlank() &&
        !it.equals(primary, ignoreCase = true) }
    if (fallback == null || hasMatches(first)) return primary to first
    return fallback to search(fallback)
}
