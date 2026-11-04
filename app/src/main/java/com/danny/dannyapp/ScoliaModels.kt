package com.danny.dannyapp

data class DartThrow(
    val sector: String,          // z.B. "T20", "D16", "s5", "Bull", "25", "None"
    val score: Int,             // z.B. 60, 32, 5, 50, 0
    val xCoordinate: Double? = null,
    val yCoordinate: Double? = null,
    val isBounceout: Boolean = false,
    val remainingScore: Int? = null,
    val detectionTime: String? = null
)

data class ScoliaGameState(
    val currentGamePhase: String? = null,  // z.B. "WaitingForThrows", "WaitingForTakeout"
    val currentPlayerId: String? = null,
    val lastThrow: DartThrow? = null
)

sealed class ScoliaEvent {
    data class ThrowDetected(val dartThrow: DartThrow) : ScoliaEvent()
    data class PhaseChanged(val phase: String) : ScoliaEvent()
    data class TakeoutStarted(val rawJson: String) : ScoliaEvent()
    data class TakeoutFinished(val rawJson: String) : ScoliaEvent()
    data class Pong(val rawJson: String) : ScoliaEvent()
    data class GenericEvent(val type: String, val rawJson: String) : ScoliaEvent()
    data class Unknown(val rawJson: String) : ScoliaEvent()
}
