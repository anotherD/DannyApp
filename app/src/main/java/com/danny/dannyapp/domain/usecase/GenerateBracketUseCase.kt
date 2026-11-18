package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentParticipantDao
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case responsible for generating initial bracket matches, seeding, and participants for all tournament types.
 * Nutzt [TournamentQualificationUseCase] als Single Source of Truth für Qualifikationspools und Setzlisten.
 */
class GenerateBracketUseCase(
    private val tournamentDao: TournamentDao,
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val tournamentPlacementDao: TournamentPlacementDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase,
    private val tournamentQualificationUseCase: TournamentQualificationUseCase
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
        GetOrderOfMeritUseCase(context),
        TournamentQualificationUseCase(context)
    )

    suspend operator fun invoke(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(tournamentId)
            ?: throw IllegalArgumentException("Tournament not found: $tournamentId")
        val season = seasonDao.getSeasonById(tournament.seasonId)
            ?: throw IllegalArgumentException("Season not found for tournament: ${tournament.seasonId}")

        val existingMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (existingMatches.isNotEmpty()) {
            return@withContext existingMatches
        }

        val event = TournamentEvent.valueOf(tournament.type)
        val careerId = season.careerId

        // Helfer zur Ermittlung vorheriger Qualifier-Sieger
        suspend fun getQualifierWinnersFromPreceding(targetCount: Int): List<Long> {
            val prevTournament = tournamentDao.getTournamentsListForSeason(season.id)
                .find { it.order == tournament.order - 1 } ?: return emptyList()
            val prevMatches = tournamentMatchDao.getMatchesListForTournament(prevTournament.id)
            if (prevMatches.isEmpty()) return emptyList()

            val rounds = prevMatches.map { it.round }.distinct()
            val finalRoundName = rounds.lastOrNull { round ->
                prevMatches.filter { it.round == round }.all { it.isCompleted && it.winnerId != null }
            } ?: rounds.lastOrNull() ?: return emptyList()

            val finalRoundMatches = prevMatches.filter { it.round == finalRoundName }
            val winners = finalRoundMatches.mapNotNull { it.winnerId }
            if (winners.size >= targetCount) {
                return winners.take(targetCount)
            }
            return winners
        }

        val precedingWinners = when (event) {
            TournamentEvent.EUROPEAN_TOUR -> getQualifierWinnersFromPreceding(10)
            TournamentEvent.GRAND_SLAM_OF_DARTS -> getQualifierWinnersFromPreceding(8)
            TournamentEvent.WORLD_CHAMPIONSHIP -> getQualifierWinnersFromPreceding(4)
            else -> emptyList()
        }

        // Single Source of Truth: Qualifikations- & Teilnehmerpool-Ermittlung
        val qualResult = tournamentQualificationUseCase.resolveTournamentQualification(
            tournament = tournament,
            seasonId = season.id,
            careerId = careerId,
            precedingQualifierWinners = precedingWinners
        )

        val tournamentMatches = mutableListOf<TournamentMatch>()

        fun appendSubsequentKoRounds(startRound: TournamentRound, startMatchCount: Int) {
            val roundSequence = event.getRoundSequence()
            val startIndex = roundSequence.indexOf(startRound)
            if (startIndex == -1) return
            var currentCount = startMatchCount
            for (roundIdx in startIndex + 1 until roundSequence.size) {
                val roundEnum = roundSequence[roundIdx]
                currentCount /= 2
                for (matchIndex in 0 until currentCount) {
                    tournamentMatches.add(
                        TournamentMatch(
                            tournamentId = tournamentId,
                            round = roundEnum.name,
                            matchIndex = matchIndex,
                            player1Id = PLACEHOLDER_PREVIOUS_WINNER_ID,
                            player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID
                        )
                    )
                }
            }
        }

        when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L128.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L128, startMatches.size)
            }

            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM -> {
                val split = qualResult.qualifierFieldSplit
                    ?: throw IllegalStateException("QualifierFieldSplit missing for qualifier tournament $tournamentId")
                val boardCount = split.boardCount
                val r1MatchesList: List<TournamentMatch> = if (split.prelimPlayers.isNotEmpty()) {
                    val prelimMatchCount = split.prelimPlayers.size / 2
                    val prelimPlayers = split.prelimPlayers
                    for (matchIndex in 0 until prelimMatchCount) {
                        tournamentMatches.add(
                            TournamentMatch(
                                tournamentId = tournament.id,
                                round = TournamentRound.QUAL_PRELIM.name,
                                matchIndex = matchIndex,
                                player1Id = prelimPlayers[matchIndex],
                                player2Id = prelimPlayers[2 * prelimMatchCount - 1 - matchIndex]
                            )
                        )
                    }
                    val prelimPlaceholders = (0 until prelimMatchCount).map { matchIndex -> -1000L - matchIndex }
                    val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(boardCount, split.byePlayers, prelimPlaceholders)
                    r1Pairings.map { pairing ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.QUAL_R1.name,
                            matchIndex = pairing.matchIndex,
                            player1Id = pairing.player1Id,
                            player2Id = pairing.player2Id
                        )
                    }
                } else {
                    val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(boardCount, split.byePlayers, emptyList())
                    r1Pairings.map { pairing ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.QUAL_R1.name,
                            matchIndex = pairing.matchIndex,
                            player1Id = pairing.player1Id,
                            player2Id = pairing.player2Id
                        )
                    }
                }
                tournamentMatches.addAll(r1MatchesList)
                appendSubsequentKoRounds(TournamentRound.QUAL_R1, r1MatchesList.size)
            }

            TournamentEvent.EUROPEAN_TOUR -> {
                val l48Pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val l48Matches = l48Pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L48.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(l48Matches)

                val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
                val l32Matches = (0 until 16).map { matchIndex ->
                    val seedIndex = seedOrder[matchIndex]
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L32.name,
                        matchIndex = matchIndex,
                        player1Id = qualResult.oomSortedIds.getOrElse(seedIndex) { 0L },
                        player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID
                    )
                }
                tournamentMatches.addAll(l32Matches)
                appendSubsequentKoRounds(TournamentRound.L32, l32Matches.size)
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                tournamentMatches.addAll(pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = pairing.round.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id,
                        groupName = pairing.groupName
                    )
                })
            }

            TournamentEvent.WORLD_MATCHPLAY -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L32.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L32, startMatches.size)
            }

            TournamentEvent.WORLD_GRAND_PRIX -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L32.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L32, startMatches.size)
            }

            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L32.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L32, startMatches.size)
            }

            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val groupPairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val groupMatches = groupPairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.GROUP.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id,
                        groupName = pairing.groupName
                    )
                }
                tournamentMatches.addAll(groupMatches)

                for (groupIdx in 0 until 16) {
                    val groupLetter = ('A' + groupIdx).toString()
                    val pot2Player = qualResult.proTourSortedIds[groupIdx]
                    tournamentMatches.add(TournamentMatch(tournamentId = tournamentId, round = TournamentRound.GROUP.name, matchIndex = 16 + groupIdx, player1Id = pot2Player, player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID, groupName = groupLetter))
                    tournamentMatches.add(TournamentMatch(tournamentId = tournamentId, round = TournamentRound.GROUP.name, matchIndex = 32 + groupIdx, player1Id = pot2Player, player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID, groupName = groupLetter))
                }

                val l16Matches = (0 until 8).map { matchIndex ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L16.name,
                        matchIndex = matchIndex,
                        player1Id = -9000L - (2 * matchIndex),
                        player2Id = -9000L - (2 * matchIndex + 1)
                    )
                }
                tournamentMatches.addAll(l16Matches)
                appendSubsequentKoRounds(TournamentRound.L16, l16Matches.size)
            }

            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L64.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L64, startMatches.size)
            }

            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L128.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L128, startMatches.size)
            }
        }

        val participants = qualResult.participants
        val uniqueParticipants = participants.distinctBy { it.playerId }
        tournamentParticipantDao.insertParticipants(uniqueParticipants)

        if (!event.isQualifier) {
            val initialPlacements = mutableListOf<TournamentPlacement>()
            when (event) {
                TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                    participants.forEach { participant ->
                        initialPlacements.add(
                            TournamentPlacement(
                                tournamentId = tournamentId,
                                playerId = participant.playerId,
                                reachedRound = TournamentRound.L128.name,
                                prizeMoney = 0.0
                            )
                        )
                    }
                }
                TournamentEvent.EUROPEAN_TOUR -> {
                    qualResult.preQualifiedOomIds.forEach { playerId ->
                        initialPlacements.add(TournamentPlacement(tournamentId = tournamentId, playerId = playerId, reachedRound = TournamentRound.L32.name, prizeMoney = 0.0))
                    }
                    qualResult.preQualifiedPtOomIds.forEach { playerId ->
                        initialPlacements.add(TournamentPlacement(tournamentId = tournamentId, playerId = playerId, reachedRound = TournamentRound.L48.name, prizeMoney = 0.0))
                    }
                    val assignedPlayerIds = (qualResult.preQualifiedOomIds + qualResult.preQualifiedPtOomIds).toSet()
                    participants.filter { it.playerId !in assignedPlayerIds }.forEach { participant ->
                        initialPlacements.add(TournamentPlacement(tournamentId = tournamentId, playerId = participant.playerId, reachedRound = TournamentRound.L48.name, prizeMoney = event.getRankingPrizeMoney(TournamentRound.L48)))
                    }
                }
                TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                    participants.forEach { participant ->
                        val round = when (participant.qualificationType) {
                            "OOM_TOP_32" -> TournamentRound.L64
                            "OOM_33_64" -> TournamentRound.R3_96
                            "OOM_65_96" -> TournamentRound.R2_128
                            else -> TournamentRound.R1_160
                        }
                        initialPlacements.add(
                            TournamentPlacement(
                                tournamentId = tournamentId,
                                playerId = participant.playerId,
                                reachedRound = round.name,
                                prizeMoney = event.getRankingPrizeMoney(round)
                            )
                        )
                    }
                }
                TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                    participants.forEach { participant ->
                        initialPlacements.add(
                            TournamentPlacement(
                                tournamentId = tournamentId,
                                playerId = participant.playerId,
                                reachedRound = TournamentRound.GROUP_3RD.name,
                                prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_3RD)
                            )
                        )
                    }
                }
                else -> {
                    val startRound = event.getRoundSequence().firstOrNull() ?: TournamentRound.L32
                    val startRoundPrize = event.getRankingPrizeMoney(startRound)
                    participants.forEach { participant ->
                        initialPlacements.add(
                            TournamentPlacement(
                                tournamentId = tournamentId,
                                playerId = participant.playerId,
                                reachedRound = startRound.name,
                                prizeMoney = startRoundPrize
                            )
                        )
                    }
                }
            }
            if (initialPlacements.isNotEmpty()) {
                tournamentPlacementDao.insertPlacements(initialPlacements.distinctBy { it.playerId })
            }
        }

        tournamentMatchDao.insertMatches(tournamentMatches)

        // Startrunde am Turnier persistieren: erste Runde der Sequenz, für die tatsächlich Matches angelegt wurden
        val fullRoundSequence = event.getRoundSequence()
        val startRound = fullRoundSequence.firstOrNull { round ->
            tournamentMatches.any { match -> match.round == round.name }
        }
        if (startRound != null) {
            tournamentDao.updateTournament(tournament.copy(currentRound = startRound.name))
        }

        tournamentMatchDao.getMatchesListForTournament(tournamentId)
    }
}
