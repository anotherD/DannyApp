package com.danny.dannyapp

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ScoliaRepository(context: Context) {

    private val scoliaLogger = ScoliaLogger(context)
    private val scoliaEventParser = ScoliaEventParser()
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _scoliaEvents = MutableSharedFlow<ScoliaEvent>()
    val scoliaEvents: SharedFlow<ScoliaEvent> = _scoliaEvents.asSharedFlow()

    fun getLogFilePath(): String = scoliaLogger.getLogFilePath()

    inner class ScoliaJavaScriptInterface {
        @JavascriptInterface
        fun onScoliaMessage(jsonData: String) {
            // 1. Ungefiltert in Datei loggen
            scoliaLogger.log(jsonData)

            // 2. Parst und emittiert das Event über Coroutines
            val event = scoliaEventParser.parse(jsonData)
            scope.launch {
                _scoliaEvents.emit(event)
            }
        }
    }

    val javaScriptInterface = ScoliaJavaScriptInterface()
}
