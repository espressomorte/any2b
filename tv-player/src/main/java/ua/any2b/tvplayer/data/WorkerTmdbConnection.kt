package ua.any2b.tvplayer.data

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ua.any2b.core.tmdb.TmdbClient

data class WorkerTmdbConnection(val client: TmdbClient, val accessKey: String)

fun workerTmdbConnection(context: Context): WorkerTmdbConnection? {
    if (SettingsManager(context).isEncryptedConfigEnabled()) {
        val token = TmdbKeyStore(context).read()?.takeIf(String::isNotBlank) ?: return null
        return WorkerTmdbConnection(TmdbClient(), token)
    }
    val url = SettingsManager(context).workerUrl().trimEnd('/')
    val key = WorkerKeyStore(context).read()?.takeIf(String::isNotBlank)
    val base = "$url/tmdb/3/".toHttpUrlOrNull()?.takeIf { it.isHttps }
    if (key != null && base != null) return WorkerTmdbConnection(TmdbClient(baseUrl = base), key)
    return null
}
