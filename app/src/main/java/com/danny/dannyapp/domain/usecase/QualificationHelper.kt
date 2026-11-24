package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry

/**
 * Zustandsloser Helper für Sortierung nach Order of Merit, Auffüllen von Teilnehmerfeldern,
 * Ermittlung von vorqualifizierten Tour-Card-Inhabern und Runden-Sequenzen.
 */
object QualificationHelper {

    /**
     * Ermittelt die Runden-Key-Sequenz (Strings) für ein gegebenes [TournamentEvent].
     * Für Nicht-Qualifier wird an [getRoundSequence] delegiert und zu Namen gemappt.
     * Für Qualifier werden bei leerer [allMatches]-Liste die in der JSON-Konfiguration definierte Runden-Keys
     * (ohne ungenutztes QUAL_PRELIM) zurückgegeben, bei gefüllter [allMatches]-Liste die Runden-Keys direkt aus den Matches
     * in chronologischer Reihenfolge (QUAL_R1, QUAL_R2, ..., QUAL_FINAL).
     */
    fun getRoundSequenceKeys(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<String> {
        if (!event.isQualifier) {
            return getRoundSequence(event, allMatches).map { it.name }
        }

        if (allMatches.isEmpty()) {
            val config = TournamentConfigLoader.safeConfig()
                ?: error("TournamentConfigLoader wurde nicht initialisiert")
            val eventConfig = config.events[event]
                ?: error("Keine Event-Konfiguration für $event gefunden")
            val definedRounds = eventConfig.definedRounds
                .filter { it != TournamentRound.QUAL_PRELIM }
                .ifEmpty { eventConfig.definedRounds }
            return definedRounds.map { it.name }
        }

        val presentRoundKeys = allMatches.map { it.round }.toSet()
        val sortedKeys = mutableListOf<String>()

        if (presentRoundKeys.contains(TournamentRound.QUAL_PRELIM.name)) {
            sortedKeys.add(TournamentRound.QUAL_PRELIM.name)
        }

        val prelimRounds = presentRoundKeys
            .mapNotNull { roundKey ->
                TournamentRound.parseQualifierRoundNumber(roundKey)?.let { roundNumber ->
                    roundNumber to roundKey
                }
            }
            .sortedBy { it.first }
            .map { it.second }

        sortedKeys.addAll(prelimRounds)

        if (presentRoundKeys.contains(TournamentRound.QUAL_FINAL.name)) {
            sortedKeys.add(TournamentRound.QUAL_FINAL.name)
        }

        return sortedKeys
    }

    /**
     * Determines the sequence of rounds for a given [TournamentEvent].
     * If [allMatches] is provided and non-empty, truncates the sequence to start from the earliest round present in matches.
     */
    fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        val fullSequence = event.getRoundSequence()

        if (allMatches.isEmpty()) return fullSequence

        val presentRoundNames = allMatches.map { it.round }.toSet()
        val startIndex = allMatches.mapNotNull { match ->
            fullSequence.indexOfFirst { it.name == match.round }.takeIf { it >= 0 }
        }.minOrNull() ?: 0

        return fullSequence.subList(startIndex, fullSequence.size).filter { round ->
            !event.isQualifier || round.name in presentRoundNames
        }
    }

    /**
     * Sortiert eine Liste von Spieler-IDs nach dem Rang in der Order of Merit aufsteigend.
     * Nicht gerankte Spieler ohne Eintrag in der Order of Merit werden zufällig geordnet.
     */
    fun sortPlayerIdsByOom(
        playerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>
    ): List<Long> {
        val oomMap = oomEntries.associateBy { it.playerId }

        return playerIds
            .shuffled()
            .sortedBy { oomMap[it]?.rank ?: Int.MAX_VALUE }
    }

    /**
     * Resolves prequalified Tour Card holders (top OOM and top Pro Tour OOM) for events or qualifiers.
     * Returns a pair of (topOomIds, topPtOomIds).
     */
    fun resolvePrequalifiedTourCardIds(
        event: TournamentEvent,
        tourCardIds: List<Long>,
        mainOom: List<OrderOfMeritEntry>,
        proTourOom: List<OrderOfMeritEntry>
    ): Pair<List<Long>, List<Long>> {
        val isWorldChampionship = event == TournamentEvent.QUALIFIER_WM || event == TournamentEvent.WORLD_CHAMPIONSHIP
        val targetOomCount = if (isWorldChampionship) 40 else 16
        val targetPtOomCount = if (isWorldChampionship) 40 else 16

        val sortedOomTc = sortPlayerIdsByOom(tourCardIds, mainOom)
        val topOomIds = sortedOomTc.take(targetOomCount)
        val remainingTcAfterOom = tourCardIds.filterNot { it in topOomIds }
        val topPtOomIds = sortPlayerIdsByOom(remainingTcAfterOom, proTourOom).take(targetPtOomCount)

        return Pair(topOomIds, topPtOomIds)
    }

}
