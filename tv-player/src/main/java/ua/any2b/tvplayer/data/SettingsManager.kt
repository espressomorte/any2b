package ua.any2b.tvplayer.data

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    fun isAutoPlayNextEnabled(): Boolean {
        return prefs.getBoolean("auto_play_next", false)
    }

    fun setAutoPlayNextEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_play_next", enabled).apply()
    }

    fun selectedPlayerPackage(): String? = prefs.getString("player_package", null)

    fun setSelectedPlayerPackage(packageName: String?) {
        prefs.edit().apply {
            if (packageName == null) remove("player_package") else putString("player_package", packageName)
        }.apply()
    }
}
