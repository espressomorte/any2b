package ua.any2b.core.network

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ApiSingleFlightTest {
    @Test
    fun requestKeyNormalizesHeaderNamesAndOrderButKeepsValuesDistinct() {
        val first = ApiRequestKey("GET", "https://example.org/3/tv/1?language=uk-UA",
            listOf("Authorization" to "Bearer token", "Accept" to "application/json"))
        val same = ApiRequestKey("get", "https://example.org/3/tv/1?language=uk-UA",
            listOf("accept" to "application/json", "authorization" to "Bearer token"))
        val otherToken = ApiRequestKey("GET", "https://example.org/3/tv/1?language=uk-UA",
            listOf("accept" to "application/json", "authorization" to "Bearer other"))
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, otherToken)
    }

    @Test
    fun cancellingOneWaiterDoesNotCancelTheOther() = runBlocking {
        val flights = ApiSingleFlight()
        val key = ApiRequestKey("GET", "https://example.org/3/tv/1?language=uk-UA",
            listOf("Authorization" to "Bearer token"))
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<String>()
        val calls = AtomicInteger()
        val request: suspend () -> String = {
            calls.incrementAndGet()
            started.complete(Unit)
            finish.await()
        }
        val first = async { flights.await(key, request) }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            flights.await(key, request)
        }
        first.cancelAndJoin()
        finish.complete("ok")
        assertEquals("ok", second.await())
        assertEquals(1, calls.get())
        assertEquals("again", flights.await(key) { "again" })
    }
}
