package com.starkbrowser.app.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.starkbrowser.app.model.BrowserTab
import com.starkbrowser.app.settings.BrowserSettings

/**
 * Manages WebView instances for browser tabs.
 * Caps active WebViews to conserve memory on low-end devices.
 * Inactive tabs beyond the cap are destroyed; their state is saved
 * and restored from URL when switched back.
 */
class TabManager(private val context: Context) {

    companion object {
        private const val MAX_LIVE_WEBVIEWS = 5
    }

    // All tab metadata (lightweight)
    private val _tabs = mutableListOf<BrowserTab>()
    val tabs: List<BrowserTab> get() = _tabs.toList()

    // Live WebView instances (bounded)
    private val liveWebViews = LinkedHashMap<String, WebView>(MAX_LIVE_WEBVIEWS, 0.75f, true)

    var activeTabId: String? = null
        private set

    private var lastClosedTab: BrowserTab? = null
    private var lastClosedUrl: String? = null

    // --- Tab creation ---

    fun createTab(url: String = "about:home", isIncognito: Boolean = false): BrowserTab {
        val tab = BrowserTab(url = url, isIncognito = isIncognito)
        _tabs.add(tab)
        return tab
    }

    fun switchToTab(tabId: String) {
        activeTabId = tabId
    }

    fun activeTab(): BrowserTab? = _tabs.find { it.id == activeTabId }

    fun updateTab(tabId: String, update: BrowserTab.() -> BrowserTab) {
        val idx = _tabs.indexOfFirst { it.id == tabId }
        if (idx != -1) _tabs[idx] = _tabs[idx].update()
    }

    // --- WebView management ---

    fun getOrCreateWebView(
        tabId: String,
        settings: BrowserSettings,
        onWebViewCreated: (WebView) -> Unit
    ): WebView {
        liveWebViews[tabId]?.let { return it }

        // Evict oldest if at cap
        if (liveWebViews.size >= MAX_LIVE_WEBVIEWS) {
            val oldest = liveWebViews.entries.first()
            oldest.value.let { webView ->
                // Save URL before destroying
                val savedUrl = webView.url
                val tab = _tabs.find { it.id == oldest.key }
                if (tab != null && savedUrl != null) {
                    val idx = _tabs.indexOf(tab)
                    if (idx != -1) _tabs[idx] = tab.copy(url = savedUrl)
                }
                webView.stopLoading()
                webView.destroy()
            }
            liveWebViews.remove(oldest.key)
        }

        val webView = WebView(context).also { wv ->
            configureWebView(wv, settings)
            onWebViewCreated(wv)

            // Load current tab URL
            val tab = _tabs.find { it.id == tabId }
            val url = tab?.url ?: "about:home"
            if (url == "about:home" || url.isBlank()) {
                wv.loadDataWithBaseURL("about:home", buildHomePage(), "text/html", "UTF-8", "about:home")
            } else {
                wv.loadUrl(url)
            }
        }
        liveWebViews[tabId] = webView
        return webView
    }

    fun getWebView(tabId: String): WebView? = liveWebViews[tabId]
    fun activeWebView(): WebView? = activeTabId?.let { liveWebViews[it] }

    fun configureWebView(webView: WebView, settings: BrowserSettings) {
        webView.settings.apply {
            javaScriptEnabled = settings.javascriptEnabled
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowContentAccess = true
            allowFileAccess = true
            textZoom = settings.zoomLevel
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // Block images in data saver mode
            blockNetworkImage = settings.dataSaver

            // Desktop mode UA
            if (settings.desktopMode) {
                userAgentString = DESKTOP_UA
            } else {
                userAgentString = null // reset to default
            }
        }

        // Cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(settings.cookiesEnabled)
            setAcceptThirdPartyCookies(webView, settings.cookiesEnabled)
        }

