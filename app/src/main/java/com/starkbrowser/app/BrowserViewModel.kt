package com.starkbrowser.app

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starkbrowser.app.browser.ContentBlocker
import com.starkbrowser.app.browser.TabManager
import com.starkbrowser.app.data.AppDatabase
import com.starkbrowser.app.data.Bookmark
import com.starkbrowser.app.data.DownloadItem
import com.starkbrowser.app.data.HistoryItem
import com.starkbrowser.app.model.BrowserTab
import com.starkbrowser.app.settings.BrowserSettings
import com.starkbrowser.app.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val settingsStore = SettingsDataStore(application)
    val contentBlocker = ContentBlocker(application)

    val tabManager = TabManager(application)

    // Settings
    val settings: StateFlow<BrowserSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    // Bookmarks / History / Downloads as flows
    val bookmarks = db.bookmarkDao().getAllFlow()
    val history = db.historyDao().getAllFlow()
    val downloads = db.downloadDao().getAllFlow()

    // UI state
    private val _activeTabState = MutableStateFlow<BrowserTab?>(null)
    val activeTabState: StateFlow<BrowserTab?> = _activeTabState.asStateFlow()

    private val _tabsState = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabsState: StateFlow<List<BrowserTab>> = _tabsState.asStateFlow()

    init {
        // Create an initial tab
        val initial = tabManager.createTab("about:home")
        tabManager.switchToTab(initial.id)
        refreshTabState()
    }

    fun refreshTabState() {
        _tabsState.value = tabManager.tabs
        _activeTabState.value = tabManager.activeTab()
    }

    fun createTab(url: String = "about:home", incognito: Boolean = false): BrowserTab {
        val tab = tabManager.createTab(url, incognito)
        tabManager.switchToTab(tab.id)
        refreshTabState()
        return tab
    }

    fun switchTab(tabId: String) {
        tabManager.switchToTab(tabId)
        refreshTabState()
    }

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
        if (tabManager.tabs.isEmpty()) {
            val newTab = tabManager.createTab("about:home")
            tabManager.switchToTab(newTab.id)
        }
        refreshTabState()
    }

    fun closeAllTabs() {
        tabManager.closeAllTabs()
        val newTab = tabManager.createTab("about:home")
        tabManager.switchToTab(newTab.id)
        refreshTabState()
    }

    fun restoreLastTab(): BrowserTab? {
        val restored = tabManager.restoreLastClosedTab() ?: return null
        tabManager.switchToTab(restored.id)
        refreshTabState()
        return restored
    }

    fun updateActiveTabMeta(tab: BrowserTab) {
        tabManager.updateTab(tab.id) { tab }
        refreshTabState()
    }

    // --- Bookmarks ---
    fun addBookmark(title: String, url: String) = viewModelScope.launch(Dispatchers.IO) {
        db.bookmarkDao().insert(Bookmark(title = title, url = url))
    }

    fun removeBookmarkByUrl(url: String) = viewModelScope.launch(Dispatchers.IO) {
        db.bookmarkDao().deleteByUrl(url)
    }

    suspend fun isBookmarked(url: String): Boolean {
        return db.bookmarkDao().isBookmarked(url) > 0
    }

    // --- History ---
    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url == "about:home" || url.startsWith("data:")) return
        viewModelScope.launch(Dispatchers.IO) {
            db.historyDao().insert(HistoryItem(title = title, url = url))
        }
    }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) {
        db.historyDao().deleteAll()
    }

    fun deleteHistoryItem(item: HistoryItem) = viewModelScope.launch(Dispatchers.IO) {
        db.historyDao().delete(item)
    }

    // --- Downloads ---
    fun startDownload(context: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val filename = extractFilename(url, contentDisposition, mimeType)
        val mime = mimeType ?: getMimeFromUrl(url) ?: "application/octet-stream"

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription("Downloading via Stark Browser")
            setMimeType(mime)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            userAgent?.let { addRequestHeader("User-Agent", it) }
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        viewModelScope.launch(Dispatchers.IO) {
            db.downloadDao().insert(
                DownloadItem(
                    filename = filename,
                    url = url,
                    mimeType = mime,
                    downloadId = downloadId,
                    status = DownloadItem.STATUS_RUNNING
                )
            )
        }
    }

    fun deleteDownload(item: DownloadItem) = viewModelScope.launch(Dispatchers.IO) {
        db.downloadDao().deleteById(item.id)
    }

    // --- Settings passthrough ---
    fun setTheme(v: Int) = viewModelScope.launch { settingsStore.setTheme(v) }
    fun setSearchEngine(v: Int) = viewModelScope.launch { settingsStore.setSearchEngine(v) }
    fun setJavascript(v: Boolean) = viewModelScope.launch { settingsStore.setJavascript(v) }
    fun setCookies(v: Boolean) = viewModelScope.launch { settingsStore.setCookies(v) }
    fun setContentBlocking(v: Boolean) = viewModelScope.launch { settingsStore.setContentBlocking(v) }
    fun setDoNotTrack(v: Boolean) = viewModelScope.launch { settingsStore.setDoNotTrack(v) }
    fun setDataSaver(v: Boolean) = viewModelScope.launch { settingsStore.setDataSaver(v) }
    fun setHomepage(v: String) = viewModelScope.launch { settingsStore.setHomepage(v) }
    fun setZoomLevel(v: Int) = viewModelScope.launch { settingsStore.setZoomLevel(v) }

    fun clearAllBrowsingData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            db.historyDao().deleteAll()
        }
        android.webkit.WebStorage.getInstance().deleteAllData()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        context.cacheDir.deleteRecursively()
    }

    override fun onCleared() {
        super.onCleared()
        tabManager.destroyAll()
    }

    // --- Helpers ---
    private fun extractFilename(url: String, contentDisposition: String?, mimeType: String?): String {
        // Try content-disposition
        contentDisposition?.let { cd ->
            val match = Regex("filename\\*?=['\"]?([^'\"\\s;]+)").find(cd)
            match?.groupValues?.get(1)?.let { return it }
        }
        // Try URL
        Uri.parse(url).lastPathSegment?.let { seg ->
            if (seg.contains('.')) return seg
        }
        // Fallback with extension from mime
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun getMimeFromUrl(url: String): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url) ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
