package ua.any2b.core.anilist

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AniListDetailsTest {
    @Test
    fun detailsKeepAnimeRelationsAndRecommendationsWithoutDuplicatesOrAdultTitles() = runBlocking {
        val response = """{
          "data": { "Media": {
            "id": 1, "title": { "romaji": "Main", "english": null, "native": null },
            "coverImage": { "large": "https://example.com/main.jpg" },
            "studios": { "nodes": [] },
            "relations": { "edges": [
              { "node": ${card(2, "Sequel")} },
              { "node": ${card(3, "Manga", type = "MANGA")} },
              { "node": ${card(4, "Adult", adult = true)} },
              { "node": ${card(1, "Main")} }
            ] },
            "recommendations": { "pageInfo": { "hasNextPage": true }, "nodes": [
              { "mediaRecommendation": ${card(5, "Suggested")} },
              { "mediaRecommendation": ${card(5, "Suggested")} },
              { "mediaRecommendation": ${card(1, "Main")} },
              { "mediaRecommendation": ${card(6, "Adult", adult = true)} }
            ] }
          } }
        }"""
        val client = AniListClient(fakeHttp(response) { requestBody ->
            assertTrue(Regex("relations\\s*\\{\\s*edges\\s*\\{").containsMatchIn(JSONObject(requestBody).getString("query")))
        })

        val details = client.animeDetails(1)

        assertEquals(listOf(2), details.related.map { it.id })
        assertEquals(listOf(5), details.recommendations.map { it.id })
        assertTrue(details.recommendationsHasMore)
        assertEquals("Sequel", details.related.single().title.display)
    }

    @Test
    fun recommendationPageUsesPaginationAndExcludesCurrentTitle() = runBlocking {
        val response = """{
          "data": { "Media": { "recommendations": {
            "pageInfo": { "hasNextPage": false },
            "nodes": [
              { "mediaRecommendation": ${card(1, "Main")} },
              { "mediaRecommendation": ${card(7, "Next")} }
            ]
          } } }
        }"""
        val client = AniListClient(fakeHttp(response) { requestBody ->
            val variables = JSONObject(requestBody).getJSONObject("variables")
            assertEquals(2, variables.getInt("page"))
            assertEquals(20, variables.getInt("perPage"))
        })

        val page = client.recommendationsPage(1, page = 2)

        assertEquals(listOf(7), page.items.map { it.id })
        assertFalse(page.hasNextPage)
    }

    private fun fakeHttp(body: String, verifyRequest: (String) -> Unit = {}) =
        OkHttpClient.Builder().addInterceptor { chain ->
        val requestBuffer = Buffer()
        chain.request().body?.writeTo(requestBuffer)
        verifyRequest(requestBuffer.readUtf8())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(body.toResponseBody()).build()
    }.build()

    private fun card(id: Int, title: String, type: String = "ANIME", adult: Boolean = false) =
        """{"id":$id,"type":"$type","isAdult":$adult,
           "title":{"romaji":"$title","english":null,"native":null},
           "coverImage":{"large":"https://example.com/$id.jpg"},
           "averageScore":80,"episodes":12,"format":"TV","status":"FINISHED","seasonYear":2025}"""
}
