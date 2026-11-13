package ua.any2b.core.tmdb

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
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
import org.json.JSONArray
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
    val matchedSeasonNumber: Int? = null,
    val backdropUrl: String? = null,
    val popularity: Double? = null,
    val airDate: String? = null,
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
    val tvdbId: Int? = null,
    val logoUrl: String? = null,
    val collectionId: Int? = null,
)

fun TmdbDetails.trailerSearchUrl(): String {
    val query = listOfNotNull(title.originalTitle ?: title.title,
        title.year?.toString(), "trailer").joinToString(" ")
    return "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
}

data class TmdbSeason(val number: Int, val name: String?, val year: Int?, val episodeCount: Int? = null)

data class TmdbEpisode(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String?,
    val stillUrl: String?,
    val airDate: String?,
)

data class TmdbSeasonDetails(val season: TmdbSeason, val episodes: List<TmdbEpisode>)

data class TmdbTrailer(val videoId: String, val name: String, val language: String?) {
    val youtubeUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

/** TMDB labels alone are not enough: a mislabeled interview can have type Trailer. */
object TmdbTrailerSelector {
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val trailerName = Regex("(?i)\\btrailer\\b|трейлер|予告")
    private val unrelated = Regex(
        "(?i)interview|fan[ -]?meet|meet[ -]?(?:the[ -]?)?fans|presentation|panel|" +
            "premiere|press[ -]conference|behind[ -]the[ -]scenes|featurette|reaction|q\\s*&\\s*a")

    fun select(videos: JSONArray): List<TmdbTrailer> = (0 until videos.length())
        .mapNotNull { videos.optJSONObject(it) }
        .filter { video ->
            video.optString("site").equals("YouTube", ignoreCase = true) &&
                video.optString("type").equals("Trailer", ignoreCase = true) &&
                video.optBoolean("official") && video.optString("key").matches(videoId) &&
                trailerName.containsMatchIn(video.optString("name")) &&
                !unrelated.containsMatchIn(video.optString("name"))
        }
        .sortedWith(compareBy<JSONObject> {
            when (it.optString("iso_639_1")) { "uk" -> 0; "en" -> 1; else -> 2 }
        }.thenBy { !it.optString("name").equals("Official Trailer", ignoreCase = true) }
            .thenBy { Regex("#\\s*\\d+|(?i)\\bpart\\s+\\d+").containsMatchIn(it.optString("name")) }
            .thenByDescending { it.optString("published_at") })
        .map { TmdbTrailer(it.optString("key"), it.optString("name"),
            it.optionalString("iso_639_1")) }
        .distinctBy(TmdbTrailer::videoId)
}

sealed class TmdbFailure(message: String) : IOException(message) {
    class MissingToken : TmdbFailure("Ключ доступу не налаштовано")
    class Unauthorized : TmdbFailure("TMDB відхилив токен доступу")
    class Http(val status: Int) : TmdbFailure("TMDB відповів HTTP $status")
    class Timeout(cause: IOException? = null) : TmdbFailure("Час очікування TMDB вичерпано") {
        init { if (cause != null) initCause(cause) }
    }
    class Network(cause: IOException? = null) : TmdbFailure("Не вдалося з’єднатися з TMDB") {
        init { if (cause != null) initCause(cause) }
    }
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
    suspend fun trailers(type: TmdbMediaType, id: Int, token: String,
        season: Int? = null): List<TmdbTrailer> {
        require(id > 0)
        if (type == TmdbMediaType.TV && season != null && season >= 0) {
            val seasonVideos = try {
                request(listOf("tv", id.toString(), "season", season.toString(), "videos"), token,
                    "en-US", "include_video_language" to "uk,en,null")
            } catch (_: TmdbFailure.Http) { null }
            seasonVideos?.optJSONArray("results")?.let(TmdbTrailerSelector::select)
                ?.takeIf(List<TmdbTrailer>::isNotEmpty)?.let { return it }
        }
        val videos = request(listOf(type.path, id.toString(), "videos"), token,
            "en-US", "include_video_language" to "uk,en,null")
        return videos.optJSONArray("results")?.let(TmdbTrailerSelector::select).orEmpty()
    }

