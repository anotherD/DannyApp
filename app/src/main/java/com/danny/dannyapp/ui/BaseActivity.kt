package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.StringRes
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.AppContainer
import com.danny.dannyapp.DannyApp
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.navigation.AppScreen
import com.danny.dannyapp.ui.settings.SettingsActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    protected var toolbar: MaterialToolbar? = null

    protected var currentScreen: AppScreen? = null

    protected open fun buildUpNavigationExtras(parentScreen: AppScreen, intent: Intent) {}

    protected fun setupNavigationToolbar(
        screen: AppScreen,
        titleOverride: String? = null,
        dynamicBreadcrumbLabels: Map<AppScreen, String> = emptyMap(),
        rootViewId: Int = android.R.id.content
    ) {
        currentScreen = screen
        val title = titleOverride ?: getString(screen.titleResId)
        val subtitle = screen.buildBreadcrumb(this, dynamicBreadcrumbLabels)
        val showBack = (screen != AppScreen.HOME)
        val menuResId = if (screen.showSettingsMenu) R.menu.menu_main else 0
        setupToolbar(
            titleText = title,
            subtitleText = subtitle,
            showBack = showBack,
            rootViewId = rootViewId,
            menuResId = menuResId
        )
    }

    protected fun setupCareerNavigationToolbar(
        screen: AppScreen,
        careerName: String,
        seasonNumber: Int? = null,
        titleOverride: String? = null,
        rootViewId: Int = android.R.id.content
    ) {
        val overviewLabel = when {
            careerName.isNotBlank() && seasonNumber != null ->
                getString(R.string.career_season_label_format, careerName, seasonNumber)
            careerName.isNotBlank() -> careerName
            else -> getString(R.string.career)
        }
        setupNavigationToolbar(
            screen = screen,
            titleOverride = titleOverride,
            dynamicBreadcrumbLabels = mapOf(AppScreen.CAREER_OVERVIEW to overviewLabel),
            rootViewId = rootViewId
        )
    }

    /**
     * Central application dependency container.
     */
    protected val appContainer: AppContainer get() = (applicationContext as DannyApp).appContainer

    /**
     * Central settings manager instance provided by [appContainer].
     */
    protected val settingsManager by lazy { appContainer.settingsManager }

    protected var debugMode: Boolean
        get() = settingsManager.debugMode
        set(value) {
            settingsManager.debugMode = value
        }

    protected var autoSimulateCpu: Boolean
        get() = settingsManager.autoSimulateCpu
        set(value) {
            settingsManager.autoSimulateCpu = value
        }

    protected var autoAdvanceRound: Boolean
        get() = settingsManager.autoAdvanceRound
        set(value) {
            settingsManager.autoAdvanceRound = value
        }

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val contentView = findViewById<View>(android.R.id.content) ?: return@OnGlobalLayoutListener
        UIHelper.applyVerticalEdgeFadeToHierarchy(contentView)
    }

    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        val contentView = findViewById<View>(android.R.id.content) ?: return@OnScrollChangedListener
        UIHelper.applyVerticalEdgeFadeToHierarchy(contentView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.errorEvents.collect { message ->
                    showMessage(message, isError = true, duration = Snackbar.LENGTH_INDEFINITE)
                    appContainer.consumeErrorEvent()
                }
            }
        }

        // Zentraler vertikaler Edge-Fade fuer alle ScrollViews/RecyclerViews im gesamten Hierarchy.
        // Der OnGlobalLayoutListener und OnScrollChangedListener erfassen auch nachtraeglich
        // instanziierte ViewPager2-Fragmente (z.B. OrderOfMeritFragment beim Wischen).
        val vto = window.decorView.viewTreeObserver
        vto.addOnGlobalLayoutListener(globalLayoutListener)
        vto.addOnScrollChangedListener(scrollChangedListener)
    }

    override fun onDestroy() {
        val vto = window.decorView.viewTreeObserver
        if (vto.isAlive) {
            vto.removeOnGlobalLayoutListener(globalLayoutListener)
            vto.removeOnScrollChangedListener(scrollChangedListener)
        }
        super.onDestroy()
    }

    protected fun showMessage(
        message: String,
        isError: Boolean = false,
        duration: Int = Snackbar.LENGTH_SHORT,
    ) {
        UIHelper.showSnackbar(findViewById(android.R.id.content), message, isError, duration)
    }

    protected fun showMessage(
        @StringRes messageResId: Int,
        isError: Boolean = false,
        duration: Int = Snackbar.LENGTH_SHORT,
    ) {
        showMessage(getString(messageResId), isError, duration)
    }

    protected fun setupToolbar(
        titleText: String,
        subtitleText: CharSequence? = null,
        showBack: Boolean = true,
        rootViewId: Int = android.R.id.content,
        menuResId: Int = R.menu.menu_main,
    ) {
        toolbar = findViewById(R.id.toolbar)
        toolbar?.let { tb ->
            tb.title = titleText
            tb.subtitle = subtitleText
            if (showBack) {
                tb.setNavigationIcon(R.drawable.ic_arrow_back)
                tb.setNavigationOnClickListener {
                    onToolbarNavigationClicked()
                }
            } else {
                tb.navigationIcon = null
            }

            if (menuResId != 0) {
                tb.menu.clear()
                tb.inflateMenu(menuResId)
                tb.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_config -> {
                            val intent = Intent(this, SettingsActivity::class.java)
                            startActivity(intent)
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        val rootView = findViewById<View>(rootViewId)
        if (rootView != null) {
            val initialLeft = toolbar?.paddingLeft ?: 0
            val initialRight = toolbar?.paddingRight ?: 0
            val initialBottom = toolbar?.paddingBottom ?: 0

            ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                toolbar?.setPadding(initialLeft, insets.top, initialRight, initialBottom)
                rootView.setPadding(insets.left, 0, insets.right, insets.bottom)
                windowInsets
            }
        }
    }

    protected open fun onToolbarNavigationClicked() {
        val screen = currentScreen ?: AppScreen.fromActivity(this)
        if (screen?.parent != null) {
            val parentScreen = screen.parent
            val intent = Intent(this, parentScreen.activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            buildUpNavigationExtras(parentScreen, intent)
            startActivity(intent)
            finish()
        } else if (screen != null && screen != AppScreen.HOME) {
            finish()
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }


}