        // Do Not Track header handled via WebViewClient intercepting
        webView.isHapticFeedbackEnabled = false // minor perf saving
    }

    fun applySettingsToAll(settings: BrowserSettings) {
        liveWebViews.values.forEach { configureWebView(it, settings) }
    }

    // --- Close / restore ---

    fun closeTab(tabId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        val webView = liveWebViews.remove(tabId)
        lastClosedTab = tab.copy(url = webView?.url ?: tab.url)
        lastClosedUrl = webView?.url ?: tab.url
        webView?.destroy()
        _tabs.remove(tab)

        // If we closed the active tab, switch to another
        if (activeTabId == tabId) {
            activeTabId = _tabs.lastOrNull()?.id
        }
    }

    fun restoreLastClosedTab(): BrowserTab? {
        val restored = lastClosedTab ?: return null
        _tabs.add(restored)
        lastClosedTab = null
        return restored
    }

    fun closeAllTabs() {
        liveWebViews.values.forEach { it.stopLoading(); it.destroy() }
        liveWebViews.clear()
        _tabs.clear()
        activeTabId = null
        lastClosedTab = null
    }

    // --- Lifecycle ---

    fun onPause() {
        liveWebViews.values.forEach { it.onPause() }
    }

    fun onResume() {
        liveWebViews.values.forEach { it.onResume() }
    }

    fun onLowMemory() {
        // Destroy all but the active WebView
        val active = activeTabId
        val toDestroy = liveWebViews.keys.filter { it != active }
        toDestroy.forEach { id ->
            val wv = liveWebViews.remove(id)
            val savedUrl = wv?.url
            val tab = _tabs.find { it.id == id }
            if (tab != null && savedUrl != null) {
                val idx = _tabs.indexOf(tab)
                if (idx != -1) _tabs[idx] = tab.copy(url = savedUrl)
            }
            wv?.destroy()
        }
    }

    fun destroyAll() {
        // Save URLs before destroying
        liveWebViews.forEach { (id, wv) ->
            val savedUrl = wv.url
            val tab = _tabs.find { it.id == id }
            if (tab != null && savedUrl != null) {
                val idx = _tabs.indexOf(tab)
                if (idx != -1) _tabs[idx] = tab.copy(url = savedUrl)
            }
            wv.stopLoading()
            wv.destroy()
        }
        liveWebViews.clear()
    }

    fun buildHomePage(): String = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta charset="utf-8">
        <title>Stark Home</title>
        <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, sans-serif; background: #1c1c1e; color: #e8e8e8;
               display: flex; flex-direction: column; align-items: center;
               padding: 48px 20px 20px; min-height: 100vh; }
        .logo { font-size: 42px; font-weight: 800; color: #1a73e8; letter-spacing: -1px;
                margin-bottom: 32px; }
        .logo span { color: #e8e8e8; }
        .search-bar { width: 100%; max-width: 480px; height: 48px;
                      background: #2c2c2e; border: none; border-radius: 24px;
                      padding: 0 20px; font-size: 16px; color: #e8e8e8;
                      outline: none; margin-bottom: 32px; }
        .search-bar::placeholder { color: #888; }
        .section-title { align-self: flex-start; font-size: 13px; color: #888;
                         text-transform: uppercase; letter-spacing: 0.5px;
                         margin-bottom: 8px; margin-top: 8px; width: 100%; max-width: 480px; }
        .shortcuts { display: grid; grid-template-columns: repeat(4, 1fr);
                     gap: 12px; width: 100%; max-width: 480px; margin-bottom: 24px; }
        .shortcut { display: flex; flex-direction: column; align-items: center;
                    padding: 12px 8px; background: #2c2c2e; border-radius: 12px;
                    text-decoration: none; color: #e8e8e8; font-size: 12px; gap: 6px; }
        .shortcut .icon { font-size: 24px; }
        </style></head><body>
        <div class="logo">Stark<span>Browser</span></div>
        <input class="search-bar" type="text" placeholder="Search or enter URL..."
               onkeydown="if(event.key==='Enter'){stark_search(this.value)}"
               autofocus />
        <div class="section-title">Quick access</div>
        <div class="shortcuts">
          <a class="shortcut" href="https://www.google.com"><span class="icon">🔍</span>Google</a>
          <a class="shortcut" href="https://www.youtube.com"><span class="icon">▶️</span>YouTube</a>
          <a class="shortcut" href="https://www.reddit.com"><span class="icon">💬</span>Reddit</a>
          <a class="shortcut" href="https://www.wikipedia.org"><span class="icon">📖</span>Wikipedia</a>
          <a class="shortcut" href="https://www.github.com"><span class="icon">💻</span>GitHub</a>
          <a class="shortcut" href="https://www.twitter.com"><span class="icon">🐦</span>Twitter</a>
          <a class="shortcut" href="https://news.ycombinator.com"><span class="icon">📰</span>HN</a>
          <a class="shortcut" href="https://www.amazon.com"><span class="icon">🛒</span>Amazon</a>
        </div>
        <script>
        function stark_search(q) {
            if(!q) return;
            if(q.match(/^https?:\/\//)) { location.href=q; return; }
            if(q.match(/^[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(\/.*)?$/)) { location.href='https://'+q; return; }
            location.href='https://www.google.com/search?q='+encodeURIComponent(q);
        }
        document.querySelector('.search-bar').addEventListener('keydown', function(e){
            if(e.key==='Enter') stark_search(this.value);
        });
        </script></body></html>
    """.trimIndent()

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
