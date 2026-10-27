package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONObject

data class TorrServerCredentials(val username: String, val password: String)

/** Optional Matrix HTTP Basic credentials, encrypted separately from the Worker key. */
class TorrServerAuthStore(context: Context) {
    private val store = ProtectedValueStore(context, "torrserver_auth.enc", "animarium_torrserver_auth_v1")

    fun read(): TorrServerCredentials? = try {
        val json = JSONObject(store.read() ?: return null)
        val username = json.optString("username")
        val password = json.optString("password")
        if (username.isBlank() || password.isBlank()) null else TorrServerCredentials(username, password)
    } catch (_: Exception) { null }

    fun write(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) store.write("")
        else store.write(JSONObject().put("username", username).put("password", password).toString())
    }
}
