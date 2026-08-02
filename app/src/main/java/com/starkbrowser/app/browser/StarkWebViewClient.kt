package com.starkbrowser.app.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import androidx.appcompat.app.AlertDialog
import com.starkbrowser.app.R

class StarkWebViewClient(
    private val context: Context,
    private val contentBlocker: ContentBlocker,
    private var contentBlockingEnabled: Boolean = false,
    private val onPageStarted: (url: String, favicon: Bitmap?) -> Unit = { _, _ -> },
    private val onPageFinished: (url: String, title: String?) -> Unit = { _, _ -> },
    private val onReceivedError: (url: String) -> Unit = {}
) : WebViewClient() {

    var isContentBlockingEnabled: Boolean
        get() = contentBlockingEnabled
        set(value) { contentBlockingEnabled = value }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url ?: "", favicon)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(url ?: "", view.title)
    }

    override fun onReceivedError(
        view: WebView, request: WebResourceRequest, error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onReceivedError(request.url?.toString() ?: "")
            // Show simple error page
            val errorHtml = buildErrorPage(
                request.url?.toString() ?: "",
                error.description?.toString() ?: "Unknown error"
            )
            view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Show a dialog — never silently bypass SSL errors
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.ssl_error))
            .setMessage(buildString {
                append("Security certificate problem for:\n")
                append(error.url ?: "unknown host")
                append("\n\nError: ")
                append(
                    when (error.primaryError) {
                        SslError.SSL_EXPIRED -> "Certificate has expired"
                        SslError.SSL_IDMISMATCH -> "Hostname does not match certificate"
                        SslError.SSL_UNTRUSTED -> "Certificate authority is untrusted"
                        SslError.SSL_DATE_INVALID -> "Certificate date is invalid"
                        else -> "Certificate error (${error.primaryError})"
                    }
                )
                append("\n\nProceeding may expose your data.")
            })
            .setPositiveButton(context.getString(R.string.proceed_anyway)) { _, _ -> handler.proceed() }
            .setNegativeButton(context.getString(R.string.go_back)) { _, _ -> handler.cancel() }
            .setCancelable(false)
            .show()
    }

    override fun shouldInterceptRequest(
        view: WebView, request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return super.shouldInterceptRequest(view, request)
        if (contentBlockingEnabled && contentBlocker.shouldBlock(url)) {
            return contentBlocker.getBlockedResponse()
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        val scheme = uri.scheme ?: return false

        // Handle tel:, mailto:, intent:, etc.
        return when {
            scheme == "tel" || scheme == "mailto" || scheme == "sms" -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) { /* ignore */ }
                true
            }
            scheme == "intent" -> {
                try {
                    val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    context.startActivity(intent)
                } catch (e: Exception) { /* ignore */ }
                true
            }
            scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) { /* ignore */ }
                true
            }
            else -> false  // Let WebView handle it
        }
    }

    private fun buildErrorPage(url: String, error: String): String = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        body { font-family: sans-serif; text-align: center; padding: 40px 20px; background: #1c1c1e; color: #e8e8e8; }
        .icon { font-size: 64px; margin-bottom: 16px; }
        h1 { color: #e8e8e8; font-size: 22px; }
        p { color: #ababab; font-size: 14px; margin: 8px 0; }
        .url { font-size: 12px; color: #666; word-break: break-all; }
        button { margin-top: 24px; padding: 12px 24px; background: #1a73e8; color: #fff;
                 border: none; border-radius: 24px; font-size: 15px; cursor: pointer; }
        </style></head><body>
        <div class="icon">⚠️</div>
        <h1>Page could not be loaded</h1>
        <p class="url">$url</p>
        <p>$error</p>
        <button onclick="history.back()">Go back</button>
        </body></html>
    """.trimIndent()
}
