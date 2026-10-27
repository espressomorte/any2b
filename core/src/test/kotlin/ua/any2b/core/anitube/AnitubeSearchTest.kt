package ua.any2b.core.anitube

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnitubeSearchTest {
    @Test
    fun metadataIsReadOnlyFromItsOwnSearchHit() = runBlocking {
        val html = """
            <article><h2 itemprop="name"><a href="https://anitube.in.ua/101-first.html">Перший</a></h2>
            <dl><dt>Рік виходу аніме:</dt> <a href="/2024">2024</a></dl>
            <dl><dt>Серій:</dt> 4 з 12 (24 хв)</dl></article>
            <section><h3 itemprop="name"><a href="https://anitube.in.ua/102-second.html">Другий</a></h3>
            <dl><dt>Рік виходу аніме:</dt> 2022</dl>
            <dl><dt>Серій:</dt> 1 з ? (110 хв)</dl></section>
            <h3 itemprop="name"><a href="https://anitube.in.ua/103-third.html">Третій</a></h3>
            <dl><dt>Серій:</dt> 1 (120 хв)</dl>
            <h3 itemprop="name"><a href="https://anitube.in.ua/104-fourth.html">Четвертий</a></h3>
        """.trimIndent()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(html.toResponseBody()).build()
        }.build()

        val results = AnitubeClient(http).search("тест")

        assertEquals(4, results.size)
        assertEquals(2024, results[0].year)
        assertEquals(4, results[0].airedEpisodes)
        assertEquals(12, results[0].totalEpisodes)
        assertEquals(24, results[0].durationMinutes)
        assertEquals(2022, results[1].year)
        assertEquals(1, results[1].airedEpisodes)
        assertNull(results[1].totalEpisodes)
        assertEquals(110, results[1].durationMinutes)
        assertNull(results[2].year)
        assertEquals(1, results[2].airedEpisodes)
        assertNull(results[2].totalEpisodes)
        assertEquals(120, results[2].durationMinutes)
        assertNull(results[3].year)
        assertNull(results[3].airedEpisodes)
    }
}
