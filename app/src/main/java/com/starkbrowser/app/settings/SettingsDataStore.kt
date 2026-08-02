package com.starkbrowser.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stark_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_THEME = intPreferencesKey("theme")          // 0=System 1=Light 2=Dark
        val KEY_SEARCH_ENGINE = intPreferencesKey("search_engine") // 0=Google 1=Bing 2=DDG 3=Brave
        val KEY_JAVASCRIPT = booleanPreferencesKey("javascript")
        val KEY_COOKIES = booleanPreferencesKey("cookies")
        val KEY_CONTENT_BLOCKING = booleanPreferencesKey("content_blocking")
        val KEY_DO_NOT_TRACK = booleanPreferencesKey("do_not_track")
        val KEY_DATA_SAVER = booleanPreferencesKey("data_saver")
        val KEY_HOMEPAGE = stringPreferencesKey("homepage")
        val KEY_DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val KEY_ZOOM_LEVEL = intPreferencesKey("zoom_level")  // text zoom %

        val SEARCH_ENGINES = listOf(
            "https://www.google.com/search?q=",
            "https://www.bing.com/search?q=",
            "https://duckduckgo.com/?q=",
            "https://search.brave.com/search?q="
        )
        val SEARCH_ENGINE_NAMES = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")

        const val DEFAULT_HOMEPAGE = "about:home"
        const val DEFAULT_ZOOM = 100
    }

    val settingsFlow: Flow<BrowserSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            BrowserSettings(
                theme = prefs[KEY_THEME] ?: 0,
                searchEngine = prefs[KEY_SEARCH_ENGINE] ?: 0,
                javascriptEnabled = prefs[KEY_JAVASCRIPT] ?: true,
                cookiesEnabled = prefs[KEY_COOKIES] ?: true,
                contentBlockingEnabled = prefs[KEY_CONTENT_BLOCKING] ?: false,
                doNotTrack = prefs[KEY_DO_NOT_TRACK] ?: false,
                dataSaver = prefs[KEY_DATA_SAVER] ?: false,
                homepage = prefs[KEY_HOMEPAGE] ?: DEFAULT_HOMEPAGE,
                desktopMode = prefs[KEY_DESKTOP_MODE] ?: false,
                zoomLevel = prefs[KEY_ZOOM_LEVEL] ?: DEFAULT_ZOOM
            )
        }

    suspend fun setTheme(value: Int) = context.dataStore.edit { it[KEY_THEME] = value }
    suspend fun setSearchEngine(value: Int) = context.dataStore.edit { it[KEY_SEARCH_ENGINE] = value }
    suspend fun setJavascript(value: Boolean) = context.dataStore.edit { it[KEY_JAVASCRIPT] = value }
    suspend fun setCookies(value: Boolean) = context.dataStore.edit { it[KEY_COOKIES] = value }
    suspend fun setContentBlocking(value: Boolean) = context.dataStore.edit { it[KEY_CONTENT_BLOCKING] = value }
    suspend fun setDoNotTrack(value: Boolean) = context.dataStore.edit { it[KEY_DO_NOT_TRACK] = value }
    suspend fun setDataSaver(value: Boolean) = context.dataStore.edit { it[KEY_DATA_SAVER] = value }
    suspend fun setHomepage(value: String) = context.dataStore.edit { it[KEY_HOMEPAGE] = value }
    suspend fun setDesktopMode(value: Boolean) = context.dataStore.edit { it[KEY_DESKTOP_MODE] = value }
    suspend fun setZoomLevel(value: Int) = context.dataStore.edit { it[KEY_ZOOM_LEVEL] = value }
}

data class BrowserSettings(
    val theme: Int = 0,
    val searchEngine: Int = 0,
    val javascriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val contentBlockingEnabled: Boolean = false,
    val doNotTrack: Boolean = false,
    val dataSaver: Boolean = false,
    val homepage: String = SettingsDataStore.DEFAULT_HOMEPAGE,
    val desktopMode: Boolean = false,
    val zoomLevel: Int = SettingsDataStore.DEFAULT_ZOOM
) {
    fun searchUrl(query: String): String {
        val base = SettingsDataStore.SEARCH_ENGINES.getOrElse(searchEngine) { SettingsDataStore.SEARCH_ENGINES[0] }
        return base + android.net.Uri.encode(query)
    }
}
