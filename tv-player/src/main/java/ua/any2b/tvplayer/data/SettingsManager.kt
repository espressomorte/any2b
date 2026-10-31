package ua.any2b.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ua.any2b.tvplayer.BuildConfig

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

    fun isEncryptedConfigEnabled(): Boolean = prefs.getBoolean("encrypted_config_enabled", false)

    fun setEncryptedConfigEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("encrypted_config_enabled", enabled).apply()
    }

    fun encryptedConfigUrl(): String {
        val configured = BuildConfig.ENCRYPTED_CONFIG_URL.takeIf(String::isNotBlank)
        val saved = prefs.getString("encrypted_config_url", null)?.takeIf(String::isNotBlank)
        return when {
            saved == OLD_CONFIG_URL -> configured ?: DEFAULT_CONFIG_URL
            saved != null -> saved
            else -> configured ?: DEFAULT_CONFIG_URL
        }
    }

    fun setEncryptedConfigUrl(value: String) {
        prefs.edit().putString("encrypted_config_url", value.trim()).apply()
    }

    fun isExperimentalAppearanceEnabled(): Boolean =
        prefs.getBoolean("experimental_appearance", false)

    fun setExperimentalAppearanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("experimental_appearance", enabled).apply()
    }

    fun homeSections(): List<HomeSectionSetting> = synchronized(homeSectionsLock) {
        readHomeSections("home_sections", HomeSections.defaults)
    }

    fun mainHomeSections(): List<HomeSectionSetting> = synchronized(homeSectionsLock) {
        readHomeSections("main_home_sections", MainHomeSections.defaults)
    }

    private fun readHomeSections(key: String, defaults: List<HomeSectionSetting>): List<HomeSectionSetting> {
        val stored = prefs.getString(key, null) ?: return defaults
        val saved = try {
            JSONArray(stored)
        } catch (_: Exception) {
            return defaults
        }
        val legacyAniListEnabled = key == "main_home_sections" &&
            (0 until saved.length()).any { index ->
                saved.optJSONObject(index)?.let { item ->
                    item.optString("id") == "anilist_top" && item.optBoolean("enabled", true)
                } == true
            }
        val defaultsById = defaults.associateBy { it.id }
        val result = mutableListOf<HomeSectionSetting>()
        for (index in 0 until saved.length()) {
            val item = saved.optJSONObject(index) ?: continue
            val default = defaultsById[item.optString("id")] ?: continue
            if (result.any { it.id == default.id }) continue
            result.add(default.copy(enabled = item.optBoolean("enabled", true) ||
                (default.id == "mal_top" && legacyAniListEnabled)))
        }
        return result + defaults.filterNot { default -> result.any { it.id == default.id } }
    }

    fun setHomeSectionEnabled(id: String, enabled: Boolean): List<HomeSectionSetting> =
        synchronized(homeSectionsLock) {
            val updated = homeSections().map { section ->
                if (section.id == id) section.copy(enabled = enabled) else section
            }
            saveHomeSections("home_sections", updated)
            updated
        }

    fun moveHomeSection(id: String, offset: Int): List<HomeSectionSetting> =
        synchronized(homeSectionsLock) {
            val updated = homeSections().toMutableList()
            val index = updated.indexOfFirst { it.id == id }
            val target = index + offset
            if (index >= 0 && target in updated.indices) {
                java.util.Collections.swap(updated, index, target)
                saveHomeSections("home_sections", updated)
            }
            updated
        }

    fun setMainHomeSectionEnabled(id: String, enabled: Boolean): List<HomeSectionSetting> =
        synchronized(homeSectionsLock) {
            val updated = mainHomeSections().map { section ->
                if (section.id == id) section.copy(enabled = enabled) else section
            }
            saveHomeSections("main_home_sections", updated)
            updated
        }

    fun moveMainHomeSection(id: String, offset: Int): List<HomeSectionSetting> =
        synchronized(homeSectionsLock) {
            val updated = mainHomeSections().toMutableList()
            val index = updated.indexOfFirst { it.id == id }
            val target = index + offset
            if (index >= 0 && target in updated.indices) {
                java.util.Collections.swap(updated, index, target)
                saveHomeSections("main_home_sections", updated)
            }
            updated
        }

    private fun saveHomeSections(key: String, sections: List<HomeSectionSetting>) {
        val array = JSONArray()
        sections.forEach { section ->
            array.put(JSONObject().put("id", section.id).put("enabled", section.enabled))
        }
        prefs.edit().putString(key, array.toString()).apply()
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
        const val OLD_CONFIG_URL =
            "https://gist.githubusercontent.com/espressomorte/62c423c03f3f2d54a3a8721b37b0183f/raw/9c1527655734c04e2f6f2f72c116d4a657bb3003/config.txt"
        const val DEFAULT_CONFIG_URL = "https://gist.githubusercontent.com/espressomorte/5d12b0ed49ed9d1726896405b3d87a22/raw/d105432385f05f81cfd62ca6cc437a57c3d8a358/config.txt"
    }
}
