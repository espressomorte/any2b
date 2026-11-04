package ua.any2b.core.toloka

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectTolokaClientTest {
    @Test
    fun logsInSearchesAndDownloadsOnlySelectedTorrent() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val logins = AtomicInteger()
        var searchedQuery = ""
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = when {
                path == "/login.php" && exchange.requestMethod == "POST" -> {
                    logins.incrementAndGet()
                    exchange.responseHeaders.add("Set-Cookie", "sid=fixture; Path=/; HttpOnly")
                    exchange.responseHeaders.add("Location", "/tracker.php")
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                    return@createContext
                }
                path == "/tracker.php" && exchange.requestURI.query?.contains("nm=") == true -> {
                    searchedQuery = exchange.requestURI.rawQuery.orEmpty()
                    """<a href="/login.php?logout=true">Вихід</a><table class="forumline">
                    <tr class="prow1"><td></td><td></td><td><a href="/t123">Тест</a></td>
                    <td></td><td></td><td><a href="/dl?id=123">DL</a></td><td>1 GB</td>
                    <td></td><td></td><td><b>1</b></td><td><b>0</b></td></tr></table>"""
                }
                path == "/tracker.php" -> "<a href='/login.php?logout=true'>Вихід</a>"
                path == "/dl" -> "d4:infod4:name4:testee" // fixture bytes, no personal URLs
                else -> "<html>login</html>"
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = DirectTolokaClient("http://127.0.0.1:${server.address.port}/".toHttpUrl())
            val first = client.search("user", "password", "Тест 2020")
            assertEquals(listOf("123"), first.results.map { it.id })
            assertFalse(first.sessionReused ?: true)
            assertTrue(searchedQuery.contains("nm="))
            assertEquals('d'.code.toByte(), client.torrent("user", "password", "123")[0])
            assertEquals(1, logins.get())
        } finally { server.stop(0) }
    }
}
