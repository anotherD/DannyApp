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

/**
 * Main activity handling navigation between the start menu and the Scolia browser view.
 */
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
        val btnProfiles: Button = findViewById(R.id.btnProfiles)
        val btnCareers: Button = findViewById(R.id.btnCareers)
        val btnOpenDesignShowcase: Button = findViewById(R.id.btnOpenDesignShowcase)

        // Set up the toolbar for the start screen – no back button.
        setupToolbar(getString(R.string.app_title), showBack = false, rootViewId = R.id.main)

        btnOpenScolia.setOnClickListener { showScoliaBrowserView(startLayout, webView) }
        btnProfiles.setOnClickListener {
            val intent = android.content.Intent(this, ProfilesActivity::class.java)
            startActivity(intent)
        }
        btnCareers.setOnClickListener {
            val intent = android.content.Intent(this, CareersActivity::class.java)
            startActivity(intent)
        }
        btnOpenDesignShowcase.setOnClickListener {
            val intent = android.content.Intent(this, DesignShowcaseActivity::class.java)
            startActivity(intent)
        }

        // Back‑button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    webView.isVisible && webView.canGoBack() -> {
                        webView.goBack()
                    }
                    webView.isVisible -> {
                        showStartMenuView(startLayout, webView)
                    }
                    else -> {
                        // Temporarily disable the callback so the system back navigation works.
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    /**
     * Shows the Scolia browser view and configures the toolbar.
     */
    private fun showScoliaBrowserView(startLayout: View, webView: WebView) {
        startLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        toolbar?.title = getString(R.string.scolia_browser)
        toolbar?.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (!isWebViewInitialized) {
            webViewManager = ScoliaWebViewManager(webView, viewModel.scoliaRepository)
            webViewManager.setupWebView("https://game.scoliadarts.com/game")
            isWebViewInitialized = true
        }
    }

    /**
     * Shows the start menu view and resets the toolbar to its default state.
     */
    private fun showStartMenuView(startLayout: View, webView: WebView) {
        webView.visibility = View.GONE
        startLayout.visibility = View.VISIBLE
        toolbar?.title = getString(R.string.app_title)
        toolbar?.navigationIcon = null
        toolbar?.setNavigationOnClickListener(null)
    }
}
