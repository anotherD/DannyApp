package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danny.dannyapp.R
import com.google.android.material.appbar.MaterialToolbar

abstract class BaseActivity : AppCompatActivity() {

    protected var toolbar: MaterialToolbar? = null
    protected var config1Checked = false
    protected var config2Checked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    protected fun setupToolbar(
        titleText: String,
        showBack: Boolean = true,
        rootViewId: Int = android.R.id.content,
        menuResId: Int = R.menu.menu_main
    ) {
        toolbar = findViewById(R.id.toolbar)
        toolbar?.let { tb ->
            tb.title = titleText
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
                            showConfigMenu()
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        val rootView = findViewById<View>(rootViewId)
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                toolbar?.setPadding(toolbar!!.paddingLeft, insets.top, toolbar!!.paddingRight, toolbar!!.paddingBottom)
                rootView.setPadding(insets.left, 0, insets.right, insets.bottom)
                windowInsets
            }
        }
    }

    protected open fun showConfigMenu() {
        val items = arrayOf(getString(R.string.dummy_config_1), getString(R.string.dummy_config_2))
        val checkedItems = booleanArrayOf(config1Checked, config2Checked)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.configuration)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                when (which) {
                    0 -> config1Checked = isChecked
                    1 -> config2Checked = isChecked
                }
            }
            .setPositiveButton(android.R.string.ok) { d, _ ->
                d.dismiss()
                Toast.makeText(this, "Konfiguration gespeichert", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(resources.getColor(R.color.rot_hell, theme))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(resources.getColor(R.color.text_primar, theme))
        }

        dialog.show()
    }

    protected open fun onToolbarNavigationClicked() {
        onBackPressedDispatcher.onBackPressed()
    }
}
