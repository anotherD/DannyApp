package com.danny.dannyapp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScoliaLogger(context: Context) {

    private val logFile: File = File(context.filesDir, "scolia_events.log")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    init {
        Log.i(TAG, "📁 Scolia Event-Logdatei bereit unter: ${logFile.absolutePath}")
    }

    @Synchronized
    fun log(rawJson: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $rawJson\n"

        try {
            FileWriter(logFile, true).use { writer ->
                writer.write(logLine)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Fehler beim Schreiben in die Logdatei", e)
        }
    }

    fun getLogFilePath(): String = logFile.absolutePath

    fun getLogFile(): File = logFile

    fun clearLog() {
        try {
            if (logFile.exists()) {
                logFile.writeText("")
                Log.i(TAG, "🧹 Logdatei geleert.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Fehler beim Leeren der Logdatei", e)
        }
    }

    companion object {
        private const val TAG = "ScoliaLogger"
    }
}
