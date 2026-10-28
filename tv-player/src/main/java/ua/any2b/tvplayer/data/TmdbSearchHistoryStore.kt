package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray

/** Last selected TMDB title names, ordered newest first. No IDs or credentials are stored. */
class TmdbSearchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "tmdb_search_history", Context.MODE_PRIVATE)

    fun recentTitles(): List<String> = try {
        val array = JSONArray(prefs.getString("titles", "[]"))
        (0 until minOf(array.length(), 10)).mapNotNull { index ->
            array.optString(index).trim().takeIf(String::isNotBlank)
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun recordSelectedTitle(title: String): List<String> {
        val clean = title.withoutCollectionSuffix().withoutYearOrSeason()
            .takeIf(String::isNotBlank) ?: return recentTitles()
        val updated = (listOf(clean) + recentTitles().filterNot {
            it.equals(clean, ignoreCase = true)
        }).take(10)
        prefs.edit().putString("titles", JSONArray(updated).toString()).apply()
        return updated
    }

    fun clear() {
        prefs.edit().remove("titles").apply()
    }
}

private val trailingYearOrSeason = Regex(
    """\s*(?:\((?:19|20)\d{2}\)|(?:Season|Сезон)\s*\d+|\d+\s*(?:season|сезон)|(?:The\s+)?Final\s+Season|Фінальний\s+сезон)\s*$""",
    RegexOption.IGNORE_CASE,
)

private val trailingCollection = Regex(
    """\s*(?:\|\s*)?(?:Колекція|Collection)\s*$""", RegexOption.IGNORE_CASE)

private fun String.withoutCollectionSuffix(): String =
    replace(trailingCollection, "").trimEnd(' ', ':', '-', '–', '—')

private fun String.withoutYearOrSeason(): String {
    var value = trim()
    while (true) {
        val shortened = value.replace(trailingYearOrSeason, "").trimEnd(' ', ':', '-', '–', '—')
        if (shortened == value || shortened.isBlank()) return value
        value = shortened
    }
}
