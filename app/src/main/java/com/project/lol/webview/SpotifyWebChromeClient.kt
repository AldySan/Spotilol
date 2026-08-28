package com.project.lol.webview

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class SpotifyWebChromeClient(
    private val onProgressChanged: ((Int) -> Unit)? = null,
    private val onShowCustomView: ((View?, CustomViewCallback?) -> Unit)? = null,
    private val onHideCustomView: (() -> Unit)? = null,
    private val onFileChooser: ((ValueCallback<Array<Uri>>, Array<String>) -> Boolean)? = null
) : WebChromeClient() {

    private var childWebView: WebView? = null

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        onShowCustomView?.invoke(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomView?.invoke()
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message?
    ): Boolean {
        childWebView?.let {
            try { it.destroy() } catch (_: Exception) {}
        }
        val newWebView = WebView(view?.context ?: return false).apply {
            webViewClient = object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    view?.loadUrl(url ?: return false)
                    return true
                }
            }
        }
        childWebView = newWebView
        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = newWebView
        resultMsg?.sendToTarget()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback == null) return false
        val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()
        return onFileChooser?.invoke(filePathCallback, acceptTypes) ?: false
    }

    @Suppress("DEPRECATION")
    override fun onPermissionRequest(permissionRequest: PermissionRequest?) {
        permissionRequest ?: return
        Handler(Looper.getMainLooper()).post {
            val resources = permissionRequest.resources
            if (resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                permissionRequest.grant(resources)
            } else {
                permissionRequest.deny()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onConsoleMessage(message: String?, lineNumber: Int, sourceId: String?) {
        android.util.Log.d("SpotifyJS", "$message [$sourceId:$lineNumber]")
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged?.invoke(newProgress)
    }

    fun cleanup() {
        childWebView?.let {
            try { it.destroy() } catch (_: Exception) {}
        }
        childWebView = null
    }
}