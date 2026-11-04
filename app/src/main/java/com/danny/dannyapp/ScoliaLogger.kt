package com.danny.dannyapp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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
        val formattedJson = try {
            val trimmed = rawJson.trim()
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> rawJson
            }
        } catch (_: Exception) {
            rawJson
        }

        val logEntry = "────────────────────────────────────────────────────────────────\n" +
                "[$timestamp]\n" +
                "$formattedJson\n\n"

        try {
            FileWriter(logFile, true).use { writer ->
                writer.write(logEntry)
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
