package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.career.CareersActivity
import com.danny.dannyapp.ui.profile.ProfilesActivity
import com.danny.dannyapp.webview.ScoliaWebViewManager

/**
 * Main activity handling navigation between the start menu and the Scolia browser view.
 */
class MainActivity : BaseActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var webViewManager: ScoliaWebViewManager
    private var isWebViewInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startLayout: View = findViewById(R.id.startLayout)
        val webView: WebView = findViewById(R.id.scoliaWebView)
        val btnOpenScolia: Button = findViewById(R.id.btnOpenScolia)
        val btnProfiles: Button = findViewById(R.id.btnProfiles)
        val btnCareers: Button = findViewById(R.id.btnCareers)
        val btnOpenDesignShowcase: Button = findViewById(R.id.btnOpenDesignShowcase)

        // Start-Choreografie nur beim ersten Öffnen (nicht nach Bildschirm-Drehung) abspielen
        if (savedInstanceState == null) {
            setupWalkOnEntranceAnimation(
                splashScreen = splashScreen,
                buttons = listOf(btnOpenScolia, btnProfiles, btnCareers, btnOpenDesignShowcase)
            )
        }

        // Set up the toolbar for the start screen – no back button.
        setupToolbar("", showBack = false, rootViewId = R.id.main)

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
     * Choreografie beim App-Start:
     * 1. Splash-Icon fliegt zur Position von "WALK-ON" und morpht in den Titel.
     * 2. Bühnenbild ("stage") knallt mit Wucht ("Rumms, da bin ich!") ins Bild.
     * 3. Untertitel und Buttons blenden gestaffelt ein.
     */
    private fun setupWalkOnEntranceAnimation(
        splashScreen: androidx.core.splashscreen.SplashScreen,
        buttons: List<View>
    ) {
        val tvAppTitle: View = findViewById(R.id.tvAppTitle)
        val ivCardImage: View = findViewById(R.id.ivCardImage)
        val layoutSubtitle: View = findViewById(R.id.layoutSubtitle)

        // Alle Start-Elemente zunächst unsichtbar / in Ausgangsposition bringen
        tvAppTitle.alpha = 0f
        tvAppTitle.scaleX = 0.72f
        tvAppTitle.scaleY = 0.72f

        ivCardImage.alpha = 0f
        ivCardImage.scaleX = 1.28f
        ivCardImage.scaleY = 1.28f

        layoutSubtitle.alpha = 0f
        layoutSubtitle.translationY = 16f

        buttons.forEach { btn ->
            btn.alpha = 0f
            btn.translationY = 24f
        }

        var animationStarted = false

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            animationStarted = true
            val splashRoot = splashScreenView.view
            val iconView = splashScreenView.iconView

            // Ziel-Mittelpunkt von "WALK-ON" auf dem Bildschirm bestimmen
            val titleLoc = IntArray(2)
            tvAppTitle.getLocationOnScreen(titleLoc)
            val targetCenterX = titleLoc[0] + tvAppTitle.width / 2f
            val targetCenterY = titleLoc[1] + tvAppTitle.height / 2f

            // Schritt 1: Dart-Icon fliegt hoch zur "WALK-ON"-Position und blendet in den Titel über
            val iconLoc = IntArray(2)
            iconView.getLocationOnScreen(iconLoc)
            val iconCenterX = iconLoc[0] + iconView.width / 2f
            val iconCenterY = iconLoc[1] + iconView.height / 2f

            val deltaX = if (tvAppTitle.width > 0) targetCenterX - iconCenterX else 0f
            val deltaY = if (tvAppTitle.height > 0) targetCenterY - iconCenterY else -450f

            iconView.animate()
                .translationX(deltaX)
                .translationY(deltaY)
                .scaleX(0.55f)
                .scaleY(0.55f)
                .rotation(-3f)
                .alpha(0f)
                .setDuration(4200L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Splash-Hintergrund weich ausblenden, während "WALK-ON" erscheint
            splashRoot.animate()
                .alpha(0f)
                .setStartDelay(1400L)
                .setDuration(600L)
                .withEndAction { splashScreenView.remove() }
                .start()

            // "WALK-ON" Titel erscheint an genau der Zielstelle und rastet ein
            tvAppTitle.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(1600L)
                .setDuration(720L)
                .setInterpolator(AnticipateOvershootInterpolator())
//                .setInterpolator(OvershootInterpolator(1.3f))
                .start()

            // Schritt 2: "Rumms, da bin ich!" – Hero-Bild knallt ins Bild
            ivCardImage.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(4600L)
                .setDuration(340L)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()

            // Schritt 3a: Untertitel fadet kurz danach ein
            layoutSubtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(7400L)
                .setDuration(3000L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Schritt 3b: Buttons faden gestaffelt von oben nach unten ein
            buttons.forEachIndexed { index, btn ->
                btn.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(8400L + index * 70L)
                    .setDuration(2800L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        // Sicherheits-Fallback (falls Android Studio den System-Splash beim Warmstart überspringt)
        window.decorView.postDelayed({
            if (!animationStarted) {
                tvAppTitle.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300L).start()
                ivCardImage.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(2200L).setDuration(3400L)
                    .setInterpolator(OvershootInterpolator(1.6f)).start()
                layoutSubtitle.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(4800L).setDuration(2800L).start()
                buttons.forEachIndexed { index, btn ->
                    btn.animate()
                        .alpha(1f).translationY(0f)
                        .setStartDelay(5600L + index * 700L).setDuration(2600L).start()
                }
            }
        }, 2500L)
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
            webViewManager.setupWebView()
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
