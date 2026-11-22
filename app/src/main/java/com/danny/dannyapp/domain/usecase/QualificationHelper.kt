package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry

/**
 * Zustandsloser Helper für Sortierung nach Order of Merit, Auffüllen von Teilnehmerfeldern,
 * Ermittlung von vorqualifizierten Tour-Card-Inhabern und Runden-Sequenzen.
 */
object QualificationHelper {

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
