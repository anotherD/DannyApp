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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var scoliaLogger: ScoliaLogger
    private val scoliaEventParser = ScoliaEventParser()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Moderne Edge-to-Edge Ansicht aktivieren
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Logger initialisieren (Schreibt ungefilterte WebSocket-Logs in eine Datei)
        scoliaLogger = ScoliaLogger(this)
        Log.i(TAG, "📁 Scolia Event-Logdatei: ${scoliaLogger.getLogFilePath()}")

        webView = findViewById(R.id.scoliaWebView)

        // Verhindert, dass das WebView unter die Status- oder Navigationsleiste rutscht
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Aktiviert JavaScript und den lokalen Speicher (HTML5 DOM Storage) für die Webseite
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Ändere den User-Agent (Scolia denkt jetzt, es läuft ein originaler Google Chrome auf dem PC)
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // Erlaube dem WebView, Daten auch über "sichere/unsichere" Protokolle (wie WSS/WS) zu mischen
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Erlaube Cookies von Drittanbietern (Scolia benötigt das für die Board-Lobby)
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // WebChromeClient für Console-Logs und WebView Debugging im Browser ermöglichen
        webView.webChromeClient = android.webkit.WebChromeClient()
        WebView.setWebContentsDebuggingEnabled(true)

        // Erstellt die Brücke namens "AndroidApp" zum Browser
        webView.addJavascriptInterface(ScoliaJavaScriptInterface(), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {

            // Dieser Filter fängt Netzwerkanfragen (z. B. Tracking-Scripte) ab
            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: ""

                // Prüft, ob die URL das Tracking-Tool "posthog" enthält
                if (url.contains("posthog.com", ignoreCase = true)) {
                    //Log.d("ScoliaAdBlock", "🚫 Tracking blockiert: $url")

                    // Gibt eine leere 200 OK Antwort zurück, damit die Webseite nicht abstürzt,
                    // sondern denkt, die Anfrage sei sauber durchgegangen.
                    return android.webkit.WebResourceResponse(
                        "application/json",
                        "UTF-8",
                        java.io.ByteArrayInputStream("{}".toByteArray())
                    )
                }

                // Alle anderen Anfragen (Scolia-Lobby, Websockets) normal durchlassen
                return super.shouldInterceptRequest(view, request)
            }

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
                                // Ignoriert Fehler beim Weiterleiten
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

    // Die Schnittstelle, die die Daten aus dem WebView empfängt
    inner class ScoliaJavaScriptInterface {
        @JavascriptInterface
        fun onScoliaMessage(jsonData: String) {
            // 1. Schreibt JEDE Rohdaten-Nachricht ungefiltert in die Logdatei
            scoliaLogger.log(jsonData)

            // 2. Parst und verarbeitet das Event auf dem Hauptthread
            runOnUiThread {
                try {
                    when (val event = scoliaEventParser.parse(jsonData)) {
                        is ScoliaEvent.ThrowDetected -> {
                            val dart = event.dartThrow
                            Log.i(
                                TAG,
                                "🎯 WURF ERKANNT: Sektor=${dart.sector} | Score=${dart.score} | Rest=${dart.remainingScore ?: "-"} | Coords=[${dart.xCoordinate}, ${dart.yCoordinate}]"
                            )
                        }
                        is ScoliaEvent.PhaseChanged -> {
                            Log.i(TAG, "🔄 SPIELPHASE: ${event.phase}")
                        }
                        is ScoliaEvent.TakeoutStarted -> {
                            Log.d(TAG, "🖐️ Darts herausziehen gestartet")
                        }
                        is ScoliaEvent.TakeoutFinished -> {
                            Log.i(TAG, "✅ Darts herausziehen beendet (Board ist frei)")
                        }
                        is ScoliaEvent.GameEnded -> {
                            Log.i(TAG, "🏁 SPIEL BEENDET (Match/Leg Statistik empfangen)")
                        }
                        is ScoliaEvent.Pong -> {
                            // Heartbeat leise ignorieren/loggen
                        }
                        is ScoliaEvent.GenericEvent -> {
                            Log.d(TAG, "📦 Event empfangen (${event.type})")
                        }
                        is ScoliaEvent.Unknown -> {
                            Log.d(TAG, "📦 Unbekanntes Event empfangen")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler bei der Event-Verarbeitung", e)
                }
            }
        }
    }

    // Hier definieren wir Konstanten sauber nach Kotlin-Standard
    companion object {
        private const val TAG = "ScoliaPoC"
    }
}
