package ua.any2b.core.anitube

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayableVoicesTest {
    @Test
    fun seriesCountDistinctEpisodesAndKeepSeparateVoiceCategories() = runBlocking {
        val playlist = """
            <li data-id="0_0">СУБТИТРИ</li>
            <li data-id="0_0_0">Студія</li>
            <li data-id="0_0_0_0">ASHDI</li>
            <li data-id="0_0_0_0" data-file="https://ashdi.vip/one">1 серія</li>
            <li data-id="0_0_0_0" data-file="https://ashdi.vip/two">2 серія</li>
            <li data-id="0_0_0_1">FENIX</li>
            <li data-id="0_0_0_1" data-file="https://fenixplay.xyz/one">1 серія</li>
            <li data-id="0_0_0_1" data-file="https://fenixplay.xyz/two">2 серія</li>
            <li data-id="0_0_0_2">MOON</li>
            <li data-id="0_0_0_2" data-file="https://moonanime.art/three">3 серія</li>
            <li data-id="0_1">ОЗВУЧЕННЯ</li>
            <li data-id="0_1_0">Студія</li>
            <li data-id="0_1_0_0">ASHDI</li>
            <li data-id="0_1_0_0" data-file="https://ashdi.vip/dub-one">1 серія</li>
        """.trimIndent()
        val raw = client(playlist).getVoices("https://anitube.in.ua/1234-sample.html")

        val voices = playableVoices(raw, "TV")

        assertEquals(2, voices.size)
        assertEquals(listOf("СУБТИТРИ", "ОЗВУЧЕННЯ"), voices.map { it.category })
        assertEquals(listOf(1, 2), voices[0].episodes.map { it.episodeNumber })
        assertEquals(listOf("https://ashdi.vip/one", "https://ashdi.vip/two"), voices[0].episodes.map { it.fileUrl })
        assertEquals(listOf(1), voices[1].episodes.map { it.episodeNumber })
    }

    @Test
    fun movieSelectsTheFilmInsteadOfExtrasAndNeverHasANextEpisode() {
        val voice = AnitubeVoice("ОЗВУЧЕННЯ", "Студія", listOf(
            episode(1, "1 серія", "https://ashdi.vip/extra"),
            episode(2, "Фільм 2", "https://ashdi.vip/movie"),
            episode(3, "Спешл", "https://ashdi.vip/special"),
        ))
        val extrasOnly = AnitubeVoice("ОЗВУЧЕННЯ", "Додаткове", listOf(
            episode(1, "1 серія", "https://ashdi.vip/extra-one"),
            episode(2, "2 серія", "https://ashdi.vip/extra-two"),
        ))

        val result = playableVoices(listOf(voice, extrasOnly), "MOVIE")

        assertEquals(1, result.size)
        assertEquals(listOf("https://ashdi.vip/movie"), result.single().episodes.map { it.fileUrl })
        assertEquals(1, result.single().episodes.single().episodeNumber)
    }

    private fun episode(number: Int, title: String, url: String) =
        AnitubeEpisode("0_0_0_0", number, title, url, "ASHDI")

    private fun client(playlist: String): AnitubeClient {
        val ajax = JSONObject().put("success", true).put("response", playlist).toString()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath.endsWith("playlists.php")) ajax
                else "<script>dle_login_hash = 'test'</script>"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        return AnitubeClient(http)
    }
}
