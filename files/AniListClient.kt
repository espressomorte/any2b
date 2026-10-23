package ua.any2b.tvplayer.anilist

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

    // org.json на Android віддає рядок "null" для JSON null, тому перевіряємо isNull
    private fun JSONObject.str(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.int(name: String): Int? = if (isNull(name)) null else getInt(name)

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co"

        /** Публічний, щоб запит можна було перевірити окремо від застосунку. */
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
    }
}
