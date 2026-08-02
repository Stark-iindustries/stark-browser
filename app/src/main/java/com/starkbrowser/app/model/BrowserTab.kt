package com.starkbrowser.app.model

import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "about:home",
    val title: String = "New Tab",
    val isIncognito: Boolean = false,
    val faviconUrl: String? = null,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Int = 0,
    val isDesktopMode: Boolean = false
)
