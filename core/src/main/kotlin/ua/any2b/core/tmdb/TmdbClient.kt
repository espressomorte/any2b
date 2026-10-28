package ua.any2b.core.tmdb

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

enum class TmdbMediaType(val path: String) { MOVIE("movie"), TV("tv") }

data class TmdbTitle(
    val id: Int,
    val type: TmdbMediaType,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?,
    val rating: Double?,
    val ukrainianTitle: String? = null,
    val englishTitle: String? = null,
    val originalLanguage: String? = null,
) {
    fun searchNames(query: String): List<String> {
        val original = originalTitle?.takeIf { originalLanguage != "en" && originalLanguage != "uk" }
        val ukrainianQuery = query.any { it in '\u0400'..'\u04FF' }
        val originalMatches = original?.contains(query.trim(), ignoreCase = true) == true
        val names = when {
            ukrainianQuery -> listOf(ukrainianTitle, englishTitle, original)
            originalMatches -> listOf(original, englishTitle, ukrainianTitle)
            else -> listOf(englishTitle, ukrainianTitle, original)
        }
        return (names + title).filterNotNull().filter(String::isNotBlank)
            .distinctBy { it.lowercase() }
    }
}

data class TmdbDetails(
    val title: TmdbTitle,
    val genres: List<String>,
    val status: String?,
    val runtimeMinutes: Int?,
    val seasons: Int?,
    val episodes: Int?,
    val collectionName: String?,
)

sealed class TmdbFailure(message: String) : IOException(message) {
    class MissingToken : TmdbFailure("TMDB токен не налаштовано для цієї збірки")
    class Unauthorized : TmdbFailure("TMDB відхилив токен доступу")
    class Http(val status: Int) : TmdbFailure("TMDB відповів HTTP $status")
    class Timeout : TmdbFailure("Час очікування TMDB вичерпано")
    class Network : TmdbFailure("Не вдалося з’єднатися з TMDB")
    class InvalidResponse : TmdbFailure("TMDB повернув неочікувані дані")
}

class TmdbClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl(),
) {
    suspend fun search(query: String, token: String): List<TmdbTitle> {
        if (query.isBlank()) return emptyList()
        val primaryLanguage = if (query.any { it in '\u0400'..'\u04FF' }) "uk-UA" else "en-US"
        val primary = searchLocale(query, token, primaryLanguage)
        val secondaryLanguage = if (primaryLanguage == "uk-UA") "en-US" else "uk-UA"
        val secondary = searchLocale(query, token, secondaryLanguage)
        val otherById = secondary.associateBy { it.type to it.id }
        return (primary + secondary.filter { item -> primary.none { it.type == item.type && it.id == item.id } })
            .map { item ->
                val other = otherById[item.type to item.id]
                if (other == null) item else item.copy(
                    ukrainianTitle = item.ukrainianTitle ?: other.ukrainianTitle,
                    englishTitle = item.englishTitle ?: other.englishTitle,
                )
            }
    }

    private suspend fun searchLocale(query: String, token: String, language: String): List<TmdbTitle> {
        val json = request(listOf("search", "multi"), token, language, "query" to query.trim())
        val results = json.optJSONArray("results") ?: throw TmdbFailure.InvalidResponse()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val type = when (item.optString("media_type")) {
                "movie" -> TmdbMediaType.MOVIE
                "tv" -> TmdbMediaType.TV
                else -> return@mapNotNull null
            }
            item.toTitle(type, language)
        }
    }

    suspend fun details(type: TmdbMediaType, id: Int, token: String, language: String = "uk-UA"): TmdbDetails {
        require(id > 0) { "Некоректний TMDB ID" }
        val json = request(listOf(type.path, id.toString()), token, language)
        val title = json.toTitle(type, language) ?: throw TmdbFailure.InvalidResponse()
        if (title.id != id) throw TmdbFailure.InvalidResponse()
        val genres = json.optJSONArray("genres")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optionalString("name") }
        }.orEmpty()
        val runtime = if (type == TmdbMediaType.MOVIE) json.optInt("runtime")
            else json.optJSONArray("episode_run_time")?.optInt(0) ?: 0
        return TmdbDetails(
            title = title,
            genres = genres,
            status = json.optionalString("status"),
            runtimeMinutes = runtime.takeIf { it > 0 },
            seasons = json.optInt("number_of_seasons").takeIf { type == TmdbMediaType.TV && it > 0 },
            episodes = json.optInt("number_of_episodes").takeIf { type == TmdbMediaType.TV && it > 0 },
            collectionName = if (type == TmdbMediaType.MOVIE)
                json.optJSONObject("belongs_to_collection")?.optionalString("name") else null,
        )
    }

    private suspend fun request(
        segments: List<String>, token: String, language: String, parameter: Pair<String, String>? = null,
    ): JSONObject {
        if (token.isBlank()) throw TmdbFailure.MissingToken()
        val url = baseUrl.newBuilder().apply {
            segments.forEach(::addPathSegment)
            addQueryParameter("language", language)
            parameter?.let { addQueryParameter(it.first, it.second) }
        }.build()
        val call = http.newCall(Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json").build())
        val response = try {
            suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                })
            }
        } catch (error: SocketTimeoutException) {
            throw TmdbFailure.Timeout()
        } catch (error: IOException) {
            throw TmdbFailure.Network()
        }
        return response.use {
            when (it.code) {
                401 -> throw TmdbFailure.Unauthorized()
                in 200..299 -> try { JSONObject(it.body?.string() ?: throw TmdbFailure.InvalidResponse()) }
                    catch (_: org.json.JSONException) { throw TmdbFailure.InvalidResponse() }
                else -> throw TmdbFailure.Http(it.code)
            }
        }
    }
}

private fun JSONObject.toTitle(type: TmdbMediaType, language: String): TmdbTitle? {
    val id = optInt("id")
    val title = optionalString(if (type == TmdbMediaType.MOVIE) "title" else "name")
        ?: optionalString(if (type == TmdbMediaType.MOVIE) "original_title" else "original_name")
        ?: return null
    if (id <= 0) return null
    val date = optionalString(if (type == TmdbMediaType.MOVIE) "release_date" else "first_air_date")
    val posterPath = optionalString("poster_path")?.takeIf { it.startsWith("/") }
    return TmdbTitle(
        id = id,
        type = type,
        title = title,
        originalTitle = optionalString(if (type == TmdbMediaType.MOVIE) "original_title" else "original_name"),
        year = date?.take(4)?.toIntOrNull(),
        posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
        overview = optionalString("overview"),
        rating = optDouble("vote_average", Double.NaN).takeIf { it.isFinite() && it > 0.0 },
        ukrainianTitle = title.takeIf { language == "uk-UA" },
        englishTitle = title.takeIf { language == "en-US" },
        originalLanguage = optionalString("original_language"),
    )
}

private fun JSONObject.optionalString(key: String): String? =
    takeIf { it.has(key) && !it.isNull(key) }?.optString(key)?.trim()?.takeIf(String::isNotBlank)
