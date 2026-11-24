package ua.any2b.tvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import java.util.concurrent.TimeUnit

/** Public MDBList JSON feed for the official Popular Movies list. */
class MdblistPopularMoviesCatalog {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): List<TmdbTitle> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(FEED_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "Any2B Android TV")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MDBList відповів HTTP ${response.code}")
            val body = response.body?.string() ?: error("MDBList повернув порожню відповідь")
            val items = JSONArray(body)
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                if (item.optString("mediatype", "movie") !in setOf("movie", "film"))
                    return@mapNotNull null
                val id = item.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
                val title = item.optString("title").trim().takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                TmdbTitle(
                    id = id,
                    type = TmdbMediaType.MOVIE,
                    title = title,
                    originalTitle = null,
                    year = item.optInt("release_year").takeIf { it in 1870..2200 },
                    posterUrl = null,
                    overview = null,
                    rating = null,
                    englishTitle = title,
                )
            }.distinctBy { it.id }.take(MAX_ITEMS)
        }
    }

    private companion object {
        const val FEED_URL = "https://mdblist.com/lists/official/movies/popular/json"
        const val MAX_ITEMS = 100
    }
}
