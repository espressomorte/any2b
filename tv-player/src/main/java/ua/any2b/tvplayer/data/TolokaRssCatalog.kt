package ua.any2b.tvplayer.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import ua.any2b.core.tmdb.TmdbTitle
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

data class TolokaRssRelease(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val category: String,
    val publishedAt: Long,
    val animated: Boolean,
    val tmdbId: Int? = null,
    val tmdbType: String? = null,
    val tmdbRating: Double? = null,
    val tmdbPopularity: Double? = null,
    val tmdbAirDate: String? = null,
    val tmdbName: String? = null,
    val tmdbPoster: String? = null,
)

/** Rolling on-device catalog of Toloka topics from the last two calendar months. */
class TolokaRssCatalog(private val context: Context) {
    private val file get() = File(context.filesDir, "toloka-rss-catalog.json")

    fun cached(): List<TolokaRssRelease> = runCatching {
        val rows = JSONObject(file.readText()).getJSONArray("items")
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val url = row.getString("url")
            val id = row.optString("toloka_id").ifBlank { topicId(url).orEmpty() }
                .takeIf(String::isNotBlank) ?: return@mapNotNull null
            val publishedAt = row.optString("published_at").takeIf(String::isNotBlank)
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: row.optLong("publishedAt")
            TolokaRssRelease(id, row.getString("title"), url,
                row.optString("thumbnail").takeIf { it.isNotBlank() },
                row.optString("category"), publishedAt,
                row.optBoolean("animated"),
                row.optInt("tmdb_id").takeIf { it > 0 },
                row.optString("tmdb_type").takeIf(String::isNotBlank),
                row.optJSONObject("tmdb_meta")?.optDouble("vote_average", Double.NaN)
                    ?.takeIf(Double::isFinite),
                row.optJSONObject("tmdb_meta")?.optDouble("popularity", Double.NaN)
                    ?.takeIf(Double::isFinite),
                row.optJSONObject("tmdb_meta")?.optString("first_air_date")
                    ?.takeIf(String::isNotBlank),
                row.optJSONObject("tmdb_meta")?.optString("title")
                    ?.takeIf(String::isNotBlank),
                row.optJSONObject("tmdb_meta")?.optString("poster_url")
                    ?.takeIf(String::isNotBlank))
        }.filter { it.publishedAt >= cutoff() }.distinctBy { it.id }
            .sortedByDescending { it.publishedAt }
    }.getOrDefault(emptyList())

    suspend fun refresh(): List<TolokaRssRelease> = mutex.withLock {
        val existing = cached()
        val age = System.currentTimeMillis() - runCatching {
            JSONObject(file.readText()).optLong("fetchedAt")
        }.getOrDefault(0L)
        val schema = runCatching { JSONObject(file.readText()).optInt("schema") }.getOrDefault(0)
        if (existing.isNotEmpty() && schema >= 4 && age in 0 until 3_600_000L)
            return@withLock existing
        try {
            val fresh = withContext(Dispatchers.IO) {
                parse(TolokaConnections.rss(context))
            }
            if (fresh.isEmpty()) {
                if (existing.isNotEmpty()) return@withLock existing
                throw IllegalStateException("RSS Toloka не містить доступних роздач")
            }
            val previousById = existing.associateBy { it.id }
            val items = (fresh.map { item ->
                val old = previousById[item.id]
                item.copy(tmdbId = old?.tmdbId, tmdbType = old?.tmdbType,
                    tmdbRating = old?.tmdbRating,
                    tmdbPopularity = old?.tmdbPopularity,
                    tmdbAirDate = old?.tmdbAirDate, tmdbName = old?.tmdbName,
                    tmdbPoster = old?.tmdbPoster)
            } + existing).filter { it.publishedAt >= cutoff() }
                .distinctBy { it.id }.sortedByDescending { it.publishedAt }
            withContext(Dispatchers.IO) { persist(items, System.currentTimeMillis()) }
            items
        } catch (error: Exception) {
            if (existing.isNotEmpty()) existing else throw error
        }
    }

    suspend fun attachMappings(titles: Map<String, TmdbTitle>) = mutex.withLock {
        if (titles.isEmpty()) return@withLock
        val updated = cached().map { item ->
            val title = titles[item.id] ?: return@map item
            item.copy(tmdbId = title.id, tmdbType = title.type.path,
                tmdbRating = title.rating, tmdbPopularity = title.popularity,
                tmdbAirDate = title.airDate, tmdbName = title.title,
                tmdbPoster = title.posterUrl)
        }
        val fetchedAt = runCatching {
            JSONObject(file.readText()).optLong("fetchedAt")
        }.getOrDefault(0L)
        withContext(Dispatchers.IO) { persist(updated, fetchedAt) }
    }

    private fun persist(items: List<TolokaRssRelease>, fetchedAt: Long) {
        val rows = JSONArray()
        items.forEach { item -> rows.put(JSONObject()
            .put("toloka_id", item.id).put("title", item.title).put("url", item.url)
            .put("thumbnail", item.thumbnail.orEmpty()).put("category", item.category)
            .put("published_at", Instant.ofEpochMilli(item.publishedAt).toString())
            .put("animated", item.animated).put("tmdb_id", item.tmdbId)
            .put("tmdb_type", item.tmdbType).put("tmdb_meta", JSONObject()
                .put("vote_average", item.tmdbRating)
                .put("popularity", item.tmdbPopularity)
                .put("first_air_date", item.tmdbAirDate)
                .put("title", item.tmdbName).put("poster_url", item.tmdbPoster))) }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(JSONObject().put("schema", 4).put("fetchedAt", fetchedAt)
            .put("items", rows).toString())
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun parse(xml: String): List<TolokaRssRelease> {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val items = mutableListOf<TolokaRssRelease>()
        var inside = false
        var title = ""
        var url = ""
        var category = ""
        var subject = ""
        var description = ""
        var image: String? = null
        var date = 0L
        var field: String? = null
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    if (name == "item" || name == "entry") {
                        inside = true; title = ""; url = ""; category = ""; subject = ""
                        description = ""; image = null; date = 0L
                    } else if (inside) {
                        field = name
                        if (name == "thumbnail" || name == "content" || name == "img")
                            image = (parser.getAttributeValue(null, "url")
                                ?: parser.getAttributeValue(null, "src")) ?: image
                        if (name == "link" && parser.getAttributeValue(null, "href") != null)
                            url = parser.getAttributeValue(null, "href")
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inside) {
                    val value = parser.text.orEmpty()
                    when (field) {
                        "title" -> title += value
                        "link" -> if (url.isBlank()) url += value
                        "subject" -> subject += value
                        "category" -> category += value
                        "description", "summary" -> description += value
                        "pubdate", "published" -> date = runCatching {
                            dateFormat.parse(value.trim())?.time ?: 0L
                        }.getOrDefault(0L)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                    if (name == "item" || name == "entry") {
                        val safeUrl = url.trim().takeIf {
                            it.startsWith("https://toloka.to/") || it.startsWith("http://toloka.to/")
                        }
                        val id = safeUrl?.let(::topicId)
                        if (title.isNotBlank() && safeUrl != null && id != null) {
                            val section = subject.trim().ifBlank { category.trim() }
                            val animated = ANIMATION_HINT.containsMatchIn(section) ||
                                ANIMATION_HINT.containsMatchIn(title)
                            val thumb = image ?: IMAGE_SRC.find(description)?.groupValues?.get(1)
                            val normalizedThumb = thumb?.let {
                                if (it.startsWith("//")) "https:$it" else it
                            }
                            items += TolokaRssRelease(id, title.trim(), safeUrl,
                                normalizedThumb?.takeIf {
                                    it.startsWith("https://") || it.startsWith("http://")
                                }, section, date, animated)
                        }
                        inside = false
                    }
                    field = null
                }
            }
            parser.next()
        }
        return items.filter { it.publishedAt >= cutoff() }
            .distinctBy { it.id }.sortedByDescending { it.publishedAt }
    }

    private fun topicId(url: String): String? = TOPIC_ID.find(url)?.groupValues?.get(1)
        ?: TOPIC_QUERY.find(url)?.groupValues?.get(1)

    private fun cutoff() = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(2)
        .toInstant().toEpochMilli()

    companion object {
        private val mutex = Mutex()
        private val ANIMATION_HINT = Regex(
            "аніме|анімован|анімаці|мульт|anime|animation|cartoon",
            RegexOption.IGNORE_CASE)
        private val IMAGE_SRC = Regex("""<img[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE)
        private val TOPIC_ID = Regex("/t([0-9]+)(?:-|[/?#]|$)")
        private val TOPIC_QUERY = Regex("[?&]t=([0-9]+)(?:&|$)")
    }
}