    suspend fun popular(type: TmdbMediaType, token: String, genreId: Int? = null,
        excludedGenreIds: Set<Int> = emptySet()): List<TmdbTitle> =
        listPage(type, token, TmdbListSort.POPULARITY, 1, genreId,
            excludedGenreIds = excludedGenreIds).titles

    suspend fun trendingTv(token: String, window: String = "week", page: Int = 1): List<TmdbTitle> {
        require(window == "day" || window == "week")
        require(page in 1..500)
        return parseList(request(listOf("trending", "tv", window), token, "uk-UA",
            "page" to page.toString()), TmdbMediaType.TV, "uk-UA")
    }

    suspend fun onTheAirTv(token: String, page: Int = 1): List<TmdbTitle> {
        require(page in 1..500)
        return parseList(request(listOf("tv", "on_the_air"), token, "uk-UA",
            "page" to page.toString()), TmdbMediaType.TV, "uk-UA")
    }

    suspend fun listPage(type: TmdbMediaType, token: String, sort: TmdbListSort, page: Int,
        genreId: Int? = null, yearRange: IntRange? = null,
        yearAscending: Boolean = false, excludedGenreIds: Set<Int> = emptySet()): TmdbPage {
        require(page in 1..500)
        require(genreId == null || genreId > 0)
        require(excludedGenreIds.all { it > 0 })
        val currentDate = java.util.Calendar.getInstance()
        val currentYear = currentDate.get(java.util.Calendar.YEAR)
        require(yearRange == null || yearRange.first in 1920..currentYear &&
            yearRange.last in yearRange.first..currentYear)
        val years = yearRange ?: if (sort == TmdbListSort.YEAR) 1920..currentYear else null
        val json = if (sort == TmdbListSort.POPULARITY && genreId == null && years == null &&
            excludedGenreIds.isEmpty()) {
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
                if (excludedGenreIds.isNotEmpty()) add("without_genres" to
                    excludedGenreIds.sorted().joinToString("|"))
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
            .filter { (genreId == null || genreId in it.genreIds) &&
                it.genreIds.none { genre -> genre in excludedGenreIds } }, page,
            json.optInt("total_pages", page).coerceIn(page, 500))
    }

    suspend fun related(type: TmdbMediaType, id: Int, token: String): List<TmdbTitle> {
        require(id > 0)
        val json = request(listOf(type.path, id.toString(), "recommendations"),
            token, "uk-UA", "page" to "1")
        return parseList(json, type, "uk-UA").filterNot { it.id == id }
    }

