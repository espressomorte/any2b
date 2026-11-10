package ua.any2b.core.toloka

import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Credentials

/** /echo is TorrServer's documented health/version endpoint. Torrent API comes in a later stage. */
class TorrServerProbe(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    suspend fun version(
        baseUrl: String,
        auth: TorrServerBasicAuth? = null,
        timeoutSeconds: Long = 8,
    ): String = withContext(Dispatchers.IO) {
        require(timeoutSeconds > 0) { "Таймаут має бути більшим за нуль" }
        val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Некоректна адреса TorrServer")
        require(base.scheme == "http" || base.scheme == "https") { "Потрібна HTTP або HTTPS адреса" }
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) {
            "Адреса TorrServer не має містити облікових даних або параметрів"
        }
        val builder = Request.Builder().url(base.newBuilder().addPathSegment("echo").build())
        if (auth != null) builder.header("Authorization", Credentials.basic(auth.username, auth.password))
        http.newBuilder()
            .connectTimeout(minOf(4, timeoutSeconds), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
            .newCall(builder.build())
            .execute().use { response ->
                if (response.code == 401) throw IllegalStateException("TorrServer потребує авторизації")
                if (!response.isSuccessful) throw IllegalStateException("TorrServer відповів HTTP ${response.code}")
                val version = response.body?.string()?.trim()?.take(100)
                    ?: throw IllegalStateException("TorrServer не повернув версію")
                if (!version.matches(Regex("[A-Za-z0-9._+ -]{1,100}"))) {
                    throw IllegalStateException("TorrServer повернув неочікувану версію")
                }
                version
            }
    }

    /** Short, bounded health checks used after returning from the TorrServe app. */
    suspend fun awaitAvailable(baseUrl: String, auth: TorrServerBasicAuth? = null): String {
        var lastFailure: IOException? = null
        repeat(3) { attempt ->
            try {
                return version(baseUrl, auth, timeoutSeconds = 2)
            } catch (error: IOException) {
                lastFailure = error
                if (attempt < 2) delay(700L * (attempt + 1))
            }
        }
        throw lastFailure ?: IOException("TorrServer недоступний")
    }
}
