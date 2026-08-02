package com.starkbrowser.app.browser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Lightweight domain-based content blocker.
 * Loads a blocklist from assets and intercepts matching requests.
 */
class ContentBlocker(context: Context) {

    private val blockedDomains: Set<String> by lazy {
        try {
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { it.trim().lowercase() }
                    .toHashSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private val emptyResponse = WebResourceResponse(
        "text/plain", "utf-8", 200, "OK",
        mapOf("Access-Control-Allow-Origin" to "*"),
        ByteArrayInputStream(ByteArray(0))
    )

    fun shouldBlock(url: String): Boolean {
        if (blockedDomains.isEmpty()) return false
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            // Check exact domain and subdomains
            if (blockedDomains.contains(host)) return true
            // Check if any blocked domain matches as suffix (e.g. "ads.example.com" → "example.com" blocked)
            var dotIndex = host.indexOf('.')
            while (dotIndex != -1) {
                val sub = host.substring(dotIndex + 1)
                if (blockedDomains.contains(sub)) return true
                dotIndex = host.indexOf('.', dotIndex + 1)
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun getBlockedResponse(): WebResourceResponse = emptyResponse
}
