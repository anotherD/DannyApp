package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.google.android.material.appbar.MaterialToolbar

abstract class BaseActivity : AppCompatActivity() {

    protected var toolbar: MaterialToolbar? = null
    protected val settingsManager by lazy { SettingsManager(this) }

    protected var config1Checked: Boolean
        get() = settingsManager.config1Checked
        set(value) {
            settingsManager.config1Checked = value
        }

    protected var config2Checked: Boolean
        get() = settingsManager.config2Checked
        set(value) {
            settingsManager.config2Checked = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    protected fun setupToolbar(
        titleText: String,
        subtitleText: String? = null,
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
                tb.inflateMenu(menuResId)
                tb.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_config -> {
                            val intent = Intent(this, ConfigurationActivity::class.java)
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
        onBackPressedDispatcher.onBackPressed()
    }
}
