package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import java.io.File

/** Last successful home rows. Expired rows remain visible while a network refresh runs. */
class HomeRowCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, "home-rows.json")

    fun read(key: String): List<TmdbTitle> = synchronized(lock) {
        runCatching {
            val rows = JSONObject(file.readText()).optJSONArray(key) ?: return@synchronized emptyList()
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.let(::decode) }
        }.getOrDefault(emptyList())
    }

    fun save(key: String, titles: List<TmdbTitle>) = synchronized(lock) {
        if (titles.isEmpty()) return@synchronized
        val root = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
        root.put(key, JSONArray().apply { titles.forEach { put(encode(it)) } })
        root.put("${key}_updated_at", System.currentTimeMillis())
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun encode(title: TmdbTitle) = JSONObject()
        .put("id", title.id).put("type", title.type.path).put("title", title.title)
        .put("originalTitle", title.originalTitle).put("year", title.year)
        .put("posterUrl", title.posterUrl).put("backdropUrl", title.backdropUrl)
        .put("overview", title.overview).put("rating", title.rating)
        .put("ukrainianTitle", title.ukrainianTitle)
        .put("englishTitle", title.englishTitle)
        .put("originalLanguage", title.originalLanguage)
        .put("genreIds", JSONArray(title.genreIds.toList()))
        .put("originCountries", JSONArray(title.originCountries.toList()))
        .put("matchedSeasonTitle", title.matchedSeasonTitle)
        .put("matchedSeasonYear", title.matchedSeasonYear)
        .put("matchedSeasonNumber", title.matchedSeasonNumber)
        .put("popularity", title.popularity).put("airDate", title.airDate)

    private fun decode(row: JSONObject): TmdbTitle? {
        val id = row.optInt("id").takeIf { it > 0 } ?: return null
        val type = TmdbMediaType.entries.firstOrNull { it.path == row.optString("type") }
            ?: return null
        val title = row.optString("title").takeIf(String::isNotBlank) ?: return null
        fun optional(key: String) = row.optString(key).takeIf {
            it.isNotBlank() && it != "null"
        }
        fun ids(key: String) = row.optJSONArray(key)?.let { array ->
            (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }.toSet()
        }.orEmpty()
        val countries = row.optJSONArray("originCountries")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
                .filter(String::isNotBlank).toSet()
        }.orEmpty()
        return TmdbTitle(id, type, title, optional("originalTitle"),
            row.optInt("year").takeIf { it > 0 }, optional("posterUrl"),
            optional("overview"), row.optDouble("rating", Double.NaN)
                .takeIf(Double::isFinite),
            ukrainianTitle = optional("ukrainianTitle"),
            englishTitle = optional("englishTitle"),
            originalLanguage = optional("originalLanguage"),
            genreIds = ids("genreIds"), originCountries = countries,
            matchedSeasonTitle = optional("matchedSeasonTitle"),
            matchedSeasonYear = row.optInt("matchedSeasonYear").takeIf { it > 0 },
            matchedSeasonNumber = row.optInt("matchedSeasonNumber")
                .takeIf { row.has("matchedSeasonNumber") && !row.isNull("matchedSeasonNumber") && it >= 0 },
            backdropUrl = optional("backdropUrl"),
            popularity = row.optDouble("popularity", Double.NaN).takeIf(Double::isFinite),
            airDate = optional("airDate"))
    }

    private companion object { val lock = Any() }
}
