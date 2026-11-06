package com.danny.dannyapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScoliaLogger(context: Context) {

    private val logFile: File = File(context.filesDir, "scolia_events.log")
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val logChannel = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        Log.i(TAG, "📁 Scolia Event-Logdatei bereit unter: ${logFile.absolutePath}")
        startLogWriter()
    }

    private fun startLogWriter() {
        scope.launch {
            for (rawJson in logChannel) {
                writeLogEntry(rawJson)
            }
        }
    }

    fun log(rawJson: String) {
        logChannel.trySend(rawJson)
    }

    private fun writeLogEntry(rawJson: String) {
        val timestamp = LocalDateTime.now().format(timeFormatter)
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

    companion object {
        private const val TAG = "ScoliaLogger"
    }
}
