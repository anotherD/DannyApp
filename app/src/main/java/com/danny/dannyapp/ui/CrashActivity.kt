package com.danny.dannyapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

        setContentView(R.layout.activity_crash)

        findViewById<TextView>(R.id.tvCrashErrorMessage).text = errorMessageText
        findViewById<TextView>(R.id.tvCrashStacktrace).text = stacktraceText
        findViewById<Button>(R.id.btnCrashCopy).setOnClickListener { copyErrorToClipboard() }
        findViewById<Button>(R.id.btnCrashRestart).setOnClickListener { restartApp() }
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
        UIHelper.showSnackbar(findViewById(android.R.id.content), getString(R.string.crash_copied_toast))
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
