package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentParticipantDao
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.TournamentProgressState
import com.danny.dannyapp.domain.model.TournamentRoundPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase to evaluate tournament progress, advance tournament rounds, and record prize money placements
 * based on the winner-based placement model (instant entry prize money + immediate winner promotions).
 */
class AdvanceTournamentRoundUseCase(
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentPlacementDao: TournamentPlacementDao,
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase,
    private val tournamentQualificationUseCase: TournamentQualificationUseCase
) {
    /** Single source of truth for group-stage standings – shared with the advance logic. */
    private val calculateGroupStandingsUseCase: CalculateGroupStandingsUseCase = CalculateGroupStandingsUseCase()
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao(),
        GetOrderOfMeritUseCase(context),
        TournamentQualificationUseCase(context)
    )

    /**
     * Ermittelt den aktuellen Zustand eines Turniers direkt aus der Datenbank (Single Source of Truth).
     * Die aktive Runde wird vorrangig aus dem am Turnier persistierten Rundenschlüssel gelesen;
     * als Fallback für ältere Datensätze ohne persistierten Rundenschlüssel wird die erste Runde
     * mit offenen oder noch nicht verbuchten Matches herangezogen.
     *
     * @param tournamentId Die ID des zu bewertenden Turniers.
     * @return [TournamentProgressState] mit Rundenstatus und Fortschrittsinfos, oder null wenn kein Turnier/Matches gefunden.
     */
    suspend fun evaluateTournamentState(tournamentId: Long): TournamentProgressState? = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext null
        val allMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (allMatches.isEmpty()) return@withContext null

        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return@withContext null }
        val fullSequence = event.getRoundSequence()
        val firstRoundInMatches = fullSequence.firstOrNull { round -> allMatches.any { it.round == round.name } } ?: fullSequence.first()
        val roundSequence = fullSequence.dropWhile { it != firstRoundInMatches }

        if (tournament.isCompleted) {
            return@withContext TournamentProgressState(
                activeRound = null,
                roundPhases = roundSequence.associateWith { TournamentRoundPhase.COMPLETED },
                canAdvanceRound = false,
                isFinalRound = false,
                hasOpenCpuMatchesInActiveRound = false
            )
        }

        val activeRound: TournamentRound = tournament.currentRound
            ?.let { roundKey -> TournamentRound.entries.find { it.name == roundKey } }
            ?: roundSequence.firstOrNull { round ->
                val roundMatches = allMatches.filter { it.round == round.name }
                roundMatches.isNotEmpty() && roundMatches.any { !it.isCompleted }
            }
            ?: roundSequence.firstOrNull { round ->
                allMatches.any { it.round == round.name }
            }
            ?: return@withContext null

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
     * @param tournamentId Die ID des weiterzuschaltenden Turniers.
     * @return `true` wenn der Rundenwechsel oder Turnierabschluss erfolgreich ausgeführt wurde, sonst `false`.
     */
    suspend operator fun invoke(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        val state = evaluateTournamentState(tournamentId) ?: return@withContext false
        if (!state.canAdvanceRound) return@withContext false
        val activeRound = state.activeRound ?: return@withContext false

        val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext false
        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return@withContext false }
        val allMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)

        if (state.isFinalRound) {
            if (event !in listOf(TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)) {
                val finalMatches = allMatches.filter { it.round == TournamentRound.F.name || it.round == TournamentRound.QUAL_FINAL.name }
                val finalMatch = finalMatches.firstOrNull()
                val winnerId = finalMatch?.winnerId
                if (winnerId != null) {
                    tournamentPlacementDao.upsertPlayerPlacement(
                        tournamentId = tournamentId,
                        playerId = winnerId,
                        reachedRound = TournamentRound.WINNER.name,
                        prizeMoney = event.getRankingPrizeMoney(TournamentRound.WINNER)
                    )
                }
            }
            tournamentDao.updateTournament(tournament.copy(isCompleted = true, currentRound = null))
            return@withContext true
        }

        advanceToNextRound(
            tournament = tournament,
            event = event,
            currentRound = activeRound,
            allMatches = allMatches
        )

        val roundSequence = event.getRoundSequence()
        val activeIndex = roundSequence.indexOf(activeRound)
        val nextRound = if (activeIndex >= 0) roundSequence.getOrNull(activeIndex + 1) else null
        if (nextRound != null) {
            tournamentDao.updateTournament(tournament.copy(currentRound = nextRound.name))
        }

        return@withContext true
    }

    /**
     * Executes round progression by setting participants into the next round matches
     * or drawing subsequent rounds for unseeded open draws (UK Open / European Open).
     */
    private suspend fun advanceToNextRound(
        tournament: Tournament,
        event: TournamentEvent,
        currentRound: TournamentRound,
        allMatches: List<TournamentMatch>
    ) {
        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRound == TournamentRound.GROUP) {
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val l16Matches = allMatches.filter { it.round == TournamentRound.L16.name }.sortedBy { it.matchIndex }
            if (groupMatches.size == 48 && l16Matches.isNotEmpty()) {
                // Delegate all standings calculation to the domain use-case (single source of truth).
                val standingsByGroup = calculateGroupStandingsUseCase(groupMatches)

                val groupWinners = mutableListOf<Long>()
                for (groupIdx in 0 until 16) {
                    val groupLetter = ('A' + groupIdx).toString()
                    val sortedGroup = standingsByGroup[groupLetter] ?: emptyList()

                    val groupWinner = sortedGroup.firstOrNull()
                    if (groupWinner != null) {
                        groupWinners.add(groupWinner.playerId)
                        tournamentPlacementDao.upsertPlayerPlacement(
                            tournamentId = tournament.id,
                            playerId = groupWinner.playerId,
                            reachedRound = TournamentRound.L16.name,
                            prizeMoney = event.getRankingPrizeMoney(TournamentRound.L16)
                        )
                    }
                    val groupRunnerUp = sortedGroup.getOrNull(1)
                    if (groupRunnerUp != null) {
                        tournamentPlacementDao.upsertPlayerPlacement(
                            tournamentId = tournament.id,
                            playerId = groupRunnerUp.playerId,
                            reachedRound = TournamentRound.GROUP_2ND.name,
                            prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_2ND)
                        )
                    }
                }

                val updatedL16 = l16Matches.mapIndexed { index, match ->
                    val player1Id = groupWinners.getOrElse(2 * index) { 0L }
                    val player2Id = groupWinners.getOrElse(2 * index + 1) { 0L }
                    match.copy(player1Id = player1Id, player2Id = player2Id)
                }
                tournamentMatchDao.insertMatches(updatedL16)
            }
            return
        }

        if (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) {
            val roundSequence = event.getRoundSequence()
            val currentIndex = roundSequence.indexOf(currentRound)
            if (currentIndex == -1 || currentIndex >= roundSequence.size - 1) return
            val nextRoundEnum = roundSequence[currentIndex + 1]

            val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
            val winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
            val shuffledWinners = winners.shuffled()

            val season = seasonDao.getSeasonById(tournament.seasonId)
            val careerId = season?.careerId ?: 0L
            val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season?.id ?: 0L)
            val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()
            val allPlayers = playerDao.getAllPlayersList()
            val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)

            val participants = tournamentParticipantDao.getParticipantsForTournament(tournament.id)
            val stored65To96 = participants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val stored33To64 = participants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val storedTop32 = participants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

            val sortedTcOom = tournamentQualificationUseCase.sortPlayerIdsByOom(tourCardPlayerIds, oomEntries, allPlayers)
            val fullOom = sortedTcOom
            val oom65To96 = if (stored65To96.isNotEmpty()) stored65To96 else fullOom.drop(64).take(32)
            val oom33To64 = if (stored33To64.isNotEmpty()) stored33To64 else fullOom.drop(32).take(32)
            val top32Oom = if (storedTop32.isNotEmpty()) storedTop32 else fullOom.take(32)

            val nextMatches = when (currentRound) {
                TournamentRound.R1_160 -> {
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.R2_128.name,
                            matchIndex = index,
                            player1Id = oom65To96.getOrElse(index) { 0L },
                            player2Id = shuffledWinners.getOrElse(index) { 0L }
                        )
                    }
                }
                TournamentRound.R2_128 -> {
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.R3_96.name,
                            matchIndex = index,
                            player1Id = oom33To64.getOrElse(index) { 0L },
                            player2Id = shuffledWinners.getOrElse(index) { 0L }
                        )
                    }
                }
                TournamentRound.R3_96 -> {
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.L64.name,
                            matchIndex = index,
                            player1Id = top32Oom.getOrElse(index) { 0L },
                            player2Id = shuffledWinners.getOrElse(index) { 0L }
                        )
                    }
                }
                else -> generateStandardKoMatches(tournament.id, nextRoundEnum, shuffledWinners)
            }
            if (nextMatches.isNotEmpty()) {
                tournamentMatchDao.insertMatches(nextMatches)
            }
            return
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && currentRound == TournamentRound.L48) {
            val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
            val existingL32 = allMatches.filter { it.round == TournamentRound.L32.name }.sortedBy { it.matchIndex }
            val l48Winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
            val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
            val updatedL32 = existingL32.mapIndexed { index, match ->
                val seedIndex = seedOrder[index]
                val winnerId = l48Winners.getOrNull(15 - seedIndex) ?: 0L
                match.copy(player2Id = winnerId)
            }
            if (updatedL32.isNotEmpty()) {
                tournamentMatchDao.insertMatches(updatedL32)
            }
            return
        }

        val roundSequence = event.getRoundSequence()
        val currentIndex = roundSequence.indexOf(currentRound)
        if (currentIndex == -1 || currentIndex >= roundSequence.size - 1) return
        val nextRoundEnum = roundSequence[currentIndex + 1]

        val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
        val winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
        val existingNext = allMatches.filter { it.round == nextRoundEnum.name }.sortedBy { it.matchIndex }

        if (existingNext.isNotEmpty()) {
            val updatedNext = existingNext.mapIndexed { index, match ->
                val player1Id = winners.getOrNull(2 * index) ?: match.player1Id
                val player2Id = winners.getOrNull(2 * index + 1) ?: match.player2Id
                match.copy(player1Id = player1Id, player2Id = player2Id)
            }
            tournamentMatchDao.insertMatches(updatedNext)
        }
    }

    /**
     * Propagates the winner of a completed match immediately into the corresponding next round slot
     * and upgrades their placement immediately to the reached round.
     */
    suspend fun propagateWinnerOnMatchCompleted(match: TournamentMatch) = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return@withContext
        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return@withContext }
        if (event in listOf(TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)) {
            return@withContext
        }
        val winnerId = match.winnerId ?: return@withContext
        val roundSequence = event.getRoundSequence()
        val currentRoundEnum = try { TournamentRound.valueOf(match.round) } catch (_: Exception) { null }

        if (currentRoundEnum != null && currentRoundEnum != TournamentRound.GROUP) {
            val currentIdx = roundSequence.indexOf(currentRoundEnum)
            if (currentIdx >= 0) {
                val reachedRound = if (currentRoundEnum == TournamentRound.F) {
                    TournamentRound.WINNER
                } else {
                    roundSequence.getOrNull(currentIdx + 1)
                }
                if (reachedRound != null) {
                    tournamentPlacementDao.upsertPlayerPlacement(
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

        val allMatches = tournamentMatchDao.getMatchesListForTournament(match.tournamentId)

        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && match.round == TournamentRound.GROUP.name && match.matchIndex in 0..15) {
            val groupIdx = match.matchIndex
            val groupLetter = ('A' + groupIdx).toString()
            val loserId = if (match.player1Id == winnerId) match.player2Id else match.player1Id
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val day2Match = groupMatches.find { it.groupName == groupLetter && it.matchIndex == 16 + groupIdx }
            val day3Match = groupMatches.find { it.groupName == groupLetter && it.matchIndex == 32 + groupIdx }
            if (day2Match != null) {
                tournamentMatchDao.updateMatch(day2Match.copy(player2Id = loserId))
            }
            if (day3Match != null) {
                tournamentMatchDao.updateMatch(day3Match.copy(player2Id = winnerId))
            }
            return@withContext
        }

        if (match.round == TournamentRound.QUAL_PRELIM.name) {
            val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }
            val placeholderId = -1000L - match.matchIndex
            for (qualMatch in qualR1Matches) {
                var updated = qualMatch
                var changed = false
                if (qualMatch.player1Id == placeholderId) {
                    updated = updated.copy(player1Id = winnerId)
                    changed = true
                }
                if (qualMatch.player2Id == placeholderId) {
                    updated = updated.copy(player2Id = winnerId)
                    changed = true
                }
                if (changed) {
                    tournamentMatchDao.updateMatch(updated)
                }
            }
            return@withContext
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && match.round == TournamentRound.L48.name) {
            val l32Matches = allMatches.filter { it.round == TournamentRound.L32.name }
            val targetIdx = BracketGenerator.BRACKET_SEED_ORDER_16.indexOf(15 - match.matchIndex)
            val l32Match = l32Matches.find { it.matchIndex == targetIdx }
            if (l32Match != null) {
                tournamentMatchDao.updateMatch(l32Match.copy(player2Id = winnerId))
            }
            return@withContext
        }

        val currentIdx = roundSequence.indexOf(currentRoundEnum)
        if (currentIdx < 0) return@withContext
        val nextRoundEnum = roundSequence.getOrNull(currentIdx + 1) ?: return@withContext
        val nextMatches = allMatches.filter { it.round == nextRoundEnum.name }
        val targetMatchIndex = match.matchIndex / 2
        val nextMatch = nextMatches.find { it.matchIndex == targetMatchIndex }
        if (nextMatch != null) {
            val updatedNext = if (match.matchIndex % 2 == 0) {
                nextMatch.copy(player1Id = winnerId)
            } else {
                nextMatch.copy(player2Id = winnerId)
            }
            tournamentMatchDao.updateMatch(updatedNext)
        }
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
