package com.danny.dannyapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Moderne Edge-to-Edge Ansicht aktivieren
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.scoliaWebView)

        // Verhindert, dass das WebView unter die Status- oder Navigationsleiste rutscht
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Aktiviert JavaScript und den lokalen Speicher für die Webseite
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true    // Erlaubt interne Web-Datenbanken

        // 1. Schalte Service Worker und App-Cache frei, damit Scripte im Hintergrund laufen dürfen
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // 2. Ändere den User-Agent (Scolia denkt jetzt, es läuft ein originaler Google Chrome auf dem PC)
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // Zwingt das WebView, im Zweifel die Daten aus dem Internet statt aus dem Cache zu laden
        // webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

        // 1. Erlaube dem WebView, Daten auch über "sichere/unsichere" Protokolle (wie WSS/WS) zu mischen
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 2. Erlaube Cookies von Drittanbietern (Scolia benötigt das für die Board-Lobby)
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)




        // Erstellt die Brücke namens "AndroidApp" zum Browser
        webView.addJavascriptInterface(ScoliaJavaScriptInterface(), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {

//            // Dieser Filter fängt JEDE Netzwerkanfrage (GET, POST etc.) ab
//            override fun shouldInterceptRequest(
//                view: WebView?,
//                request: android.webkit.WebResourceRequest?
//            ): android.webkit.WebResourceResponse? {
//                val url = request?.url?.toString() ?: ""
//
//                // Prüft, ob die URL das Tracking-Tool "posthog" enthält
//                if (url.contains("posthog.com", ignoreCase = true)) {
//                    Log.d("ScoliaAdBlock", "🚫 Tracking blockiert: $url")
//
//                    // Gibt eine leere 200 OK Antwort zurück, damit die Webseite nicht abstürzt,
//                    // sondern denkt, die Anfrage sei sauber durchgegangen.
//                    return android.webkit.WebResourceResponse(
//                        "application/json",
//                        "UTF-8",
//                        java.io.ByteArrayInputStream("{}".toByteArray())
//                    )
//                }
//
//                // Alle anderen Anfragen (Scolia-Lobby, Websockets) normal durchlassen
//                return super.shouldInterceptRequest(view, request)
//            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Webseite erfolgreich geladen: $url")
                injectScoliaHook()
            }
        }

        // Lädt die Scolia Web-App
        webView.loadUrl("https://game.scoliadarts.com/game")
//        webView.loadUrl("https://game.scoliadarts.com")
//        webView.loadUrl("https://www.google.de")
    }

    private fun injectScoliaHook() {
        val jsCode = """
            (function() {
                if (window.WebSocket.prototype.isHooked) return;
                
                const OriginalWebSocket = window.WebSocket;
                window.WebSocket = function(url, protocols) {
                    const ws = new OriginalWebSocket(url, protocols);
                    
                    ws.addEventListener('message', function(event) {
                        try {
                            const data = JSON.parse(event.data);
                            // Leitet die Daten an Kotlin weiter
                            AndroidApp.onScoliaMessage(event.data);
                        } catch (e) {
                            // Ignoriert nicht-JSON oder fehlerhafte Datenpakete
                        }
                    });
                    
                    return ws;
                };
                window.WebSocket.prototype = OriginalWebSocket.prototype;
                window.WebSocket.prototype.isHooked = true;
                console.log("🎯 Scolia WebSocket Hook aktiv!");
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    // Die Schnittstelle, die die Daten aus dem WebView empfängt
    inner class ScoliaJavaScriptInterface {
        @JavascriptInterface
        fun onScoliaMessage(jsonData: String) {
            // Wechselt auf den Hauptthread, damit du später die UI steuern kannst
            runOnUiThread {
                try {
                    val json = JSONObject(jsonData)

                    // Zeigt das komplette Datenpaket im Logcat an
                    Log.d(TAG, "📦 Daten erhalten: $jsonData")

                    // Filtert nach Wurf-Events (prüfe die genaue Schreibweise im Logcat!)
                    if (json.has("event") && json.getString("event") == "throw") {
                        val value = json.optString("value", "Unbekannt")
                        Log.i(TAG, "🎯 WURF ERKANNT IM POC: $value")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Parsen der JSON-Daten", e)
                }
            }
        }
    }

    // Hier definieren wir Konstanten sauber nach Kotlin-Standard
    companion object {
        private const val TAG = "ScoliaPoC"
    }
}
