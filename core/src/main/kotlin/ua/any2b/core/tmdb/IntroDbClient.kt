package ua.any2b.core.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IntroSegment(val startMs: Long, val endMs: Long)
data class IntroDbSegments(val intros: List<IntroSegment>, val credits: List<IntroSegment>)

/** Minimal read-only client for TheIntroDB v4 skip timestamps. */
class IntroDbClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun findSegments(
        tmdbId: Int,
        type: TmdbMediaType,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null,
    ): IntroDbSegments = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || durationMs <= 0L ||
            (type == TmdbMediaType.TV && (season == null || episode == null))) {
            return@withContext IntroDbSegments(emptyList(), emptyList())
        }

        val url = "https://api.theintrodb.org/v4/media".toHttpUrl().newBuilder()
            .addQueryParameter("tmdb_id", tmdbId.toString())
            .addQueryParameter("duration_ms", durationMs.toString())
            .apply {
                if (type == TmdbMediaType.TV) {
                    addQueryParameter("season", season.toString())
                    addQueryParameter("episode", episode.toString())
                }
            }
            .build()
        val request = Request.Builder().url(url).get().build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use IntroDbSegments(emptyList(), emptyList())
                val body = response.body?.string().orEmpty()
                val result = JSONObject(body)
                IntroDbSegments(
                    parseSegments(result, "intro", durationMs),
                    parseSegments(result, "credits", durationMs),
                )
            }
        }.getOrDefault(IntroDbSegments(emptyList(), emptyList()))
    }

    private fun parseSegments(body: JSONObject, key: String, durationMs: Long): List<IntroSegment> {
        val segments = body.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONObject(index) ?: continue
                val start = segment.optLong("start_ms", 0L).coerceAtLeast(0L)
                val endValue = segment.opt("end_ms")
                val end = when (endValue) {
                    null, JSONObject.NULL -> durationMs
                    else -> (endValue as? Number)?.toLong() ?: continue
                }.coerceAtMost(durationMs)
                if (end > start) add(IntroSegment(start, end))
            }
        }
    }
}
