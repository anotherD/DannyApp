package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.webview.ScoliaWebViewManager

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var webViewManager: ScoliaWebViewManager
    private var isWebViewInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startLayout: View = findViewById(R.id.startLayout)
        val webView: WebView = findViewById(R.id.scoliaWebView)
        val btnOpenScolia: Button = findViewById(R.id.btnOpenScolia)

        // Verhindert, dass die Ansicht unter die Status- oder Navigationsleiste rutscht
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        btnOpenScolia.setOnClickListener {
            startLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE

            if (!isWebViewInitialized) {
                // WebView Manager initialisieren und Konfiguration laden
                webViewManager = ScoliaWebViewManager(webView, viewModel.scoliaRepository)
                webViewManager.setupWebView("https://game.scoliadarts.com/game")
                isWebViewInitialized = true
            }
        }

        // Back-Button Handling: Wenn WebView sichtbar ist, entweder im WebView zurück navigieren oder zurück zur Startseite
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
                    webView.goBack()
                } else if (webView.visibility == View.VISIBLE) {
                    webView.visibility = View.GONE
                    startLayout.visibility = View.VISIBLE
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }
}
