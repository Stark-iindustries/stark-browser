package com.starkbrowser.app

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.starkbrowser.app.browser.ContentBlocker
import com.starkbrowser.app.browser.StarkWebChromeClient
import com.starkbrowser.app.browser.StarkWebViewClient
import com.starkbrowser.app.databinding.ActivityMainBinding
import com.starkbrowser.app.model.BrowserTab
import com.starkbrowser.app.settings.BrowserSettings
import com.starkbrowser.app.settings.SettingsDataStore
import com.starkbrowser.app.ui.BookmarksActivity
import com.starkbrowser.app.ui.DownloadsActivity
import com.starkbrowser.app.ui.HistoryActivity
import com.starkbrowser.app.ui.TabSwitcherActivity
import com.starkbrowser.app.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()

    private var currentSettings = BrowserSettings()
    private var chromeClient: StarkWebChromeClient? = null
    private var fullscreenView: View? = null
    private var isDesktopMode = false
    private var findInPageActive = false

    // File chooser launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        chromeClient?.onFileChooserResult(result.data)
    }

    // Tab switcher launcher
    private val tabSwitcherLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val tabId = result.data?.getStringExtra(TabSwitcherActivity.EXTRA_TAB_ID)
            if (tabId != null) {
                viewModel.switchTab(tabId)
                attachActiveWebView()
            } else if (result.data?.getBooleanExtra(TabSwitcherActivity.EXTRA_NEW_TAB, false) == true) {
                val incognito = result.data?.getBooleanExtra(TabSwitcherActivity.EXTRA_INCOGNITO, false) ?: false
                openNewTab(incognito = incognito)
            }
        }
    }

    // Bookmarks / History launcher — opens URL if selected
    private val browserActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val url = result.data?.getStringExtra(EXTRA_OPEN_URL)
            if (url != null) navigateTo(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomBar()
        setupFindInPage()
        observeSettings()
        observeTabState()
        handleIncomingIntent(intent)

        // Back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    findInPageActive -> closeFindInPage()
                    fullscreenView != null -> exitFullscreen()
                    activeWebView()?.canGoBack() == true -> activeWebView()?.goBack()
                    viewModel.tabManager.tabs.size > 1 -> {
                        val currentId = viewModel.tabManager.activeTabId
                        if (currentId != null) viewModel.closeTab(currentId)
                        attachActiveWebView()
                    }
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(android.app.SearchManager.QUERY)?.let {
                currentSettings.searchUrl(it)
            }
            else -> null
        }
        if (url != null) navigateTo(url)
    }

    // --- Settings observation ---

    private fun observeSettings() {
        lifecycleScope.launch {
            viewModel.settings.collectLatest { settings ->
                currentSettings = settings
                applyTheme(settings.theme)
                viewModel.tabManager.applySettingsToAll(settings)
                isDesktopMode = settings.desktopMode
                activeWebView()?.let {
                    viewModel.tabManager.configureWebView(it, settings)
                }
            }
        }
    }

    private fun applyTheme(theme: Int) {
        val mode = when (theme) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // --- Tab state observation ---

    private fun observeTabState() {
        lifecycleScope.launch {
            viewModel.activeTabState.collectLatest { tab ->
                tab ?: return@collectLatest
                updateBottomBarForTab(tab)
            }
        }
        lifecycleScope.launch {
            viewModel.tabsState.collectLatest { tabs ->
                binding.btnTabs.text = tabs.size.toString()
            }
        }
    }

    // --- WebView management ---

    private fun attachActiveWebView() {
        val tab = viewModel.tabManager.activeTab() ?: return
        binding.webViewContainer.removeAllViews()

        val webView = viewModel.tabManager.getOrCreateWebView(
            tab.id, currentSettings
        ) { newWebView ->
            setupWebViewClients(newWebView, tab)
        }

        // Re-attach clients if WebView was reused
        if (webView.webViewClient !is StarkWebViewClient) {
            setupWebViewClients(webView, tab)
        }

        binding.webViewContainer.addView(webView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Update incognito styling
        if (tab.isIncognito) {
            binding.bottomBar.setBackgroundColor(getColor(R.color.stark_incognito))
        } else {
            binding.bottomBar.setBackgroundColor(
                resolveAttrColor(com.google.android.material.R.attr.colorSurface)
            )
        }

        updateBottomBarForTab(tab)
    }

    private fun setupWebViewClients(webView: WebView, tab: BrowserTab) {
        val client = StarkWebViewClient(
            context = this,
            contentBlocker = viewModel.contentBlocker,
            contentBlockingEnabled = currentSettings.contentBlockingEnabled,
            onPageStarted = { url, _ ->
                runOnUiThread { onPageStarted(tab.id, url) }
            },
            onPageFinished = { url, title ->
                runOnUiThread { onPageFinished(tab.id, url, title) }
            },
            onReceivedError = { url ->
                runOnUiThread {
                    viewModel.updateActiveTabMeta(
                        viewModel.tabManager.activeTab()?.copy(isLoading = false) ?: return@runOnUiThread
                    )
                    updateProgressBar(0, false)
                }
            }
        )

        val chrome = StarkWebChromeClient(
            activity = this,
            onProgressChanged = { progress ->
                runOnUiThread { updateProgressBar(progress, progress < 100) }
            },
            onTitleReceived = { title ->
                runOnUiThread {
                    viewModel.updateActiveTabMeta(
                        viewModel.tabManager.activeTab()?.copy(title = title) ?: return@runOnUiThread
                    )
                }
            },
            onFaviconReceived = { _ -> },
            onFullscreenEnter = { view -> enterFullscreen(view) },
            onFullscreenExit = { exitFullscreen() },
            onOpenNewTab = { url -> runOnUiThread { openNewTab(url) } },
            fileChooserLauncher = fileChooserLauncher
        ).also { chromeClient = it }

        webView.webViewClient = client
        webView.webChromeClient = chrome

        // Download listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            viewModel.startDownload(this, url, userAgent, contentDisposition, mimetype)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPageStarted(tabId: String, url: String) {
        if (tabId != viewModel.tabManager.activeTabId) return
        viewModel.updateActiveTabMeta(
            viewModel.tabManager.activeTab()?.copy(
                url = url, isLoading = true,
                canGoBack = activeWebView()?.canGoBack() ?: false,
                canGoForward = activeWebView()?.canGoForward() ?: false
            ) ?: return
        )
        if (!url.startsWith("data:") && url != "about:home") {
            binding.urlBar.setText(url)
        }
        binding.lockIcon.isVisible = url.startsWith("https://")
        updateNavButtons()
    }

    private fun onPageFinished(tabId: String, url: String, title: String?) {
        if (tabId != viewModel.tabManager.activeTabId) return
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: url
        viewModel.updateActiveTabMeta(
            viewModel.tabManager.activeTab()?.copy(
                url = url,
                title = safeTitle,
                isLoading = false,
                canGoBack = activeWebView()?.canGoBack() ?: false,
                canGoForward = activeWebView()?.canGoForward() ?: false
            ) ?: return
        )
        if (!url.startsWith("data:") && url != "about:home") {
            binding.urlBar.setText(url)
            viewModel.addHistory(safeTitle, url)
        }
        binding.lockIcon.isVisible = url.startsWith("https://")
        updateProgressBar(100, false)
        updateNavButtons()
    }

    private fun activeWebView(): WebView? = viewModel.tabManager.activeWebView()

    // --- Bottom bar setup ---

    private fun setupBottomBar() {
        binding.btnBack.setOnClickListener {
            if (activeWebView()?.canGoBack() == true) activeWebView()?.goBack()
        }
        binding.btnForward.setOnClickListener {
            if (activeWebView()?.canGoForward() == true) activeWebView()?.goForward()
        }
        binding.btnHome.setOnClickListener {
            loadHomepage()
        }
        binding.btnReload.setOnClickListener {
            val tab = viewModel.tabManager.activeTab()
            if (tab?.isLoading == true) {
                activeWebView()?.stopLoading()
            } else {
                activeWebView()?.reload()
            }
        }
        binding.btnTabs.setOnClickListener { openTabSwitcher() }
        binding.btnTabs.parent?.let { parent ->
            (parent as? View)?.setOnClickListener { openTabSwitcher() }
        }

        binding.btnMenu.setOnClickListener { showBrowserMenu() }

        // URL bar
        binding.urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val input = binding.urlBar.text.toString().trim()
                if (input.isNotEmpty()) navigateTo(input)
                hideKeyboard()
                true
            } else false
        }
        binding.urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.urlBar.selectAll()
        }

        // Initial attach
        attachActiveWebView()
    }

    private fun updateBottomBarForTab(tab: BrowserTab) {
        binding.btnBack.alpha = if (tab.canGoBack) 1f else 0.4f
        binding.btnForward.alpha = if (tab.canGoForward) 1f else 0.4f
        if (!binding.urlBar.isFocused) {
            val displayUrl = if (tab.url == "about:home" || tab.url.startsWith("data:")) "" else tab.url
            binding.urlBar.setText(displayUrl)
        }
        binding.lockIcon.isVisible = tab.url.startsWith("https://")
        if (tab.isLoading) {
            binding.btnReload.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            binding.btnReload.setImageResource(android.R.drawable.stat_notify_sync)
        }
    }

    private fun updateNavButtons() {
        val wv = activeWebView()
        binding.btnBack.alpha = if (wv?.canGoBack() == true) 1f else 0.4f
        binding.btnForward.alpha = if (wv?.canGoForward() == true) 1f else 0.4f
    }

    private fun updateProgressBar(progress: Int, visible: Boolean) {
        binding.progressBar.isVisible = visible
        binding.progressBar.progress = progress
    }

    // --- Navigation ---

    fun navigateTo(input: String) {
        val url = resolveUrl(input)
        val wv = activeWebView() ?: run {
            openNewTab(url)
            return
        }
        if (url == "about:home") {
            wv.loadDataWithBaseURL("about:home",
                viewModel.tabManager.buildHomePage(), "text/html", "UTF-8", "about:home")
        } else {
            wv.loadUrl(url)
        }
        binding.urlBar.clearFocus()
    }

    private fun resolveUrl(input: String): String {
        if (input.isBlank()) return "about:home"
        if (input == "about:home" || input == "about:blank") return input
        if (input.startsWith("http://") || input.startsWith("https://") ||
            input.startsWith("file://") || input.startsWith("data:")) return input
        // looks like domain?
        val domainRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z]{2,})(/.*)?\$")
        if (domainRegex.matches(input)) return "https://$input"
        // search
        return currentSettings.searchUrl(input)
    }

    private fun loadHomepage() {
        val hp = currentSettings.homepage
        if (hp == "about:home" || hp.isBlank()) {
            activeWebView()?.loadDataWithBaseURL("about:home",
                viewModel.tabManager.buildHomePage(), "text/html", "UTF-8", "about:home")
            binding.urlBar.setText("")
        } else {
            navigateTo(hp)
        }
    }

    private fun openNewTab(url: String = "about:home", incognito: Boolean = false) {
        val tab = viewModel.createTab(url, incognito)
        attachActiveWebView()
    }

    private fun openTabSwitcher() {
        val intent = Intent(this, TabSwitcherActivity::class.java).apply {
            val ids = ArrayList(viewModel.tabManager.tabs.map { it.id })
            val titles = ArrayList(viewModel.tabManager.tabs.map { it.title })
            val urls = ArrayList(viewModel.tabManager.tabs.map { it.url })
            val incognitos = ArrayList(viewModel.tabManager.tabs.map { it.isIncognito })
            putStringArrayListExtra(TabSwitcherActivity.EXTRA_TAB_IDS, ids)
            putStringArrayListExtra(TabSwitcherActivity.EXTRA_TAB_TITLES, titles)
            putStringArrayListExtra(TabSwitcherActivity.EXTRA_TAB_URLS, urls)
            putExtra(TabSwitcherActivity.EXTRA_TAB_INCOGNITOS, incognitos.toBooleanArray())
            putExtra(TabSwitcherActivity.EXTRA_ACTIVE_TAB_ID, viewModel.tabManager.activeTabId)
        }
        tabSwitcherLauncher.launch(intent)
    }

    // --- Menu ---

    private fun showBrowserMenu() {
        val popup = PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        val tab = viewModel.tabManager.activeTab()
        val wv = activeWebView()

        // Update desktop site checkmark
        popup.menu.findItem(R.id.menu_desktop_site)?.isChecked = isDesktopMode

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_tab -> openNewTab()
                R.id.menu_new_incognito -> openNewTab(incognito = true)
                R.id.menu_bookmarks -> browserActivityLauncher.launch(
                    Intent(this, BookmarksActivity::class.java))
                R.id.menu_history -> browserActivityLauncher.launch(
                    Intent(this, HistoryActivity::class.java))
                R.id.menu_downloads -> startActivity(Intent(this, DownloadsActivity::class.java))
                R.id.menu_add_bookmark -> {
                    val url = wv?.url ?: return@setOnMenuItemClickListener true
                    val title = wv.title ?: url
                    viewModel.addBookmark(title, url)
                    Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
                }
                R.id.menu_find_in_page -> showFindInPage()
                R.id.menu_share -> {
                    val url = wv?.url ?: return@setOnMenuItemClickListener true
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }.let { Intent.createChooser(it, getString(R.string.share)) })
                }
                R.id.menu_copy_url -> {
                    val url = wv?.url ?: return@setOnMenuItemClickListener true
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("URL", url))
                    Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show()
                }
                R.id.menu_desktop_site -> {
                    isDesktopMode = !isDesktopMode
                    viewModel.setDataSaver(false)
                    lifecycleScope.launch {
                        viewModel.settingsStore.setDesktopMode(isDesktopMode)
                    }
                    wv?.reload()
                }
                R.id.menu_add_to_home -> addToHomeScreen(wv?.url, wv?.title)
                R.id.menu_zoom_in -> wv?.settings?.let {
                    it.textZoom = minOf(it.textZoom + 20, 200)
                }
                R.id.menu_zoom_out -> wv?.settings?.let {
                    it.textZoom = maxOf(it.textZoom - 20, 50)
                }
                R.id.menu_clear_data -> {
                    viewModel.clearAllBrowsingData(this)
                    Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
                }
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    // --- Find in page ---

    private fun setupFindInPage() {
        binding.findCloseBtn.setOnClickListener { closeFindInPage() }
        binding.findNextBtn.setOnClickListener {
            activeWebView()?.findNext(true)
        }
        binding.findPrevBtn.setOnClickListener {
            activeWebView()?.findNext(false)
        }
        binding.findInPageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doFind(binding.findInPageInput.text.toString())
                true
            } else false
        }
        binding.findInPageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                doFind(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun showFindInPage() {
        findInPageActive = true
        binding.findInPageBar.isVisible = true
        binding.findInPageInput.requestFocus()
        showKeyboard(binding.findInPageInput)
        activeWebView()?.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            binding.findResultCount.text = if (numberOfMatches > 0) {
                getString(R.string.results_of, activeMatchOrdinal + 1, numberOfMatches)
            } else {
                if (isDoneCounting) getString(R.string.no_results) else ""
            }
        }
    }

    private fun closeFindInPage() {
        findInPageActive = false
        binding.findInPageBar.isVisible = false
        activeWebView()?.clearMatches()
        hideKeyboard()
    }

    private fun doFind(query: String) {
        if (query.isNotEmpty()) activeWebView()?.findAllAsync(query)
        else activeWebView()?.clearMatches()
    }

    // --- Fullscreen video ---

    private fun enterFullscreen(view: View) {
        fullscreenView = view
        binding.webViewContainer.addView(view, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        binding.bottomBar.isVisible = false
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        binding.webViewContainer.removeView(view)
        fullscreenView = null
        binding.bottomBar.isVisible = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        chromeClient?.let { }
    }

    // --- Add to home screen ---

    private fun addToHomeScreen(url: String?, title: String?) {
        if (url == null) return
        val shortcutIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, title ?: url)
        }
        sendBroadcast(addIntent)
        Toast.makeText(this, "Added to home screen", Toast.LENGTH_SHORT).show()
    }

    // --- Lifecycle ---

    override fun onResume() {
        super.onResume()
        viewModel.tabManager.onResume()
        activeWebView()?.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.tabManager.onPause()
        activeWebView()?.onPause()
        // Save current tab URL state
        val wv = activeWebView()
        val tabId = viewModel.tabManager.activeTabId
        if (wv != null && tabId != null) {
            val savedUrl = wv.url
            if (savedUrl != null) {
                viewModel.updateActiveTabMeta(
                    viewModel.tabManager.activeTab()?.copy(url = savedUrl) ?: return
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save tab IDs and URLs for process recreation
        val tabs = viewModel.tabManager.tabs
        outState.putStringArrayList("tab_ids", ArrayList(tabs.map { it.id }))
        outState.putStringArrayList("tab_urls", ArrayList(tabs.map { it.url }))
        outState.putStringArrayList("tab_titles", ArrayList(tabs.map { it.title }))
        outState.putBooleanArray("tab_incognitos", tabs.map { it.isIncognito }.toBooleanArray())
        outState.putString("active_tab_id", viewModel.tabManager.activeTabId)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        viewModel.tabManager.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
            viewModel.tabManager.onLowMemory()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Config changes handled: orientation, screenSize, keyboard, uiMode
        // WebView handles these internally; no reload needed
    }

    // --- Helpers ---

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlBar.windowToken, 0)
        binding.urlBar.clearFocus()
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun resolveAttrColor(attr: Int): Int {
        val ta = theme.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0xFFFFFF)
        ta.recycle()
        return color
    }

    companion object {
        const val EXTRA_OPEN_URL = "extra_open_url"
    }
}
