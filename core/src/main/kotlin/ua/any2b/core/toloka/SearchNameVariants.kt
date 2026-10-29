package ua.any2b.core.toloka

/** Optional, broader Toloka queries. The full title is always kept separately. */
fun shortenedSearchNames(title: String): List<String> {
    val original = title.trim()
    if (original.isBlank()) return emptyList()
    val variants = linkedSetOf<String>()

    fun add(value: String) {
        val clean = value.trim().trimEnd(':', '：', '-', '–').trim()
        if (clean.length >= 3 && !clean.equals(original, ignoreCase = true)) variants += clean
    }

    var withoutPart = original
    while (true) {
        val shorter = withoutPart.replace(trailingPart, "").trim()
        if (shorter == withoutPart) break
        withoutPart = shorter
        add(shorter)
    }
    for (value in listOf(original, withoutPart)) {
        val separator = value.indexOfAny(charArrayOf(':', '：'))
        if (separator > 0 && separator < value.lastIndex) {
            val base = value.substring(0, separator).trim()
            add(base)
            add(base.replace(trailingPart, ""))
        }
    }
    return variants.toList()
}

private val trailingPart = Regex(
    """\s*\(\s*(?:(?:сезон|частина|season|part)\s*(?:\d{1,3}|[IVX]{1,5})|(?:\d{1,3}|[IVX]{1,5})\s*(?:сезон|частина|season|part))\s*\)\s*$""",
    RegexOption.IGNORE_CASE,
)
