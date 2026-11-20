package com.danny.dannyapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R

/**
 * Activity displayed when an uncaught exception crashes the application, providing
 * readable error details, copy functionality, and app restart options.
 */
class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACKTRACE = "extra_stacktrace"
    }

    private var errorMessageText: String = ""
    private var stacktraceText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_DannyApp)

        errorMessageText = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: getString(R.string.error_unknown)
        stacktraceText = intent.getStringExtra(EXTRA_STACKTRACE) ?: ""

        setupCrashLayout()
    }

    /**
     * Programmatically builds the crash screen layout with title, subtitle, error box,
     * scrollable stacktrace view, copy button, and restart button.
     */
    private fun setupCrashLayout() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_hintergrund))
            setPadding(48, 64, 48, 64)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        val titleView = TextView(this).apply {
            text = getString(R.string.crash_title)
            setTextColor(ContextCompat.getColor(context, R.color.rot_haupt))
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(titleView)

        // Subtitle
        val subtitleView = TextView(this).apply {
            text = getString(R.string.crash_subtitle)
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(subtitleView)

        // Highlighted error message box container
        val errorBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_container))
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }

        val errorMessageView = TextView(this).apply {
            text = errorMessageText
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        errorBox.addView(errorMessageView)
        rootLayout.addView(errorBox)

        // Scrollable stacktrace container (Vertical ScrollView with horizontal ScrollView)
        val verticalScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            setBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_tief))
            setPadding(24, 24, 24, 24)
        }

        val horizontalScrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
        }

        val stacktraceTextView = TextView(this).apply {
            text = stacktraceText
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        horizontalScrollView.addView(stacktraceTextView)
        verticalScrollView.addView(horizontalScrollView)
        rootLayout.addView(verticalScrollView)

        // Buttons Container
        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Copy Button
        val copyButton = Button(this).apply {
            text = getString(R.string.crash_copy_button)
            setBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_container_hoch))
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
            setOnClickListener { copyErrorToClipboard() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        buttonsLayout.addView(copyButton)

        // Restart Button
        val restartButton = Button(this).apply {
            text = getString(R.string.crash_restart_button)
            setBackgroundColor(ContextCompat.getColor(context, R.color.rot_haupt))
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setOnClickListener { restartApp() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        buttonsLayout.addView(restartButton)

        rootLayout.addView(buttonsLayout)

        setContentView(rootLayout)
    }

    /**
     * Copies the error message and technical stacktrace to the system clipboard
     * and shows a toast notification.
     */
    private fun copyErrorToClipboard() {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val reportText = "Error:\n$errorMessageText\n\nStacktrace:\n$stacktraceText"
        val clipData = ClipData.newPlainText("Crash Report", reportText)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, R.string.crash_copied_toast, Toast.LENGTH_SHORT).show()
    }

    /**
     * Restarts the application by launching MainActivity with cleared tasks and finishes CrashActivity.
     */
    private fun restartApp() {
        val restartIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(restartIntent)
        finish()
    }
}
