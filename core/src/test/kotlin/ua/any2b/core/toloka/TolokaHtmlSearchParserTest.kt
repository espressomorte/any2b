package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TolokaHtmlSearchParserTest {
    @Test
    fun readsRowsWithoutExposingPersonalDownloadLinksInHits() {
        val html = """<table class="forumline"><tr class="prow1">
            <td></td><td></td><td><a href="/t668277">Фільм / Movie (2020)</a></td>
            <td><a href="/tracker.php?pid=12">studio</a></td><td></td>
            <td><a href="/download.php?id=668277&amp;passkey=private">torrent</a></td>
            <td>6.54 GB</td><td></td><td></td><td><b>53</b></td><td><b>3</b></td>
            </tr><tr class="prow2"><td></td><td></td>
            <td><a href="/t668278">Інший фільм</a></td><td></td><td></td><td></td>
            <td>1 GB</td><td></td><td></td><td></td><td></td></tr></table>"""
        val parsed = TolokaHtmlSearchParser.parse(html)
        assertEquals(listOf("668277", "668278"), parsed.hits.map { it.id })
        assertEquals("studio", parsed.hits.first().author)
        assertEquals(53, parsed.hits.first().seeders)
        assertNull(parsed.hits.last().seeders)
        assertEquals("https://toloka.to/t668277", parsed.hits.first().pageUrl)
        assertEquals(1, parsed.downloads.size)
        assertEquals("private", parsed.downloads["668277"]?.queryParameter("passkey"))
    }
}
