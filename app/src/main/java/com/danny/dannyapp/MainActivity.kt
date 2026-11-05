package com.danny.dannyapp

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var webViewManager: ScoliaWebViewManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView: WebView = findViewById(R.id.scoliaWebView)

        // Verhindert, dass das WebView unter die Status- oder Navigationsleiste rutscht
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // WebView Manager initialisieren und Konfiguration laden
        webViewManager = ScoliaWebViewManager(webView, viewModel.scoliaRepository)
        webViewManager.setupWebView("https://game.scoliadarts.com/game")
    }
}
