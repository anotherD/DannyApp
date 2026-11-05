package com.danny.dannyapp.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.ScoliaEvent
import com.danny.dannyapp.data.ScoliaRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val scoliaRepository = ScoliaRepository(application)
    val scoliaEvents: SharedFlow<ScoliaEvent> = scoliaRepository.scoliaEvents

    init {
        Log.i(TAG, "📁 Scolia Event-Logdatei: ${scoliaRepository.getLogFilePath()}")

        viewModelScope.launch {
            scoliaEvents.collect { event ->
                handleScoliaEvent(event)
            }
        }
    }

    private fun handleScoliaEvent(event: ScoliaEvent) {
        when (event) {
            is ScoliaEvent.ThrowDetected -> {
                val (sector, score, x, y, _, remainingScore) = event.dartThrow
                Log.i(
                    TAG,
                    "🎯 WURF ERKANNT: Sektor=$sector | Score=$score | Rest=${remainingScore ?: "-"} | Coords=[$x, $y]",
                )
            }
            is ScoliaEvent.PhaseChanged -> {
                Log.i(TAG, "🔄 SPIELPHASE: ${event.phase}")
            }
            is ScoliaEvent.TakeoutStarted -> {
                Log.d(TAG, "🖐️ Darts herausziehen gestartet")
            }
            is ScoliaEvent.TakeoutFinished -> {
                Log.i(TAG, "✅ Darts herausziehen beendet (Board ist frei)")
            }
            is ScoliaEvent.GameEnded -> {
                Log.i(TAG, "🏁 SPIEL BEENDET (Match/Leg Statistik empfangen)")
            }
            is ScoliaEvent.Pong -> {
                // Heartbeat
            }
            is ScoliaEvent.GenericEvent -> {
                Log.d(TAG, "📦 Event empfangen (${event.type})")
            }
            is ScoliaEvent.Unknown -> {
                Log.d(TAG, "📦 Unbekanntes Event empfangen")
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
