package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.TournamentProgressState
import com.danny.dannyapp.domain.model.TournamentRoundPhase
import com.danny.dannyapp.domain.strategy.TournamentRuleStrategy
import com.danny.dannyapp.domain.strategy.TournamentRuleStrategyRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase to evaluate tournament progress, advance tournament rounds, and record prize money placements
 * based on the winner-based placement model (instant entry prize money + immediate winner promotions).
 */
class AdvanceTournamentRoundUseCase(
    private val tournamentRepository: TournamentRepository
) {
    /** Single source of truth for group-stage standings – shared with the advance logic. */
    private val calculateGroupStandingsUseCase: CalculateGroupStandingsUseCase = CalculateGroupStandingsUseCase()

    companion object {
        /**
         * Resolves the target match index and slot in the next round for a given match.
         * Returns (targetMatchIndex, isPlayer1Slot).
         */
        fun resolveNextRoundSlot(event: TournamentEvent, currentRound: TournamentRound, matchIndex: Int): Pair<Int, Boolean> {
            return when {
                event == TournamentEvent.EUROPEAN_TOUR && currentRound == TournamentRound.L48 -> {
                    Pair(BracketGenerator.BRACKET_SEED_ORDER_16.indexOf(15 - matchIndex), false)
                }
                else -> {
                    Pair(matchIndex / 2, matchIndex % 2 == 0)
                }
            }
        }
    }

    /**
     * Ermittelt den aktuellen Zustand eines Turniers direkt aus der Datenbank (Single Source of Truth).
     * Die aktive Runde wird vorrangig aus dem am Turnier persistierten Rundenschlüssel gelesen;
     * als Fallback für ältere Datensätze ohne persistierten Rundenschlüssel wird die erste Runde
     * mit offenen oder noch nicht verbuchten Matches herangezogen.
     *
     * @param tournamentId Die ID des zu bewertenden Turniers.
     * @return [TournamentProgressState] mit Rundenstatus und Fortschrittsinfos, oder null, wenn keine Matches für geplante Turniere existieren.
     */
    suspend fun evaluateTournamentState(tournamentId: Long): TournamentProgressState? = withContext(Dispatchers.IO) {
        val tournament = tournamentRepository.getTournamentById(tournamentId)
        val allMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
        if (allMatches.isEmpty()) return@withContext null

        val event = TournamentEvent.valueOf(tournament.type)
        val roundSequence = QualificationHelper.getRoundSequence(event, allMatches)

        if (tournament.isCompleted) {
            return@withContext TournamentProgressState(
                activeRound = null,
                roundPhases = roundSequence.associateWith { TournamentRoundPhase.COMPLETED },
                canAdvanceRound = false,
                isFinalRound = false,
                hasOpenCpuMatchesInActiveRound = false
            )
        }

        val currentRoundKey = requireNotNull(tournament.currentRound) {
            "Aktive Runde fehlt für laufendes Turnier $tournamentId"
        }
        val activeRound: TournamentRound = requireNotNull(TournamentRound.entries.find { it.name == currentRoundKey }) {
            "Ungültige Runde $currentRoundKey für Turnier $tournamentId"
        }

        val roundPhases = roundSequence.associateWith { round ->
            when {
                round == activeRound -> TournamentRoundPhase.ACTIVE
                roundSequence.indexOf(round) < roundSequence.indexOf(activeRound) -> TournamentRoundPhase.COMPLETED
                else -> TournamentRoundPhase.PENDING
            }
        }

        val activeRoundMatches = allMatches.filter { it.round == activeRound.name }
        val canAdvanceRound = activeRoundMatches.isNotEmpty() && activeRoundMatches.all { it.isCompleted }
        val hasOpenCpuMatches = activeRoundMatches.any { match ->
            !match.isCompleted && match.player1Id > 0L && match.player2Id > 0L
        }
        val isFinalRound = (activeRound == TournamentRound.F || activeRound == TournamentRound.QUAL_FINAL)

        return@withContext TournamentProgressState(
            activeRound = activeRound,
            roundPhases = roundPhases,
            canAdvanceRound = canAdvanceRound,
            isFinalRound = isFinalRound,
            hasOpenCpuMatchesInActiveRound = hasOpenCpuMatches
        )
    }

    /**
     * Schaltet das Turnier zur nächsten Runde weiter, sofern alle Matches der aktiven Runde abgeschlossen sind.
     * Bei der Finalrunde wird das Turnier abgeschlossen, der Finalsieger garantiert hochgestuft
     * und die aktive Runde am Turnier auf null gesetzt.
     *
     * @param tournamentId Die ID des weiter zuschaltenden Turniers.
     * @return `true` wenn der Rundenwechsel oder Turnierabschluss erfolgreich ausgeführt wurde, sonst `false`.
     */
    suspend operator fun invoke(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        tournamentRepository.runInTransaction {
            val state = evaluateTournamentState(tournamentId) 
                ?: error("Turnier $tournamentId hat keine Matches für Rundenwechsel")
            check(state.canAdvanceRound) {
                "Runde '${state.activeRound?.name ?: "unbekannt"}' in Turnier $tournamentId ist noch nicht abgeschlossen"
            }
            val activeRound = requireNotNull(state.activeRound) {
                "Aktive Runde fehlt für Turnier $tournamentId"
            }

            val tournament = tournamentRepository.getTournamentById(tournamentId)
            val event = TournamentEvent.valueOf(tournament.type)
            val strategy = TournamentRuleStrategyRegistry.getStrategy(event)
            val allMatches = tournamentRepository.getMatchesListForTournament(tournamentId)

            if (state.isFinalRound) {
                if (strategy.awardsPrizeMoneyAndPlacements) {
                    val finalMatches = allMatches.filter { it.round == TournamentRound.F.name || it.round == TournamentRound.QUAL_FINAL.name }
                    val finalMatch = finalMatches.firstOrNull()
                    val winnerId = finalMatch?.winnerId
                    if (winnerId != null) {
                        tournamentRepository.upsertPlayerPlacement(
                            tournamentId = tournamentId,
                            playerId = winnerId,
                            reachedRound = TournamentRound.WINNER.name,
                            prizeMoney = event.getRankingPrizeMoney(TournamentRound.WINNER)
                        )
                    }
                }
                tournamentRepository.updateTournament(tournament.copy(isCompleted = true, currentRound = null))
                return@runInTransaction true
            }

            advanceToNextRound(
                tournament = tournament,
                event = event,
                strategy = strategy,
                currentRound = activeRound,
                allMatches = allMatches
            )

            val roundSequence = QualificationHelper.getRoundSequence(event, allMatches)
            val activeIndex = roundSequence.indexOf(activeRound)
            val nextRound = if (activeIndex >= 0) roundSequence.getOrNull(activeIndex + 1) else null
            if (nextRound != null) {
                tournamentRepository.updateTournament(tournament.copy(currentRound = nextRound.name))
            }

            true
        }
    }

    /**
     * Setzt die Turnierteilnehmer der abgeschlossenen [currentRound] in die Matches der Folgerunde ein.
     * Für offene Auslosungen (UK Open / European Open) werden die nächsten Paarungen frisch ausgelost.
     *
     * Bei allen Nicht-Qualifier-Turnieren und Nicht-Gruppenrunden wird zusätzlich für jeden Sieger
     * das Placement auf die Folgerunde hochgestuft (`nextRoundEnum` mit dem entsprechenden Preisgeld).
     * Damit ist sichergestellt, dass vorqualifizierte Spieler (z. B. Top-16-OOM auf der European Tour,
     * die mit `prizeMoney = 0.0` starten) nach einem Sieg in `L48` oder `L32` nicht ohne
     * Preisgeld-Eintrag bleiben.
     */
    private suspend fun advanceToNextRound(
        tournament: Tournament,
        event: TournamentEvent,
        strategy: TournamentRuleStrategy,
        currentRound: TournamentRound,
        allMatches: List<TournamentMatch>
    ) {
        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRound == TournamentRound.GROUP) {
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val l16Matches = allMatches.filter { it.round == TournamentRound.L16.name }.sortedBy { it.matchIndex }
            check(groupMatches.size == 48 && l16Matches.size == 8) {
                "Grand Slam Gruppenphase erfordert genau 48 Gruppen-Matches und 8 L16-Matches (gefunden: ${groupMatches.size} Gruppen-Matches, ${l16Matches.size} L16-Matches)"
            }
            // Delegate all standings calculation to the domain use-case (single source of truth).
            val standingsByGroup = calculateGroupStandingsUseCase(groupMatches)

            val groupWinners = mutableListOf<Long>()
            for (groupIndex in 0 until 16) {
                val groupLetter = ('A' + groupIndex).toString()
                val sortedGroup = standingsByGroup[groupLetter] ?: emptyList()

                val groupWinner = sortedGroup.firstOrNull()
                if (groupWinner != null) {
                    groupWinners.add(groupWinner.playerId)
                    tournamentRepository.upsertPlayerPlacement(
                        tournamentId = tournament.id,
                        playerId = groupWinner.playerId,
                        reachedRound = TournamentRound.L16.name,
                        prizeMoney = event.getRankingPrizeMoney(TournamentRound.L16)
                    )
                }
                val groupRunnerUp = sortedGroup.getOrNull(1)
                if (groupRunnerUp != null) {
                    tournamentRepository.upsertPlayerPlacement(
                        tournamentId = tournament.id,
                        playerId = groupRunnerUp.playerId,
                        reachedRound = TournamentRound.GROUP_2ND.name,
                        prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_2ND)
                    )
                }
            }

            require(groupWinners.size == 16) { "Grand Slam Gruppenphase erfordert genau 16 Gruppensieger" }
            val updatedL16 = l16Matches.mapIndexed { index, match ->
                val player1Id = groupWinners[2 * index]
                val player2Id = groupWinners[2 * index + 1]
                match.copy(player1Id = player1Id, player2Id = player2Id)
            }
            tournamentRepository.insertMatches(updatedL16)
            return
        }

        if (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) {
            val roundSequence = QualificationHelper.getRoundSequence(event, allMatches)
            val currentIndex = roundSequence.indexOf(currentRound)
            check(currentIndex in 0 until roundSequence.lastIndex) {
                "Ungültiger Rundenwechsel für $event von Runde $currentRound (Index $currentIndex in Sequenz mit ${roundSequence.size} Runden)"
            }
            val nextRoundEnum = roundSequence[currentIndex + 1]

            val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
            val winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
            val shuffledWinners = winners.shuffled()

            val participants = tournamentRepository.getParticipantsForTournament(tournament.id)
            val oom65To96 = participants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val oom33To64 = participants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val top32Oom = participants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

            val nextMatches = when (currentRound) {
                TournamentRound.R1_160 -> {
                    require(oom65To96.size == 32 && shuffledWinners.size == 32) {
                        "UK/European Open R2 erfordert 32 OOM (65-96) und 32 R1-Sieger"
                    }
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.R2_128.name,
                            matchIndex = index,
                            player1Id = oom65To96[index],
                            player2Id = shuffledWinners[index]
                        )
                    }
                }
                TournamentRound.R2_128 -> {
                    require(oom33To64.size == 32 && shuffledWinners.size == 32) {
                        "UK/European Open R3 erfordert 32 OOM (33-64) und 32 R2-Sieger"
                    }
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.R3_96.name,
                            matchIndex = index,
                            player1Id = oom33To64[index],
                            player2Id = shuffledWinners[index]
                        )
                    }
                }
                TournamentRound.R3_96 -> {
                    require(top32Oom.size == 32 && shuffledWinners.size == 32) {
                        "UK/European Open L64 erfordert 32 Top-32-OOM und 32 R3-Sieger"
                    }
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.L64.name,
                            matchIndex = index,
                            player1Id = top32Oom[index],
                            player2Id = shuffledWinners[index]
                        )
                    }
                }
                else -> generateStandardKoMatches(tournament.id, nextRoundEnum, shuffledWinners)
            }
            if (nextMatches.isNotEmpty()) {
                tournamentRepository.insertMatches(nextMatches)
            }
            return
        }

        val roundSequence = QualificationHelper.getRoundSequence(event, allMatches)
        val currentIndex = roundSequence.indexOf(currentRound)
        check(currentIndex in 0 until roundSequence.lastIndex) {
            "Ungültiger Rundenwechsel für $event von Runde $currentRound (Index $currentIndex in Sequenz mit ${roundSequence.size} Runden)"
        }
        val nextRoundEnum = roundSequence[currentIndex + 1]

        val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
        val sortedWinners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
        val existingNextRoundMatches = allMatches.filter { it.round == nextRoundEnum.name }.sortedBy { it.matchIndex }

        check(existingNextRoundMatches.isNotEmpty()) {
            "Keine existierenden Matches für Folgerunde ${nextRoundEnum.name} in Turnier ${tournament.id} gefunden"
        }

        val updatedNextRoundMatches = existingNextRoundMatches.toMutableList()
        for (match in currentRoundMatches) {
            val winnerId = requireNotNull(match.winnerId) {
                "Match ${match.id} (Runde ${match.round}, Index ${match.matchIndex}) hat keinen winnerId beim Rundenwechsel"
            }
            val (targetIdx, isPlayer1Slot) = resolveNextRoundSlot(event, currentRound, match.matchIndex)
            val nextMatch = requireNotNull(updatedNextRoundMatches.find { it.matchIndex == targetIdx }) {
                "Ziel-Match mit Index $targetIdx in Folgerunde ${nextRoundEnum.name} für Match ${match.matchIndex} (${match.round}) nicht gefunden"
            }
            val updated = if (isPlayer1Slot) {
                nextMatch.copy(player1Id = winnerId)
            } else {
                nextMatch.copy(player2Id = winnerId)
            }
            val matchIndexInList = updatedNextRoundMatches.indexOf(nextMatch)
            updatedNextRoundMatches[matchIndexInList] = updated
        }
        tournamentRepository.insertMatches(updatedNextRoundMatches)

        // Preisgeld-Hochstufung beim Rundenwechsel: Jeder Sieger der abgeschlossenen Runde wird
        // auf die Folgerunde hochgestuft. Betrifft insbesondere vorqualifizierte European-Tour-
        // Spieler (Top-16 OOM in L32, Top-16 PT-OOM in L48), die mit prizeMoney = 0.0 starten
        // und nach einem Sieg in L48 oder L32 sonst ohne Preisgeld-Eintrag bleiben würden.
        if (strategy.awardsPrizeMoneyAndPlacements && currentRound != TournamentRound.GROUP) {
            val prizeMoneyForNextRound = event.getRankingPrizeMoney(nextRoundEnum)
            for (winnerId in sortedWinners) {
                tournamentRepository.upsertPlayerPlacement(
                    tournamentId = tournament.id,
                    playerId = winnerId,
                    reachedRound = nextRoundEnum.name,
                    prizeMoney = prizeMoneyForNextRound
                )
            }
        }
    }

    /**
     * Propagates the winner of a completed match immediately into the corresponding next round slot
     * and upgrades their placement immediately to the reached round.
     */
    suspend fun propagateWinnerOnMatchCompleted(match: TournamentMatch) = withContext(Dispatchers.IO) {
        val tournament = tournamentRepository.getTournamentById(match.tournamentId)
        val event = TournamentEvent.valueOf(tournament.type)
        val strategy = TournamentRuleStrategyRegistry.getStrategy(event)
        val allMatches = tournamentRepository.getMatchesListForTournament(match.tournamentId)
        val winnerId = requireNotNull(match.winnerId) { "Abgeschlossenes Match ${match.id} hat keinen winnerId" }
        val roundSequence = QualificationHelper.getRoundSequence(event, allMatches)
        val currentRoundEnum = TournamentRound.valueOf(match.round)

        if (strategy.awardsPrizeMoneyAndPlacements && currentRoundEnum != TournamentRound.GROUP) {
            val currentIdx = roundSequence.indexOf(currentRoundEnum)
            if (currentIdx >= 0) {
                val reachedRound = if (currentRoundEnum == TournamentRound.F) {
                    TournamentRound.WINNER
                } else {
                    roundSequence.getOrNull(currentIdx + 1)
                }
                if (reachedRound != null) {
                    tournamentRepository.upsertPlayerPlacement(
                        tournamentId = match.tournamentId,
                        playerId = winnerId,
                        reachedRound = reachedRound.name,
                        prizeMoney = event.getRankingPrizeMoney(reachedRound)
                    )
                }
            }
        }

        if (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) {
            return@withContext
        }

        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && match.round == TournamentRound.GROUP.name && match.matchIndex in 0..15) {
            val groupIdx = match.matchIndex
            val groupLetter = ('A' + groupIdx).toString()
            val loserId = if (match.player1Id == winnerId) match.player2Id else match.player1Id
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val day2Match = groupMatches.find { it.groupName == groupLetter && it.matchIndex == 16 + groupIdx }
            val day3Match = groupMatches.find { it.groupName == groupLetter && it.matchIndex == 32 + groupIdx }
            if (day2Match != null) {
                tournamentRepository.updateMatch(day2Match.copy(player2Id = loserId))
            }
            if (day3Match != null) {
                tournamentRepository.updateMatch(day3Match.copy(player2Id = winnerId))
            }
            return@withContext
        }

        if (currentRoundEnum == TournamentRound.F || currentRoundEnum == TournamentRound.QUAL_FINAL) {
            return@withContext
        }

        val currentIdx = roundSequence.indexOf(currentRoundEnum)
        check(currentIdx in 0 until roundSequence.lastIndex) {
            "Keine Folgerunde für Match ${match.id} (Runde $currentRoundEnum, Index $currentIdx in $event)"
        }
        val nextRoundEnum = roundSequence[currentIdx + 1]
        val nextMatches = allMatches.filter { it.round == nextRoundEnum.name }
        val (targetMatchIndex, isPlayer1Slot) = resolveNextRoundSlot(event, currentRoundEnum, match.matchIndex)
        val nextMatch = requireNotNull(nextMatches.find { it.matchIndex == targetMatchIndex }) {
            "Ziel-Match mit Index $targetMatchIndex in Folgerunde ${nextRoundEnum.name} für Match ${match.id} (${match.round}, Index ${match.matchIndex}) nicht gefunden"
        }
        val updatedNext = if (isPlayer1Slot) {
            nextMatch.copy(player1Id = winnerId)
        } else {
            nextMatch.copy(player2Id = winnerId)
        }
        tournamentRepository.updateMatch(updatedNext)
    }

    /**
     * Generates match pairings for an unseeded knockout round from a list of winners.
     */
    private fun generateStandardKoMatches(
        tournamentId: Long,
        nextRound: TournamentRound,
        winners: List<Long>
    ): List<TournamentMatch> {
        val nextMatchCount = winners.size / 2
        return (0 until nextMatchCount).map { index ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = nextRound.name,
                matchIndex = index,
                player1Id = winners[2 * index],
                player2Id = winners[2 * index + 1]
            )
        }
    }
}
