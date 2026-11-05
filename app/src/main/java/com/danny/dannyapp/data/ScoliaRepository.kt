package com.danny.dannyapp.data

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ScoliaRepository(context: Context) {

    private val scoliaLogger = ScoliaLogger(context)
    private val scoliaEventParser = ScoliaEventParser()

    private val _scoliaEvents = MutableSharedFlow<ScoliaEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val scoliaEvents: SharedFlow<ScoliaEvent> = _scoliaEvents.asSharedFlow()

    fun getLogFilePath(): String = scoliaLogger.getLogFilePath()

    inner class ScoliaJavaScriptInterface {
        @JavascriptInterface
        fun onScoliaMessage(jsonData: String) {
            // 1. Ungefiltert in Datei loggen
            scoliaLogger.log(jsonData)

            // 2. Parst und emittiert das Event direkt über tryEmit (nicht-blockierend & thread-sicher)
            val event = scoliaEventParser.parse(jsonData)
            _scoliaEvents.tryEmit(event)
        }
    }

    val javaScriptInterface = ScoliaJavaScriptInterface()
}
