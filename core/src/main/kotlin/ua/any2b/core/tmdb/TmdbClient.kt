package ua.any2b.core.tmdb

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import java.net.URLEncoder

enum class TmdbMediaType(val path: String) { MOVIE("movie"), TV("tv") }

enum class TmdbListSort { POPULARITY, RATING, YEAR }

data class TmdbPage(val titles: List<TmdbTitle>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

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
    val genreIds: Set<Int> = emptySet(),
    val originCountries: Set<String> = emptySet(),
    val matchedSeasonTitle: String? = null,
    val matchedSeasonYear: Int? = null,
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
    val seasonEntries: List<TmdbSeason> = emptyList(),
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val countryCodes: List<String> = emptyList(),
    val alternativeTitles: List<String> = emptyList(),
)

fun TmdbDetails.trailerSearchUrl(): String {
    val query = listOfNotNull(title.originalTitle ?: title.title,
        title.year?.toString(), "trailer").joinToString(" ")
    return "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
}

data class TmdbSeason(val number: Int, val name: String?, val year: Int?, val episodeCount: Int? = null)

sealed class TmdbFailure(message: String) : IOException(message) {
    class MissingToken : TmdbFailure("Ключ доступу не налаштовано")
    class Unauthorized : TmdbFailure("Worker відхилив ключ доступу")
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
    suspend fun popular(type: TmdbMediaType, token: String, genreId: Int? = null): List<TmdbTitle> =
        listPage(type, token, TmdbListSort.POPULARITY, 1, genreId).titles

    suspend fun listPage(type: TmdbMediaType, token: String, sort: TmdbListSort, page: Int,
        genreId: Int? = null, yearRange: IntRange? = null,
        yearAscending: Boolean = false): TmdbPage {
        require(page in 1..500)
        require(genreId == null || genreId > 0)
        val currentDate = java.util.Calendar.getInstance()
        val currentYear = currentDate.get(java.util.Calendar.YEAR)
        require(yearRange == null || yearRange.first in 1920..currentYear &&
            yearRange.last in yearRange.first..currentYear)
        val years = yearRange ?: if (sort == TmdbListSort.YEAR) 1920..currentYear else null
        val json = if (sort == TmdbListSort.POPULARITY && genreId == null && years == null) {
            request(listOf(type.path, "popular"), token, "uk-UA", "page" to page.toString())
        } else {
            val sortBy = when (sort) {
                TmdbListSort.RATING -> "vote_average.desc"
                TmdbListSort.YEAR -> (if (type == TmdbMediaType.MOVIE)
                    "primary_release_date" else "first_air_date") +
                    if (yearAscending) ".asc" else ".desc"
                TmdbListSort.POPULARITY -> "popularity.desc"
            }
            val parameters = buildList {
                add("page" to page.toString())
                add("sort_by" to sortBy)
                if (sort == TmdbListSort.RATING) add("vote_count.gte" to "100")
                genreId?.let { add("with_genres" to it.toString()) }
                if (genreId != null && type == TmdbMediaType.MOVIE)
                    add("with_runtime.gte" to "60")
                years?.let { range ->
                    val dateField = if (type == TmdbMediaType.MOVIE)
                        "primary_release_date" else "first_air_date"
                    add("$dateField.gte" to "${range.first}-01-01")
                    val upperDate = if (range.last == currentYear)
                        "%04d-%02d-%02d".format(java.util.Locale.ROOT, currentYear,
                            currentDate.get(java.util.Calendar.MONTH) + 1,
                            currentDate.get(java.util.Calendar.DAY_OF_MONTH))
                    else "${range.last}-12-31"
                    add("$dateField.lte" to upperDate)
                }
            }
            request(listOf("discover", type.path), token, "uk-UA", *parameters.toTypedArray())
        }
        return TmdbPage(parseList(json, type, "uk-UA")
            .filter { genreId == null || genreId in it.genreIds }, page,
            json.optInt("total_pages", page).coerceIn(page, 500))
    }

    suspend fun related(type: TmdbMediaType, id: Int, token: String): List<TmdbTitle> {
        require(id > 0)
        val json = request(listOf(type.path, id.toString(), "recommendations"),
            token, "uk-UA", "page" to "1")
        return parseList(json, type, "uk-UA").filterNot { it.id == id }
    }

