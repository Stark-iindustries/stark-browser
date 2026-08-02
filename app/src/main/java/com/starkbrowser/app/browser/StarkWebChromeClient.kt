package com.starkbrowser.app.browser

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.*
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StarkWebChromeClient(
    private val activity: Activity,
    private val onProgressChanged: (Int) -> Unit = {},
    private val onTitleReceived: (String) -> Unit = {},
    private val onFaviconReceived: (Bitmap?) -> Unit = {},
    private val onFullscreenEnter: (View) -> Unit = {},
    private val onFullscreenExit: () -> Unit = {},
    private val onOpenNewTab: (String) -> Unit = {},
    private val fileChooserLauncher: ActivityResultLauncher<Intent>? = null
) : WebChromeClient() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        title?.let { onTitleReceived(it) }
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        onFaviconReceived(icon)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        customView = view
        customViewCallback = callback
        onFullscreenEnter(view)
    }

    override fun onHideCustomView() {
        customView = null
        customViewCallback = null
        onFullscreenExit()
    }

    override fun onCreateWindow(
        view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
    ): Boolean {
        val newWebView = WebView(activity)
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        transport.webView = newWebView
        resultMsg.sendToTarget()
        // Get URL from the new WebView once loaded
        newWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                url?.let { onOpenNewTab(it) }
                newWebView.destroy()
            }
        }
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val permsToRequest = mutableListOf<String>()
        val webPerms = request.resources

        for (perm in webPerms) {
            when (perm) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                        permsToRequest.add(Manifest.permission.CAMERA)
                    }
                }
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        permsToRequest.add(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }

        if (permsToRequest.isNotEmpty()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Permission Required")
                .setMessage("This website needs access to: ${webPerms.joinToString(", ")}")
                .setPositiveButton("Allow") { _, _ ->
                    ActivityCompat.requestPermissions(activity, permsToRequest.toTypedArray(), 1001)
                    request.grant(webPerms)
                }
                .setNegativeButton("Deny") { _, _ -> request.deny() }
                .show()
        } else {
            request.grant(webPerms)
        }
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String, callback: GeolocationPermissions.Callback
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Location Access")
            .setMessage("$origin wants to know your location.")
            .setPositiveButton("Allow") { _, _ ->
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false)
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1002
                    )
                    callback.invoke(origin, false, false)
                }
            }
            .setNegativeButton("Deny") { _, _ -> callback.invoke(origin, false, false) }
            .show()
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        MaterialAlertDialogBuilder(activity)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result.confirm() }
            .setCancelable(false)
            .show()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        MaterialAlertDialogBuilder(activity)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result.confirm() }
            .setNegativeButton("Cancel") { _, _ -> result.cancel() }
            .setCancelable(false)
            .show()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        this.filePathCallback?.onReceiveValue(null)
        this.filePathCallback = filePathCallback

        val intent = fileChooserParams.createIntent()
        return try {
            fileChooserLauncher?.launch(intent)
            true
        } catch (e: Exception) {
            this.filePathCallback = null
            false
        }
    }

    fun onFileChooserResult(data: Intent?) {
        val results = if (data?.data != null) {
            FileChooserParams.parseResult(Activity.RESULT_OK, data)
        } else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    fun cancelFileChooser() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }
}
