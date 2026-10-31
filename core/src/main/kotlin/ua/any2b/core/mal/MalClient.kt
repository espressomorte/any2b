package ua.any2b.core.mal

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class MalAnime(
    val id: Int,
    val title: String,
    val englishTitle: String?,
    val posterUrl: String?,
    val year: Int?,
    val score: Double?,
    val mediaType: String?,
    val startDate: String? = null,
)

data class MalRankingPage(val items: List<MalAnime>, val hasMore: Boolean)

class MalClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS).build(),
) {
    suspend fun topAnime(clientId: String, page: Int, perPage: Int = 25,
        rankingType: String = "all"): MalRankingPage {
        require(clientId.isNotBlank() && page > 0 && perPage in 1..100)
        require(rankingType == "all" || rankingType == "bypopularity")
        val url = ENDPOINT.newBuilder()
            .addQueryParameter("ranking_type", rankingType)
            .addQueryParameter("limit", perPage.toString())
            .addQueryParameter("offset", ((page - 1) * perPage).toString())
            .addQueryParameter("fields", "mean,rank,media_type,start_date,main_picture,alternative_titles")
            .build()
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .header("X-MAL-CLIENT-ID", clientId).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("MyAnimeList HTTP ${response.code}")
                parseRanking(response.body?.string().orEmpty())
            }
        }
    }

    companion object {
        private val ENDPOINT = "https://api.myanimelist.net/v2/anime/ranking".toHttpUrl()

        fun parseRanking(json: String): MalRankingPage {
            val root = JSONObject(json)
            val data = root.getJSONArray("data")
            val items = (0 until data.length()).mapNotNull { index ->
                val node = data.optJSONObject(index)?.optJSONObject("node") ?: return@mapNotNull null
                val id = node.optInt("id")
                if (id <= 0) return@mapNotNull null
                val picture = node.optJSONObject("main_picture")
                MalAnime(
                    id = id,
                    title = node.optString("title").ifBlank { "—" },
                    englishTitle = node.optJSONObject("alternative_titles")
                        ?.optString("en")?.takeIf(String::isNotBlank),
                    posterUrl = picture?.optString("large")?.takeIf(String::isNotBlank)
                        ?: picture?.optString("medium")?.takeIf(String::isNotBlank),
                    year = node.optString("start_date").take(4).toIntOrNull(),
                    score = node.optDouble("mean").takeIf(Double::isFinite),
                    mediaType = node.optString("media_type").takeIf(String::isNotBlank),
                    startDate = node.optString("start_date").takeIf(String::isNotBlank),
                )
            }
            return MalRankingPage(items,
                root.optJSONObject("paging")?.optString("next")?.isNotBlank() == true)
        }
    }
}
