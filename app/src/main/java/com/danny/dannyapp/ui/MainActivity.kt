package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.danny.dannyapp.R
import com.danny.dannyapp.webview.ScoliaWebViewManager
import androidx.core.view.isVisible

class MainActivity : BaseActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var webViewManager: ScoliaWebViewManager
    private var isWebViewInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startLayout: View = findViewById(R.id.startLayout)
        val webView: WebView = findViewById(R.id.scoliaWebView)
        val btnOpenScolia: Button = findViewById(R.id.btnOpenScolia)
        val btnCareer: Button = findViewById(R.id.btnCareer)
        val btnOpenDesignShowcase: Button = findViewById(R.id.btnOpenDesignShowcase)

        // Auf der Startseite: Titel "Danny App", KEIN Zurück-Button
        setupToolbar(getString(R.string.app_title), showBack = false, rootViewId = R.id.main)

        btnOpenScolia.setOnClickListener {
            startLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            toolbar?.title = getString(R.string.scolia_browser)
            toolbar?.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar?.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            if (!isWebViewInitialized) {
                webViewManager = ScoliaWebViewManager(webView, viewModel.scoliaRepository)
                webViewManager.setupWebView("https://game.scoliadarts.com/game")
                isWebViewInitialized = true
            }
        }

        btnCareer.setOnClickListener {
            val intent = android.content.Intent(this, CareerActivity::class.java)
            startActivity(intent)
        }

        btnOpenDesignShowcase.setOnClickListener {
            val intent = android.content.Intent(this, DesignShowcaseActivity::class.java)
            startActivity(intent)
        }

        // Back-Button Handling
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        webView.isVisible && webView.canGoBack() -> {
                            webView.goBack()
                        }
                        webView.isVisible -> {
                            webView.visibility = View.GONE
                            startLayout.visibility = View.VISIBLE
                            toolbar?.title = getString(R.string.app_title)
                            toolbar?.navigationIcon = null
                        }
                        else -> {
                            remove()
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            },
        )
    }
}
