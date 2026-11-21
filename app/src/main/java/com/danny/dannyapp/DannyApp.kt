package com.danny.dannyapp

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.danny.dannyapp.ui.CrashActivity
import kotlin.system.exitProcess

/**
 * Custom Application class responsible for global initialization and uncaught exception handling.
 */
class DannyApp : Application() {

    /**
     * Central application dependency container.
     */
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
    }

    /**
     * Sets up a global default uncaught exception handler to prevent silent crashes and present
     * a user-friendly crash screen.
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { targetThread, throwable ->
            try {
                val readableErrorMessage = throwable.cause?.message
                    ?: throwable.message
                    ?: throwable::class.java.simpleName
                val completeStacktrace = Log.getStackTraceString(throwable)

                val crashIntent = Intent(this@DannyApp, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_ERROR_MESSAGE, readableErrorMessage)
                    putExtra(CrashActivity.EXTRA_STACKTRACE, completeStacktrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(crashIntent)

                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (handlerException: Exception) {
                Log.e("DannyApp", "Error in UncaughtExceptionHandler", handlerException)
                defaultHandler?.uncaughtException(targetThread, throwable)
            }
        }
    }
}
