package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.TournamentRoundPhase

/**
 * Zustandsloser Helper für Sortierung nach Order of Merit, Auffüllen von Teilnehmerfeldern,
 * Ermittlung von vorqualifizierten Tour-Card-Inhabern und Runden-Sequenzen.
 */
object QualificationHelper {

    /**
     * Ermittelt die Runden-Sequenz für ein [Tournament] und [TournamentEvent] basierend auf allen bisherigen Matches.
     */
    fun resolveTournamentRoundSequence(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        return getRoundSequence(event, allMatches)
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

        val startIndex = allMatches.mapNotNull { match ->
            fullSequence.indexOfFirst { it.name == match.round }.takeIf { it >= 0 }
        }.minOrNull() ?: 0

        return fullSequence.subList(startIndex, fullSequence.size)
    }

    /**
     * Ermittelt den aktuellen Status ([TournamentRoundPhase]) der aktiven Qualifikationsrunde.
     */
    fun resolveActiveQualificationPhase(
        event: TournamentEvent,
        round: TournamentRound?
    ): TournamentRoundPhase {
        return if (round != null) {
            TournamentRoundPhase.ACTIVE
        } else {
            TournamentRoundPhase.COMPLETED
        }
    }

    /**
     * Sorts a list of player IDs based on Order of Merit ranking, prize money, skill level, and name.
     */
    fun sortPlayerIdsByOom(
        playerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): List<Long> {
        val oomMap = oomEntries.associateBy { it.playerId }
        val dbPlayerMap = allPlayers.associateBy { it.id }

        return playerIds.sortedWith(
            compareBy<Long> { oomMap[it]?.rank ?: Int.MAX_VALUE }
                .thenByDescending { oomMap[it]?.prizeMoney ?: 0.0 }
                .thenByDescending {
                    if (it == USER_ID) SkillLevel.DEFAULT.value else dbPlayerMap[it]?.skill ?: SkillLevel.DEFAULT.value
                }
                .thenBy {
                    if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: ""
                }
        )
    }

    /**
     * Resolves prequalified Tour Card holders (top OOM and top Pro Tour OOM) for events or qualifiers.
     * Returns a pair of (topOomIds, topPtOomIds).
     */
    fun resolvePrequalifiedTourCardIds(
        event: TournamentEvent,
        tourCardIds: List<Long>,
        mainOom: List<OrderOfMeritEntry>,
        proTourOom: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): Pair<List<Long>, List<Long>> {
        val isWorldChampionship = event == TournamentEvent.QUALIFIER_WM || event == TournamentEvent.WORLD_CHAMPIONSHIP
        val targetOomCount = if (isWorldChampionship) 40 else 16
        val targetPtOomCount = if (isWorldChampionship) 40 else 16

        val sortedOomTc = sortPlayerIdsByOom(tourCardIds, mainOom, allPlayers)
        val topOomIds = sortedOomTc.take(targetOomCount)
        val remainingTcAfterOom = tourCardIds.filterNot { it in topOomIds }
        val topPtOomIds = sortPlayerIdsByOom(remainingTcAfterOom, proTourOom, allPlayers).take(targetPtOomCount)

        return Pair(topOomIds, topPtOomIds)
    }

    /**
     * Fills an initial list of player IDs to a target count using fallback candidates sorted by Order of Merit.
     */
    fun fillToTargetCount(
        initialList: List<Long>,
        targetCount: Int,
        fallbackCandidates: List<Long>,
        oomList: List<OrderOfMeritEntry>
    ): List<Long> {
        if (initialList.size >= targetCount) {
            return initialList.take(targetCount)
        }

        val result = initialList.toMutableList()
        val oomMap = oomList.associateBy { it.playerId }
        val remainingCandidates = fallbackCandidates
            .filterNot { it in result }
            .sortedWith(
                compareBy<Long> { oomMap[it]?.rank ?: Int.MAX_VALUE }
                    .thenByDescending { oomMap[it]?.prizeMoney ?: 0.0 }
                    .thenByDescending { oomMap[it]?.skill ?: SkillLevel.DEFAULT.value }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }

    /**
     * Overload von [fillToTargetCount], um Spielerpools mit Ausschlusslisten und Spielerobjekten aufzufüllen.
     */
    fun fillToTargetCount(
        currentIds: List<Long>,
        targetCount: Int,
        oomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        excludedIds: Set<Long> = emptySet(),
        tourCardOnlyIds: Set<Long>? = null
    ): List<Long> {
        val baseCandidates = (tourCardOnlyIds?.toList() ?: (listOf(USER_ID) + allPlayers.map { it.id })).filterNot { it in excludedIds }
        return fillToTargetCount(currentIds, targetCount, baseCandidates, oomEntries)
    }
}
