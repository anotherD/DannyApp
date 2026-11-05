package com.danny.dannyapp

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class ScoliaWebViewManager(
    private val webView: WebView,
    private val repository: ScoliaRepository,
) {

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(urlToLoad: String = "https://game.scoliadarts.com/game") {
        // 1. JavaScript & Storage
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // 2. User-Agent (Desktop Chrome)
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // 3. Protocol & Cookie Management
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 4. Debugging & Chrome Client
        webView.webChromeClient = WebChromeClient()
        WebView.setWebContentsDebuggingEnabled(true)

        // 5. JavaScript Interface Brücke
        webView.addJavascriptInterface(repository.javaScriptInterface, "AndroidApp")

        // 6. WebViewClient mit AdBlocker für PostHog Tracking
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""

                if (url.contains("posthog.com", ignoreCase = true)) {
                    return WebResourceResponse(
                        "application/json",
                        "UTF-8",
                        ByteArrayInputStream("{}".toByteArray()),
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Webseite geladen: $url")
                injectScoliaHook()
            }
        }

        // 7. URL Laden
        webView.loadUrl(urlToLoad)
    }

    private fun injectScoliaHook() {
        val jsCode = """
            (function() {
                if (window.WebSocket.__isHooked) return;
                
                const OriginalWebSocket = window.WebSocket;
                
                const WebSocketProxy = new Proxy(OriginalWebSocket, {
                    construct(target, args) {
                        const ws = new target(...args);
                        
                        ws.addEventListener('message', function(event) {
                            try {
                                if (window.AndroidApp && window.AndroidApp.onScoliaMessage) {
                                    window.AndroidApp.onScoliaMessage(event.data);
                                }
                            } catch (e) {
                                // Ignoriert Fehler
                            }
                        });
                        
                        return ws;
                    }
                });
                
                window.WebSocket = WebSocketProxy;
                window.WebSocket.__isHooked = true;
                console.log("🎯 Scolia WebSocket Hook aktiv!");
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    companion object {
        private const val TAG = "ScoliaWebViewManager"
    }
}
