package ua.any2b.core.toloka

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

data class TorrServerFile(val id: Int, val path: String, val length: Long) {
    val name: String get() = path.substringAfterLast('/')
}

data class TorrServerBasicAuth(val username: String, val password: String)

data class TorrServerTorrent(val hash: String, val files: List<TorrServerFile>) {
    val videoFiles: List<TorrServerFile> get() = files.filter { file ->
        file.name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }.sortedBy { it.id }

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv", "flv")
    }
}

sealed class TorrServerFailure(message: String) : IOException(message) {
    class NeedsAuthorization : TorrServerFailure("TorrServer потребує Basic авторизації")
    class Http(val status: Int) : TorrServerFailure("TorrServer відповів HTTP $status")
    class InvalidResponse : TorrServerFailure("TorrServer повернув неочікувані дані")
    class FilesUnavailable : TorrServerFailure("TorrServer ще не повернув список файлів")
    class Timeout : TorrServerFailure("Час очікування TorrServer вичерпано")
    class Network : TorrServerFailure("Не вдалося з’єднатися з TorrServer")
}

/** Matrix API: multipart POST /torrent/upload, then POST /torrents {action:get, hash}. */
class TorrServerClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    suspend fun upload(baseUrl: String, torrentBytes: ByteArray, auth: TorrServerBasicAuth? = null): TorrServerTorrent {
        require(torrentBytes.isNotEmpty()) { "Порожній torrent-файл" }
        val base = validatedBase(baseUrl)
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "upload.torrent", torrentBytes.toRequestBody("application/x-bittorrent".toMediaType()))
            .build()
        return execute(withAuth(Request.Builder()
            .url(base.newBuilder().addPathSegments("torrent/upload").build())
            .post(multipart), auth).build()).use { response -> parseStatus(response.body?.string().orEmpty()) }
    }

    suspend fun get(baseUrl: String, hash: String, auth: TorrServerBasicAuth? = null): TorrServerTorrent {
        require(hash.matches(Regex("[0-9a-fA-F]{40}"))) { "Некоректний hash торента" }
        val base = validatedBase(baseUrl)
        val body = JSONObject().put("action", "get").put("hash", hash)
            .toString().toRequestBody("application/json".toMediaType())
        return execute(withAuth(Request.Builder().url(base.newBuilder().addPathSegment("torrents").build())
            .post(body), auth).build()).use { response -> parseStatus(response.body?.string().orEmpty()) }
    }

    fun playbackUrl(baseUrl: String, torrent: TorrServerTorrent, file: TorrServerFile): String {
        require(file in torrent.videoFiles) { "Вибраний файл не є відео з цього торента" }
        require(torrent.hash.matches(Regex("[0-9a-fA-F]{40}"))) { "Некоректний hash торента" }
        return validatedBase(baseUrl).newBuilder().addPathSegment("stream")
            .addPathSegment(file.name)
            .addQueryParameter("link", torrent.hash)
            .addQueryParameter("index", file.id.toString())
            .addQueryParameter("play", null)
            .build().toString()
    }

    private fun parseStatus(body: String): TorrServerTorrent {
        val json = try {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                val list = JSONArray(trimmed)
                if (list.length() != 1) throw TorrServerFailure.InvalidResponse()
                list.getJSONObject(0)
            } else JSONObject(trimmed)
        } catch (_: Exception) { throw TorrServerFailure.InvalidResponse() }
        val hash = json.optString("hash")
        if (!hash.matches(Regex("[0-9a-fA-F]{40}"))) throw TorrServerFailure.InvalidResponse()
        val array = json.optJSONArray("file_stats")
        val files = if (array == null) emptyList() else (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw TorrServerFailure.InvalidResponse()
            if (!item.has("id") || !item.has("path") || !item.has("length")) {
                throw TorrServerFailure.InvalidResponse()
            }
            TorrServerFile(item.getInt("id"), item.getString("path"), item.getLong("length"))
                .also { if (it.id <= 0 || it.path.isBlank() || it.length < 0) throw TorrServerFailure.InvalidResponse() }
        }
        return TorrServerTorrent(hash, files)
    }

    private fun validatedBase(value: String) = value.trim().trimEnd('/').toHttpUrlOrNull()?.also {
        require((it.scheme == "http" || it.scheme == "https") && it.username.isEmpty() &&
            it.password.isEmpty() && it.query == null && it.fragment == null) {
            "Некоректна адреса TorrServer"
        }
    } ?: throw IllegalArgumentException("Некоректна адреса TorrServer")

    private fun withAuth(builder: Request.Builder, auth: TorrServerBasicAuth?): Request.Builder =
        if (auth == null) builder else builder.header("Authorization", Credentials.basic(auth.username, auth.password))

    private suspend fun execute(request: Request): Response {
        val call = http.newCall(request)
        val response = try {
            suspendCancellableCoroutine<Response> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                })
            }
        } catch (_: SocketTimeoutException) { throw TorrServerFailure.Timeout() }
        catch (_: IOException) { throw TorrServerFailure.Network() }
        if (response.isSuccessful) return response
        response.use {
            if (it.code == 401) throw TorrServerFailure.NeedsAuthorization()
            throw TorrServerFailure.Http(it.code)
        }
    }
}
