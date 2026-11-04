package com.danny.dannyapp

import android.util.Log
import org.json.JSONObject

class ScoliaEventParser {

    fun parse(jsonString: String): ScoliaEvent {
        return try {
            val json = JSONObject(jsonString)
            val type = json.optString("type", "")

            when {
                // 1. Scolia Web-App Format (via WebView WebSocket Proxy)
                (type == "API::GAME::GAME_STATE_CHANGED" || type == "API::GAME::GAME_CURRENT_STATE") -> {
                    parseGameStateChanged(json)
                }
                type == "API::COMMON::TAKEOUT_STARTED" -> {
                    ScoliaEvent.TakeoutStarted(jsonString)
                }
                type == "API::COMMON::TAKEOUT_FINISHED" || type.equals("TAKEOUT_FINISHED", ignoreCase = true) -> {
                    ScoliaEvent.TakeoutFinished(jsonString)
                }
                type == "API::GAME::GAME_ENDED_STATISTICS" || type == "API::X01::GAME_DELETED" -> {
                    ScoliaEvent.GameEnded(jsonString)
                }
                type == "API::COMMON::PUBLISH" -> {
                    parsePublishEvent(json)
                }
                type == "API::COMMON::PONG" -> {
                    ScoliaEvent.Pong(jsonString)
                }
                type.isNotEmpty() -> {
                    ScoliaEvent.GenericEvent(type, jsonString)
                }
                else -> {
                    ScoliaEvent.Unknown(jsonString)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Parsen des Scolia-Events", e)
            ScoliaEvent.Unknown(jsonString)
        }
    }

    private fun parsePublishEvent(json: JSONObject): ScoliaEvent {
        val payload = json.optJSONObject("payload") ?: return ScoliaEvent.GenericEvent("API::COMMON::PUBLISH", json.toString())
        val data = payload.optJSONObject("data") ?: return ScoliaEvent.GenericEvent("API::COMMON::PUBLISH", json.toString())
        val eventType = data.optString("eventType", "")

        if (eventType == "boardCurrentStateUpdate") {
            val currentState = data.optJSONObject("currentState")
            val state = currentState?.optString("state", "Unknown") ?: "Unknown"
            return ScoliaEvent.PhaseChanged("BoardState:$state")
        }

        return ScoliaEvent.GenericEvent("API::COMMON::PUBLISH", json.toString())
    }

    private fun parseGameStateChanged(json: JSONObject): ScoliaEvent {
        val payload = json.optJSONObject("payload") ?: return ScoliaEvent.Unknown(json.toString())
        val state = payload.optJSONObject("state") ?: return ScoliaEvent.Unknown(json.toString())
        val game = state.optJSONObject("game")

        // 1. Prüfe auf Wurf-Events in lastTriplets
        val lastTriplets = game?.optJSONObject("lastTriplets")
        if (lastTriplets != null) {
            extractLatestThrowFromTriplets(lastTriplets)?.let { dartThrow ->
                return ScoliaEvent.ThrowDetected(dartThrow)
            }
        }

        // 2. Prüfe auf Spielphasen-Änderungen (currentGamePhase)
        if (game != null && game.has("currentGamePhase")) {
            val phaseArray = game.optJSONArray("currentGamePhase")
            if (phaseArray != null && phaseArray.length() > 1) {
                val phase = phaseArray.optString(1, "")
                if (phase.isNotEmpty()) {
                    return ScoliaEvent.PhaseChanged(phase)
                }
            }
        }

        return ScoliaEvent.GenericEvent("API::GAME::GAME_STATE_CHANGED", json.toString())
    }

    private fun extractLatestThrowFromTriplets(lastTriplets: JSONObject): DartThrow? {
        // In lastTriplets liegen Spieler-Objekte mit Array an Würfen
        val keys = lastTriplets.keys()
        while (keys.hasNext()) {
            val playerKey = keys.next()
            val playerTriplets = lastTriplets.optJSONObject(playerKey) ?: continue

            val tripletKeys = playerTriplets.keys()
            while (tripletKeys.hasNext()) {
                val tripletKey = tripletKeys.next()
                if (tripletKey.startsWith("_")) continue // Ignoriere interne Metadaten wie _t

                val throwArray = playerTriplets.optJSONArray(tripletKey) ?: continue
                if (throwArray.length() > 0) {
                    val lastThrowObj = throwArray.optJSONObject(throwArray.length() - 1) ?: continue

                    val sector = lastThrowObj.optString("sector", "None")
                    val score = if (lastThrowObj.has("score")) lastThrowObj.optInt("score") else calculateScoreFromSector(sector)
                    val remainingScore = lastThrowObj.optInt("remainingScore", 0)
                    val bounceout = lastThrowObj.optBoolean("bounceout", false)
                    val detectionTime = if (lastThrowObj.has("detectionTime")) lastThrowObj.optString("detectionTime") else null

                    var x: Double? = null
                    var y: Double? = null
                    val coords = lastThrowObj.optJSONArray("coordinates")
                    if (coords != null && coords.length() >= 2) {
                        x = coords.optDouble(0)
                        y = coords.optDouble(1)
                    }

                    return DartThrow(
                        sector = sector,
                        score = score,
                        xCoordinate = x,
                        yCoordinate = y,
                        isBounceout = bounceout,
                        remainingScore = remainingScore,
                        detectionTime = detectionTime,
                    )
                }
            }
        }
        return null
    }

    private fun calculateScoreFromSector(sector: String): Int {
        val s = sector.trim().uppercase()
        return when {
            s == "BULL" || s == "DB" -> 50
            s == "25" || s == "SB" -> 25
            s == "NONE" || s == "0" -> 0
            s.startsWith("T") -> (s.substring(1).toIntOrNull() ?: 0) * 3
            s.startsWith("D") -> (s.substring(1).toIntOrNull() ?: 0) * 2
            s.startsWith("S") -> s.substring(1).toIntOrNull() ?: 0
            else -> s.toIntOrNull() ?: 0
        }
    }

    companion object {
        private const val TAG = "ScoliaEventParser"
    }
}
