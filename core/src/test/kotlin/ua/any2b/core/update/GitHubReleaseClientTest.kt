package ua.any2b.core.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleaseClientTest {
    @Test
    fun prereleaseVersionsAreOrderedNumerically() {
        assertTrue(compareVersions("v0.1.0-alpha.10", "0.1.0-alpha.2") > 0)
        assertTrue(compareVersions("v0.1.0", "0.1.0-alpha.10") > 0)
        assertTrue(compareVersions("v0.2.0-alpha.1", "0.1.9") > 0)
        assertEquals(0, compareVersions("v0.1.0-alpha.1", "0.1.0-alpha.1"))
    }

    @Test
    fun newestPublishedPrereleaseIsReturnedEvenWithoutAnApk() = runBlocking {
        val body = """[
          {"tag_name":"v0.1.0-alpha.2","html_url":"https://github.com/espressomorte/any2b/releases/tag/v0.1.0-alpha.2","draft":false,"published_at":"2026-10-26T12:00:00Z","assets":[]},
          {"tag_name":"v0.1.0-alpha.1","html_url":"https://github.com/espressomorte/any2b/releases/tag/v0.1.0-alpha.1","draft":false,"published_at":"2026-10-25T12:00:00Z","assets":[{"name":"animarium.apk","browser_download_url":"https://github.com/espressomorte/any2b/releases/download/v0.1.0-alpha.1/animarium.apk"}]},
          {"tag_name":"v0.2.0","html_url":"https://github.com/espressomorte/any2b/releases/tag/v0.2.0","draft":true,"published_at":null,"assets":[]}
        ]"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()

        val release = GitHubReleaseClient(http).newestRelease()

        assertEquals("v0.1.0-alpha.2", release?.tag)
        assertNull(release?.apkUrl)
    }

    @Test
    fun apkAssetIsSelectedFromPublishedRelease() = runBlocking {
        val apkUrl = "https://github.com/espressomorte/any2b/releases/download/v0.1.0-alpha.2/animarium.apk"
        val body = """[{"tag_name":"v0.1.0-alpha.2","html_url":"https://github.com/espressomorte/any2b/releases/tag/v0.1.0-alpha.2","draft":false,"published_at":"2026-10-26T12:00:00Z","assets":[{"name":"source.zip","browser_download_url":"https://example.com/source.zip"},{"name":"animarium.apk","browser_download_url":"$apkUrl"}]}]"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()

        assertEquals(apkUrl, GitHubReleaseClient(http).newestRelease()?.apkUrl)
    }
}