    private fun parseList(json: JSONObject, type: TmdbMediaType, language: String): List<TmdbTitle> {
        val array = json.optJSONArray("results") ?: throw TmdbFailure.InvalidResponse()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.takeUnless { it.optBoolean("adult") }
                ?.toTitle(type, language)
        }
    }
    suspend fun search(query: String, token: String): List<TmdbTitle> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val primaryLanguage = if (query.any { it in '\u0400'..'\u04FF' }) "uk-UA" else "en-US"
        val secondaryLanguage = if (primaryLanguage == "uk-UA") "en-US" else "uk-UA"
        val primaryRequest = async { searchLocale(query, token, primaryLanguage) }
        val secondaryRequest = async { searchLocale(query, token, secondaryLanguage) }
        val primary = primaryRequest.await()
        val secondary = secondaryRequest.await()
        val otherById = secondary.associateBy { it.type to it.id }
        (primary + secondary.filter { item -> primary.none { it.type == item.type && it.id == item.id } })
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
        val credits = json.optJSONObject("credits")
        val directors = credits?.optJSONArray("crew")?.let { crew ->
            (0 until crew.length()).mapNotNull { index ->
                crew.optJSONObject(index)?.takeIf { it.optString("job") == "Director" }
                    ?.optionalString("name")
            }.distinct()
        }.orEmpty()
        val cast = credits?.optJSONArray("cast")?.let { actors ->
            (0 until actors.length()).mapNotNull { index ->
                actors.optJSONObject(index)?.optionalString("name")
            }.distinct()
        }.orEmpty()
        val countryCodes = if (type == TmdbMediaType.MOVIE) {
            json.optJSONArray("production_countries")?.let { countries ->
                (0 until countries.length()).mapNotNull { index ->
                    countries.optJSONObject(index)?.optionalString("iso_3166_1")
                }
            }.orEmpty()
        } else {
            json.optJSONArray("origin_country")?.let { countries ->
                (0 until countries.length()).mapNotNull { index ->
                    countries.optString(index).takeIf(String::isNotBlank)
                }
            }.orEmpty()
        }.distinct()
        val alternativeTitles = json.optJSONObject("alternative_titles")?.let { alternatives ->
            val entries = alternatives.optJSONArray("titles") ?: alternatives.optJSONArray("results")
            entries?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { item ->
                        item.optionalString("title") ?: item.optionalString("name")
                    }
                }
            }
        }.orEmpty().distinctBy { it.lowercase() }
        return TmdbDetails(
            title = title,
            genres = genres,
            status = json.optionalString("status"),
            runtimeMinutes = runtime.takeIf { it > 0 },
            seasons = json.optInt("number_of_seasons").takeIf { type == TmdbMediaType.TV && it > 0 },
            episodes = json.optInt("number_of_episodes").takeIf { type == TmdbMediaType.TV && it > 0 },
            collectionName = if (type == TmdbMediaType.MOVIE)
                json.optJSONObject("belongs_to_collection")?.optionalString("name") else null,
            seasonEntries = if (type == TmdbMediaType.TV) json.optJSONArray("seasons")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val season = array.optJSONObject(index) ?: return@mapNotNull null
                    TmdbSeason(season.optInt("season_number"), season.optionalString("name"),
                        season.optionalString("air_date")?.take(4)?.toIntOrNull(),
                        season.optInt("episode_count").takeIf { it > 0 })
                }
            }.orEmpty() else emptyList(),
            directors = directors,
            cast = cast,
            countryCodes = countryCodes,
            alternativeTitles = alternativeTitles,
        )
    }

    private suspend fun request(
        segments: List<String>, token: String, language: String, vararg parameters: Pair<String, String>,
    ): JSONObject {
        if (token.isBlank()) throw TmdbFailure.MissingToken()
        val url = baseUrl.newBuilder().apply {
            segments.forEach(::addPathSegment)
            addQueryParameter("language", language)
            parameters.forEach { addQueryParameter(it.first, it.second) }
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
        genreIds = optJSONArray("genre_ids")?.let { array ->
            (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }.toSet()
        }.orEmpty(),
        originCountries = optJSONArray("origin_country")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.orEmpty(),
    )
}

private fun JSONObject.optionalString(key: String): String? =
    takeIf { it.has(key) && !it.isNull(key) }?.optString(key)?.trim()?.takeIf(String::isNotBlank)
