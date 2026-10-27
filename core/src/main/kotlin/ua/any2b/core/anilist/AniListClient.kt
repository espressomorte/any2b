package ua.any2b.core.anilist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Клієнт публічного GraphQL API AniList (без ключа, ліміт ~90 запитів на хвилину).
 * Головна сторінка тягнеться ОДНИМ запитом: усі ряди йдуть аліасами.
 */
class AniListClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    /** Усі ряди головної одним запитом. Порожні ряди відкидаються. */
    suspend fun homeRows(perRow: Int = 20): List<HomeRow> {
        val (season, year) = Season.current()
        val (nextSeason, nextYear) = season.next(year)

        val vars = JSONObject()
            .put("season", season.name)
            .put("year", year)
            .put("nextSeason", nextSeason.name)
            .put("nextYear", nextYear)
            .put("perPage", perRow)

        val data = execute(HOME_QUERY, vars)

        fun row(alias: String, id: String, title: String) = HomeRow(
            id = id,
            title = title,
            items = data.getJSONObject(alias).getJSONArray("media").toCards(),
        )

        return listOf(
            row("popularSeason", "popular_season", "Популярне цього сезону"),
            row("trending", "trending", "У тренді"),
            row("upcoming", "upcoming", "Скоро: наступний сезон"),
            row("popularAll", "popular_all", "Популярне за весь час"),
            row("topScore", "top_score", "Найвищі оцінки"),
        ).filter { it.items.isNotEmpty() }
    }

    /** Детальна інформація про обраний тайтл для сторінки інфо. */
    suspend fun animeDetails(id: Int): AnimeDetails {
        val vars = JSONObject().put("id", id)
        val data = execute(DETAILS_QUERY, vars)
        val m = data.getJSONObject("Media")
        val t = m.getJSONObject("title")
        val cover = m.optJSONObject("coverImage")
        val genres = m.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        val studios = mutableListOf<String>()
        m.optJSONObject("studios")?.optJSONArray("nodes")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.getJSONObject(i).optString("name").takeIf { it.isNotBlank() }?.let { studios.add(it) }
            }
        }

        val related = m.relationsCards()
            .filterNot { it.id == id }
        val recommendationsConnection = m.optJSONObject("recommendations")
        val recommendations = recommendationsConnection?.recommendedCards().orEmpty()
            .filterNot { it.id == id }

        return AnimeDetails(
            id = m.getInt("id"),
            title = Title(t.str("romaji"), t.str("english"), t.str("native")),
            cover = cover?.str("extraLarge") ?: cover?.str("large"),
            banner = m.str("bannerImage"),
            description = m.str("description"),
            score = m.int("averageScore"),
            episodes = m.int("episodes"),
            durationMinutes = m.int("duration"),
            format = m.str("format"),
            status = m.str("status"),
            seasonYear = m.int("seasonYear"),
            genres = genres,
            studios = studios,
            related = related,
            recommendations = recommendations,
            recommendationsHasMore = recommendationsConnection?.optJSONObject("pageInfo")
                ?.optBoolean("hasNextPage") == true,
            trailer = m.optJSONObject("trailer")?.let { trailer ->
                val trailerId = trailer.str("id")
                val site = trailer.str("site")
                if (trailerId.isNullOrBlank() || site.isNullOrBlank()) null
                else MediaTrailer(trailerId, site)
            },
            synonyms = m.optJSONArray("synonyms")?.let { values ->
                (0 until values.length()).mapNotNull { index ->
                    values.optString(index).takeIf { it.isNotBlank() }
                }
            }.orEmpty(),
            nextEpisode = m.optJSONObject("nextAiringEpisode")?.int("episode"),
        )
    }

    /** A page of recommendations for the full list on a details screen. */
    suspend fun recommendationsPage(id: Int, page: Int, perPage: Int = 20): AnimeCardPage {
        require(page > 0 && perPage in 1..25)
        val variables = JSONObject().put("id", id).put("page", page).put("perPage", perPage)
        val connection = execute(RECOMMENDATIONS_QUERY, variables)
            .getJSONObject("Media").getJSONObject("recommendations")
        return AnimeCardPage(
            items = connection.recommendedCards().filterNot { it.id == id },
            hasNextPage = connection.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true,
        )
    }

    /** Пошук тайтлів за запитом. */
    suspend fun search(query: String, perPage: Int = 20): List<AnimeCard> {
        val vars = JSONObject().put("search", query).put("perPage", perPage)
        val data = execute(SEARCH_QUERY, vars)
        return data.getJSONObject("Page").getJSONArray("media").toCards()
    }

    /** One page of a home category, used by the full category screen. */
    suspend fun categoryPage(categoryId: String, page: Int, perPage: Int = 20): List<AnimeCard> {
        require(page > 0 && perPage > 0)
        val (season, year) = Season.current()
        val (nextSeason, nextYear) = season.next(year)
        val genre = categoryId.takeIf { it.startsWith("genre:") }
            ?.removePrefix("genre:")?.takeIf { it.isNotBlank() }
        val mediaArgs = when {
            categoryId == "popular_season" -> "season: ${'$'}season, seasonYear: ${'$'}year, sort: POPULARITY_DESC"
            categoryId == "trending" -> "sort: TRENDING_DESC"
            categoryId == "upcoming" -> "season: ${'$'}nextSeason, seasonYear: ${'$'}nextYear, sort: POPULARITY_DESC"
            categoryId == "popular_all" -> "sort: POPULARITY_DESC"
            categoryId == "top_score" -> "sort: SCORE_DESC"
            genre != null -> "genre_in: ${'$'}genres, sort: POPULARITY_DESC"
            else -> throw IllegalArgumentException("Невідома категорія: $categoryId")
        }
        val categoryVariables = when {
            categoryId == "popular_season" -> ", ${'$'}season: MediaSeason, ${'$'}year: Int"
            categoryId == "upcoming" -> ", ${'$'}nextSeason: MediaSeason, ${'$'}nextYear: Int"
            genre != null -> ", ${'$'}genres: [String]"
            else -> ""
        }
        val variables = JSONObject()
            .put("page", page)
            .put("perPage", perPage)
            .put("season", season.name)
            .put("year", year)
            .put("nextSeason", nextSeason.name)
            .put("nextYear", nextYear)
        if (genre != null) variables.put("genres", JSONArray().put(genre))
        val query = """
query Category(${'$'}page: Int, ${'$'}perPage: Int$categoryVariables) {
  Page(page: ${'$'}page, perPage: ${'$'}perPage) {
    media(type: ANIME, isAdult: false, $mediaArgs) {
      id
      title { romaji english native }
      coverImage { large extraLarge }
      averageScore
      episodes
      format
      status
      seasonYear
      nextAiringEpisode { episode }
    }
  }
}
"""
        return execute(query, variables).getJSONObject("Page").getJSONArray("media").toCards()
    }

    // ── Мережа ────────────────────────────────────────────────────────────

    private suspend fun execute(query: String, variables: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("query", query)
                .put("variables", variables)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(payload)
                .header("Accept", "application/json")
                .header("User-Agent", "Any2B/1.0 (Android TV)")
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 429) {
                    val wait = resp.header("Retry-After") ?: "60"
                    throw AniListException("Забагато запитів до AniList, спробуйте через $wait с")
                }
                if (!resp.isSuccessful) {
                    throw AniListException("AniList відповів HTTP ${resp.code}")
                }
                val json = JSONObject(text)
                if (json.has("errors") && json.isNull("data")) {
                    val msg = json.getJSONArray("errors")
                        .getJSONObject(0)
                        .optString("message", "помилка запиту")
                    throw AniListException("AniList: $msg")
                }
                json.getJSONObject("data")
            }
        }

    // ── Розбір ────────────────────────────────────────────────────────────

    private fun JSONArray.toCards(): List<AnimeCard> =
        (0 until length()).map { getJSONObject(it).toCard() }

    private fun JSONArray.toAnimeCards(): List<AnimeCard> =
        (0 until length()).mapNotNull { optJSONObject(it) }
            .filter { it.optString("type") == "ANIME" && !it.optBoolean("isAdult") }
            .map { it.toCard() }.distinctBy { it.id }

    private fun JSONObject.recommendedCards(): List<AnimeCard> {
        val nodes = optJSONArray("nodes") ?: return emptyList()
        val media = JSONArray()
        for (i in 0 until nodes.length()) {
            nodes.optJSONObject(i)?.optJSONObject("mediaRecommendation")?.let { media.put(it) }
        }
        return media.toAnimeCards()
    }

    private fun JSONObject.relationsCards(): List<AnimeCard> {
        val edges = optJSONObject("relations")?.optJSONArray("edges") ?: return emptyList()
        val media = JSONArray()
        for (i in 0 until edges.length()) {
            edges.optJSONObject(i)?.optJSONObject("node")?.let { media.put(it) }
        }
        return media.toAnimeCards()
    }

    private fun JSONObject.toCard(): AnimeCard {
        val t = getJSONObject("title")
        val cover = optJSONObject("coverImage")
        return AnimeCard(
            id = getInt("id"),
            title = Title(t.str("romaji"), t.str("english"), t.str("native")),
            cover = cover?.str("extraLarge") ?: cover?.str("large"),
            score = int("averageScore"),
            episodes = int("episodes"),
            format = str("format"),
            status = str("status"),
            seasonYear = int("seasonYear"),
            nextEpisode = optJSONObject("nextAiringEpisode")?.int("episode"),
        )
    }

    private fun JSONObject.str(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.int(name: String): Int? = if (isNull(name)) null else getInt(name)

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co"

        const val HOME_QUERY = """
query Home(${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}nextSeason: MediaSeason, ${'$'}nextYear: Int, ${'$'}perPage: Int) {
  popularSeason: Page(page: 1, perPage: ${'$'}perPage) {
    media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, sort: POPULARITY_DESC, isAdult: false) { ...card }
  }
  trending: Page(page: 1, perPage: ${'$'}perPage) {
    media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { ...card }
  }
  upcoming: Page(page: 1, perPage: ${'$'}perPage) {
    media(type: ANIME, season: ${'$'}nextSeason, seasonYear: ${'$'}nextYear, sort: POPULARITY_DESC, isAdult: false) { ...card }
  }
  popularAll: Page(page: 1, perPage: ${'$'}perPage) {
    media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) { ...card }
  }
  topScore: Page(page: 1, perPage: ${'$'}perPage) {
    media(type: ANIME, sort: SCORE_DESC, isAdult: false) { ...card }
  }
}

fragment card on Media {
  id
  title { romaji english native }
  coverImage { large extraLarge }
  averageScore
  episodes
  format
  status
  seasonYear
  nextAiringEpisode { episode }
}
"""

        const val DETAILS_QUERY = """
query Details(${'$'}id: Int) {
  Media(id: ${'$'}id, type: ANIME) {
    id
    title { romaji english native }
    coverImage { large extraLarge }
    bannerImage
    description(asHtml: false)
    averageScore
    episodes
    duration
    format
    status
    seasonYear
    synonyms
    nextAiringEpisode { episode }
    trailer { id site }
    genres
    studios(isMain: true) {
      nodes { name }
    }
    relations {
      edges {
        node { ...relatedCard }
      }
    }
    recommendations(page: 1, perPage: 25) {
      pageInfo { hasNextPage }
      nodes { mediaRecommendation { ...relatedCard } }
    }
  }
}

fragment relatedCard on Media {
  id
  type
  isAdult
  title { romaji english native }
  coverImage { large extraLarge }
  averageScore
  episodes
  format
  status
  seasonYear
  nextAiringEpisode { episode }
}
"""

        const val RECOMMENDATIONS_QUERY = """
query Recommendations(${'$'}id: Int, ${'$'}page: Int, ${'$'}perPage: Int) {
  Media(id: ${'$'}id, type: ANIME) {
    recommendations(page: ${'$'}page, perPage: ${'$'}perPage) {
      pageInfo { hasNextPage }
      nodes {
        mediaRecommendation {
          id
          type
          isAdult
          title { romaji english native }
          coverImage { large extraLarge }
          averageScore
          episodes
          format
          status
          seasonYear
          nextAiringEpisode { episode }
        }
      }
    }
  }
}
"""

        const val SEARCH_QUERY = """
query Search(${'$'}search: String, ${'$'}perPage: Int) {
  Page(page: 1, perPage: ${'$'}perPage) {
    media(type: ANIME, search: ${'$'}search, isAdult: false) { ...card }
  }
}

fragment card on Media {
  id
  title { romaji english native }
  coverImage { large extraLarge }
  averageScore
  episodes
  format
  status
  seasonYear
  nextAiringEpisode { episode }
}
"""
    }
}
