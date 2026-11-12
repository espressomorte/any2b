package ua.any2b.core.anilist

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AniListNetworkTest {
    @Test fun ipv4AddressesAreTriedBeforeIpv6() {
        val ipv4 = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4))
        val ipv6 = InetAddress.getByAddress(ByteArray(16).also { it[15] = 1 })
        val dns = Ipv4FirstDns(object : Dns {
            override fun lookup(hostname: String) = listOf(ipv6, ipv4)
        })

        assertEquals(listOf(ipv4, ipv6), dns.lookup("graphql.anilist.co"))
    }

    @Test fun networkFailureHasReadableMessageAndKeepsCause() {
        val http = OkHttpClient.Builder().addInterceptor {
            throw SocketTimeoutException("failed to connect to 2606:4700:20::681a:e47")
        }.build()

        val error = assertFailsWith<AniListException> {
            runBlocking { AniListClient(http).homeRows(1) }
        }
        assertEquals("Не вдалося з’єднатися з AniList. Перевірте мережу та повторіть.", error.message)
        assertTrue(generateSequence(error as Throwable) { it.cause }
            .any { it is SocketTimeoutException })
    }
}
