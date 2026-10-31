package ua.any2b.core.anilist

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import ua.any2b.core.tmdb.TmdbListSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AniListCategorySortTest {
    @Test
    fun yearSortFiltersOnServerAndExcludesFutureDates() = runBlocking {
        var payload: JSONObject? = null
        val client = AniListClient(OkHttpClient.Builder().addInterceptor { chain ->
            payload = JSONObject(chain.request().body!!.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            })
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("""{"data":{"Page":{"media":[]}}}""".toResponseBody())
                .build()
        }.build())
        client.categoryPage("popular_all", 1, sort = TmdbListSort.YEAR,
            yearRange = 2010..2019, yearAscending = true)
        val query = payload!!.getString("query")
        val variables = payload!!.getJSONObject("variables")
        assertTrue("sort: START_DATE" in query)
        assertTrue("startDate_greater:" in query)
        assertTrue("startDate_lesser:" in query)
        assertEquals(20091231, variables.getInt("lowerDate"))
        assertEquals(20200101, variables.getInt("upperDate"))
    }
}
