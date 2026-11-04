package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONObject

data class TolokaCredentials(val username: String, val password: String)

class TolokaCredentialStore(context: Context) {
    private val delegate = ProtectedValueStore(context, "toloka_credentials.enc", "animarium_toloka_credentials_v1")

    fun read(): TolokaCredentials? = try {
        val json = JSONObject(delegate.read() ?: return null)
        val user = json.optString("username")
        val password = json.optString("password")
        if (user.isBlank() || password.isBlank()) null else TolokaCredentials(user, password)
    } catch (_: Exception) { null }

    fun write(user: String, password: String) {
        require(user.isNotBlank() && password.isNotBlank())
        delegate.write(JSONObject().put("username", user).put("password", password).toString())
    }
}
