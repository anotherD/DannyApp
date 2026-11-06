package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.danny.dannyapp.R
import com.danny.dannyapp.webview.ScoliaWebViewManager

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
        setupToolbar("Danny App", showBack = false, rootViewId = R.id.main)

        btnOpenScolia.setOnClickListener {
            startLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            toolbar?.title = "Scolia Browser"
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
                        webView.visibility == View.VISIBLE && webView.canGoBack() -> {
                            webView.goBack()
                        }
                        webView.visibility == View.VISIBLE -> {
                            webView.visibility = View.GONE
                            startLayout.visibility = View.VISIBLE
                            toolbar?.title = "Danny App"
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
