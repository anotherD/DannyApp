package com.danny.dannyapp.data

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ScoliaRepository private constructor(context: Context) {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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

            // 2. Parst und emittiert das Event asynchron auf dem Dispatchers.Default Background-Thread
            repositoryScope.launch {
                val event = scoliaEventParser.parse(jsonData)
                _scoliaEvents.emit(event)
            }
        }
    }

    val javaScriptInterface = ScoliaJavaScriptInterface()

    companion object {
        @Volatile
        private var instance: ScoliaRepository? = null

        fun getInstance(context: Context): ScoliaRepository {
            return instance ?: synchronized(this) {
                instance ?: ScoliaRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
