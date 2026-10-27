package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    fun workerUrl(): String = prefs.getString("worker_url", "").orEmpty()

    fun setWorkerUrl(value: String) {
        prefs.edit().putString("worker_url", value.trim().trimEnd('/')).apply()
    }

    fun torrServerUrl(): String = prefs.getString("torrserver_url", "").orEmpty()

    fun setTorrServerUrl(value: String) {
        prefs.edit().putString("torrserver_url", value.trim().trimEnd('/')).apply()
    }

    fun homeSections(): List<HomeSectionSetting> = synchronized(homeSectionsLock) {
        val stored = prefs.getString("home_sections", null) ?: return@synchronized HomeSections.defaults
        val saved = try {
            JSONArray(stored)
        } catch (_: Exception) {
            return@synchronized HomeSections.defaults
        }
        val defaultsById = HomeSections.defaults.associateBy { it.id }
        val result = mutableListOf<HomeSectionSetting>()
        for (index in 0 until saved.length()) {
            val item = saved.optJSONObject(index) ?: continue
            val default = defaultsById[item.optString("id")] ?: continue
            if (result.any { it.id == default.id }) continue
            result.add(default.copy(enabled = item.optBoolean("enabled", true)))
        }
        result + HomeSections.defaults.filterNot { default -> result.any { it.id == default.id } }
    }

    fun setHomeSectionEnabled(id: String, enabled: Boolean): List<HomeSectionSetting> =
        synchronized(homeSectionsLock) {
            val updated = homeSections().map { section ->
                if (section.id == id) section.copy(enabled = enabled) else section
            }
            saveHomeSections(updated)
            updated
        }

    fun moveHomeSection(id: String, offset: Int): List<HomeSectionSetting> =
        synchronized(homeSectionsLock) {
            val updated = homeSections().toMutableList()
            val index = updated.indexOfFirst { it.id == id }
            val target = index + offset
            if (index >= 0 && target in updated.indices) {
                java.util.Collections.swap(updated, index, target)
                saveHomeSections(updated)
            }
            updated
        }

    private fun saveHomeSections(sections: List<HomeSectionSetting>) {
        val array = JSONArray()
        sections.forEach { section ->
            array.put(JSONObject().put("id", section.id).put("enabled", section.enabled))
        }
        prefs.edit().putString("home_sections", array.toString()).apply()
    }

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

    private companion object {
        val homeSectionsLock = Any()
    }
}