    /** Explicit movie franchise membership; unlike recommendations these share a TMDB collection. */
    suspend fun collectionParts(collectionId: Int, token: String): List<TmdbTitle> {
        require(collectionId > 0)
        val json = request(listOf("collection", collectionId.toString()), token, "uk-UA")
        val parts = json.optJSONArray("parts") ?: throw TmdbFailure.InvalidResponse()
        return (0 until parts.length()).mapNotNull { index ->
            parts.optJSONObject(index)?.takeUnless { it.optBoolean("adult") }
                ?.toTitle(TmdbMediaType.MOVIE, "uk-UA")
        }.distinctBy(TmdbTitle::id)
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
        val json = request(listOf(type.path, id.toString()), token, language,
            *if (type == TmdbMediaType.TV) arrayOf("append_to_response" to "external_ids")
            else emptyArray())
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
        val imageLanguage = title.originalLanguage?.takeIf(String::isNotBlank)
            ?: countryCodes.firstOrNull()?.let { country ->
                mapOf("JP" to "ja", "US" to "en", "GB" to "en", "UA" to "uk",
                    "KR" to "ko", "CN" to "zh", "FR" to "fr", "DE" to "de")[country]
            }
        val images = try {
            request(listOf(type.path, id.toString(), "images"), token, "",
                "include_image_language" to listOfNotNull("null", imageLanguage, "uk", "en")
                    .distinct().joinToString(","))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val poster = images?.optJSONArray("posters")?.preferredImage(
            listOf(null, imageLanguage, "uk", "en"))
        val logo = images?.optJSONArray("logos")?.preferredImage(
            listOf(imageLanguage, "uk", "en", null))
        return TmdbDetails(
            title = title.copy(posterUrl = poster?.let { "https://image.tmdb.org/t/p/w780$it" }
                ?: title.posterUrl),
            genres = genres,
            status = json.optionalString("status"),
            runtimeMinutes = runtime.takeIf { it > 0 },
            seasons = json.optInt("number_of_seasons").takeIf { type == TmdbMediaType.TV && it > 0 },
            episodes = json.optInt("number_of_episodes").takeIf { type == TmdbMediaType.TV && it > 0 },
            collectionName = if (type == TmdbMediaType.MOVIE)
                json.optJSONObject("belongs_to_collection")?.optionalString("name") else null,
            collectionId = if (type == TmdbMediaType.MOVIE)
                json.optJSONObject("belongs_to_collection")?.optInt("id")?.takeIf { it > 0 }
            else null,
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
            tvdbId = if (type == TmdbMediaType.TV)
                json.optJSONObject("external_ids")?.optInt("tvdb_id")?.takeIf { it > 0 }
            else null,
            logoUrl = logo?.let { "https://image.tmdb.org/t/p/w500$it" },
        )
    }

    suspend fun seasonDetails(
        seriesId: Int,
        seasonNumber: Int,
        token: String,
        language: String = "uk-UA",
    ): TmdbSeasonDetails {
        require(seriesId > 0 && seasonNumber >= 0) { "Некоректний TMDB сезон" }
        val json = request(listOf("tv", seriesId.toString(), "season", seasonNumber.toString()),
            token, language)
        val episodes = json.optJSONArray("episodes")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val episode = array.optJSONObject(index) ?: return@mapNotNull null
                val number = episode.optInt("episode_number").takeIf { it > 0 }
                    ?: return@mapNotNull null
                TmdbEpisode(
                    id = episode.optInt("id"),
                    seasonNumber = episode.optInt("season_number", seasonNumber),
                    episodeNumber = number,
                    name = episode.optionalString("name") ?: "Серія $number",
                    overview = episode.optionalString("overview"),
                    stillUrl = episode.optionalString("still_path")
                        ?.let { "https://image.tmdb.org/t/p/w780$it" },
                    airDate = episode.optionalString("air_date"),
                )
            }
        }.orEmpty()
        return TmdbSeasonDetails(
            season = TmdbSeason(seasonNumber, json.optionalString("name"),
                json.optionalString("air_date")?.take(4)?.toIntOrNull(), episodes.size),
            episodes = episodes,
        )
    }

    private suspend fun request(
        segments: List<String>, token: String, language: String, vararg parameters: Pair<String, String>,
    ): JSONObject {
        if (token.isBlank()) throw TmdbFailure.MissingToken()
        val url = baseUrl.newBuilder().apply {
            segments.forEach(::addPathSegment)
            if (language.isNotBlank()) addQueryParameter("language", language)
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
            throw TmdbFailure.Timeout(error)
        } catch (error: IOException) {
            throw TmdbFailure.Network(error)
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

private fun JSONArray.preferredImage(languages: List<String?>): String? {
    val priorities = languages.distinct()
    return (0 until length()).mapNotNull { optJSONObject(it) }
        .filter { image -> image.optionalString("file_path")?.let { path ->
            path.startsWith("/") && !path.endsWith(".svg", ignoreCase = true)
        } == true }
        .minWithOrNull(compareBy<JSONObject> {
            val language = it.optionalString("iso_639_1")
            priorities.indexOf(language).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
        }.thenByDescending { it.optDouble("vote_average") }
            .thenByDescending { it.optInt("vote_count") })
        ?.takeIf { image ->
            priorities.contains(image.optionalString("iso_639_1"))
        }?.optionalString("file_path")
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
        } ?: optJSONArray("genres")?.let { array ->
            (0 until array.length()).map { array.optJSONObject(it)?.optInt("id") ?: 0 }
                .filter { it > 0 }.toSet()
        } ?: emptySet(),
        originCountries = optJSONArray("origin_country")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.orEmpty(),
        backdropUrl = optionalString("backdrop_path")?.takeIf { it.startsWith("/") }
            ?.let { "https://image.tmdb.org/t/p/w1280$it" },
        popularity = optDouble("popularity", Double.NaN)
            .takeIf { it.isFinite() && it >= 0.0 },
        airDate = date,
    )
}

private fun JSONObject.optionalString(key: String): String? =
    takeIf { it.has(key) && !it.isNull(key) }?.optString(key)?.trim()?.takeIf(String::isNotBlank)
