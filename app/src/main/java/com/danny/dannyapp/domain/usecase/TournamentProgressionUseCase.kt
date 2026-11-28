package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.USER_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase orchestrating the overall progression of tournaments, including CPU simulation,
 * user match simulation, and continuous round advancement.
 */
class TournamentProgressionUseCase(
    private val completeMatchUseCase: CompleteMatchUseCase,
    private val advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase,
    private val tournamentRepository: TournamentRepository,
    private val generateBracketUseCase: GenerateBracketUseCase? = null,
    private val settingsManager: SettingsManager
) {

    /**
     * Startet ein Turnier: Generiert den Turnierbaum, falls noch keine Matches existieren,
     * und wendet anschließend den automatischen Fortschritt an.
     *
     * @param tournamentId Die ID des zu startenden Turniers.
     */
    suspend fun startTournament(tournamentId: Long) = withContext(Dispatchers.IO) {
        val existingMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
        if (existingMatches.isEmpty()) {
            requireNotNull(generateBracketUseCase) { "GenerateBracketUseCase fehlt" }.invoke(tournamentId)
        }
        applyAutoProgress(tournamentId)
    }

    /**
     * Führt automatischen Rundenfortschritt und CPU-Simulationen aus,
     * basierend auf den Einstellungen in SettingsManager.
     *
     * @param tournamentId Die ID des Turniers.
     * @return `true` wenn mindestens einmal die Runde vorgerückt wurde, ansonsten `false`.
     */
    suspend fun applyAutoProgress(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        var loopCount = 0
        var roundAdvanced = false
        val maxLoopCount = 200

        while (loopCount < maxLoopCount) {
            loopCount++
            val tournament = tournamentRepository.getTournamentById(tournamentId)
            if (tournament.isCompleted || tournament.currentRound == null) break

            val state = requireNotNull(advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)) {
                "Turnier-Status für laufendes Turnier $tournamentId konnte nicht ermittelt werden"
            }

            if (settingsManager.autoSimulateCpu && state.hasOpenCpuMatchesInActiveRound) {
                completeMatchUseCase.simulateAllOpenCpuMatches(tournamentId, stopAtUserMatch = true)
                continue
            }

            val updatedState = requireNotNull(advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)) {
                "Turnier-Status für laufendes Turnier $tournamentId konnte nicht ermittelt werden"
            }

            if (settingsManager.autoAdvanceRound && updatedState.canAdvanceRound) {
                advanceTournamentRoundUseCase(tournamentId)
                roundAdvanced = true
                continue
            }

            break
        }
        roundAdvanced
    }

    /**
     * Simulates an entire tournament from start to finish.
     * Generates bracket if empty, simulates all CPU matches, realistically simulates user matches,
     * and advances rounds until the tournament is marked completed.
     * Always executes round advancement in this batch mode to ensure the simulation loop completes.
     *
     * @param tournamentId The ID of the tournament to simulate completely.
     * @return `true` if the tournament completed successfully, `false` otherwise.
     */
    suspend fun simulateCompleteTournament(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        var tournament = tournamentRepository.getTournamentById(tournamentId)
        if (tournament.isCompleted) return@withContext true

        val existingMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
        if (existingMatches.isEmpty()) {
            generateBracketUseCase?.invoke(tournamentId)
        }

        var loopCount = 0
        val maxLoopCount = 200

        while (loopCount < maxLoopCount) {
            loopCount++
            tournament = tournamentRepository.getTournamentById(tournamentId)
            if (tournament.isCompleted) return@withContext true

            val state = checkNotNull(advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)) {
                "Turnier-Status konnte für ID $tournamentId nicht ermittelt werden"
            }
            val activeRoundKey = checkNotNull(state.activeRoundKey) { "Aktive Runde fehlt für laufendes Turnier $tournamentId" }

            completeMatchUseCase.simulateAllOpenCpuMatches(tournamentId)

            val currentMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
            val openUserMatches = currentMatches.filter {
                it.round == activeRoundKey && !it.isCompleted && (it.player1Id == USER_ID || it.player2Id == USER_ID)
            }

            for (match in openUserMatches) {
                val isPlayer1User = (match.player1Id == USER_ID)
                val opponentId = if (isPlayer1User) match.player2Id else match.player1Id
                require(opponentId > 0L || opponentId == USER_ID) {
                    "Unaufgelöste Gegner-ID $opponentId in aktivem User-Match ${match.id} (Turnier $tournamentId, Runde $activeRoundKey)"
                }

                completeMatchUseCase.simulateMatch(match, includeUserMatches = true)
            }

            val updatedState = advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)
            val hasNewPlayableMatchesInRound = tournamentRepository.getMatchesListForTournament(tournamentId).any {
                it.round == activeRoundKey && !it.isCompleted && (it.player1Id > 0L || it.player1Id == USER_ID) && (it.player2Id > 0L || it.player2Id == USER_ID)
            }

            if (updatedState != null && updatedState.canAdvanceRound) {
                advanceTournamentRoundUseCase(tournamentId)
            } else if (hasNewPlayableMatchesInRound) {
                // Continue loop to simulate newly unlocked matches in the same round (e.g., Grand Slam of Darts group stage days 2/3)
                continue
            } else {
                val openMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
                    .count { it.round == activeRoundKey && !it.isCompleted }
                error("Turnier $tournamentId steckt in Runde '$activeRoundKey' fest: $openMatches offene Matches verbleiben")
            }
        }

        val finalTournament = tournamentRepository.getTournamentById(tournamentId)
        check(finalTournament.isCompleted) {
            "Turnier $tournamentId wurde nach $loopCount Durchläufen nicht abgeschlossen (Runde: ${finalTournament.currentRound})"
        }
        return@withContext true
    }

    /**
     * Simuliert alle nicht abgeschlossenen Turniere einer Saison chronologisch bis einschließlich zum angegebenen Zielturnier.
     *
     * @param targetTournamentId ID des Zielturniers, bis zu dem simuliert werden soll.
     * @param onProgress Callback für Fortschritts-Updates (aktueller Index, Gesamtanzahl, Name des Turniers).
     */
    suspend fun simulateSeasonUpToTournament(
        targetTournamentId: Long,
        onProgress: suspend (currentIndex: Int, totalCount: Int, tournamentName: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val targetTournament = tournamentRepository.getTournamentById(targetTournamentId)
        val seasonTournaments = tournamentRepository.getTournamentsListForSeason(targetTournament.seasonId)
        val toSimulate = seasonTournaments
            .filter { !it.isCompleted && it.order <= targetTournament.order }
            .sortedBy { it.order }

        if (toSimulate.isEmpty()) return@withContext

        for (index in 0 until toSimulate.size) {
            val tournament = toSimulate[index]
            onProgress(index + 1, toSimulate.size, tournament.name)
            simulateCompleteTournament(tournament.id)
        }
    }
}
