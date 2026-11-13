package com.danny.dannyapp.domain.usecase

import android.content.Context
import android.util.Log
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.MatchPairing
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.model.TournamentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateBracketUseCase(
    private val tournamentDao: TournamentDao,
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        GetOrderOfMeritUseCase(context)
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

        // 1. Fetch Tour Cards for this season
        val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season.id)
        val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()

        // 2. Fetch All Players
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        // 3. Fetch Orders of Merit
        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        // Helper sorting function for player IDs using Order of Merit rank / prize money / skill / name
        fun getPlayerListSortedByOom(playerIds: List<Long>, oomList: List<OrderOfMeritEntry>): List<Long> {
            val playerMap = oomList.associateBy { it.playerId }
            val dbPlayerMap = allPlayers.associateBy { it.id }
            return playerIds.sortedWith(
                compareBy<Long> { playerMap[it]?.rank ?: Int.MAX_VALUE }
                    .thenByDescending { playerMap[it]?.prizeMoney ?: 0.0 }
                    .thenByDescending {
                        if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5
                    }
                    .thenBy {
                        if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: ""
                    }
            )
        }

        fun formatPlayer(id: Long): String {
            if (id == USER_ID) return "ID: -1 | User"
            val p = allPlayers.find { it.id == id }
            return if (p != null) "ID: ${p.id} | ${p.name} (Land: ${p.countryCode}, Skill: ${p.skill})" else "ID: $id | CPU #$id"
        }

        fun logI(tag: String, msg: String) {
            try {
                Log.i(tag, msg)
            } catch (_: Throwable) {
                println("[$tag] $msg")
            }
        }

        fun logQualificationGroup(label: String, playerIds: List<Long>) {
            logI("TournamentQualification", "--- $label (${playerIds.size} Spieler) ---")
            playerIds.forEachIndexed { i, id ->
                logI("TournamentQualification", "  %2d. %s".format(i + 1, formatPlayer(id)))
            }
        }

        // Helper to get winners from preceding qualifier tournament
        suspend fun getQualifierWinnersFromPreceding(targetCount: Int): List<Long> {
            val prevTournament = tournamentDao.getTournamentsListForSeason(season.id)
                .find { it.order == tournament.order - 1 } ?: return emptyList()
            val prevMatches = tournamentMatchDao.getMatchesListForTournament(prevTournament.id)
            if (prevMatches.isEmpty()) return emptyList()

            val rounds = prevMatches.map { it.round }.distinct()
            val finalRoundName = rounds.lastOrNull { r ->
                prevMatches.filter { it.round == r }.all { it.isCompleted && it.winnerId != null }
            } ?: rounds.lastOrNull() ?: return emptyList()

            val finalRoundMatches = prevMatches.filter { it.round == finalRoundName }
            val winners = finalRoundMatches.mapNotNull { it.winnerId }
            if (winners.size >= targetCount) {
                return winners.take(targetCount)
            }
            return winners
        }

        // Pre-qualified sets for ET and Grand Slam
        val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
        val top16Oom = sortedOomTc.take(16)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries).take(16)
        val etPreQualified = (top16Oom + top16PtOom).toSet()

        val nonPreqEtTc = tourCardPlayerIds.filterNot { it in etPreQualified }

        val matchPairings: List<MatchPairing> = when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedByPt = getPlayerListSortedByOom(tcIds, ptOomEntries)
                val top32 = sortedByPt.take(32)
                val remaining96 = sortedByPt.drop(32)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Top 32 Pro Tour Seeds (Gesetzt)", top32)
                logQualificationGroup("Weitere 96 Tour-Card-Inhaber (Ungesetzt)", remaining96)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedByPt,
                    proTourSortedIds = top32,
                    qualifierIds = remaining96
                )
            }

            TournamentEvent.QUALIFIER_ET -> {
                val sortedNonPreq = getPlayerListSortedByOom(nonPreqEtTc, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Vorqualifiziert (Top 16 OOM + Top 16 PT OOM - Befreit)", etPreQualified.toList())
                logQualificationGroup("Teilnehmende Tour-Card-Inhaber im ET Qualifier", sortedNonPreq)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedNonPreq
                )
            }

            TournamentEvent.EUROPEAN_TOUR -> {
                var tcQualifiers = getQualifierWinnersFromPreceding(10)
                if (tcQualifiers.size < 10) {
                    tcQualifiers = fillToTargetCount(tcQualifiers, 10, nonPreqEtTc, oomEntries)
                }

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds }
                val hostCountry = tournament.hostCountry ?: "DEU"
                val hostCountryNonTc = nonTcPlayerIds.filter { id ->
                    val player = allPlayers.find { it.id == id }
                    player?.countryCode == hostCountry && player.tourCardYear == 0
                }
                val hostQualifiers = fillToTargetCount(hostCountryNonTc, 4, nonTcPlayerIds, oomEntries).take(4)
                val remainingNonTc = nonTcPlayerIds.filterNot { it in hostQualifiers }
                val otherQualifiers = getPlayerListSortedByOom(remainingNonTc, oomEntries).take(2)

                var qualifiersPool = tcQualifiers + hostQualifiers + otherQualifiers
                if (qualifiersPool.size < 16) {
                    val candidatePool = allPlayerIds.filterNot { it in etPreQualified || it in qualifiersPool }
                    qualifiersPool = fillToTargetCount(qualifiersPool, 16, candidatePool, oomEntries)
                }

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("1. Top 16 Order of Merit (Gesetzte R2-Teilnehmer)", top16Oom)
                logQualificationGroup("2. Top 16 Pro Tour Order of Merit (R1-Teilnehmer)", top16PtOom)
                logQualificationGroup("3. 10 Tour Card Holder Qualifier Sieger", tcQualifiers)
                logQualificationGroup("4. 4 Host Nation Qualifier Sieger ($hostCountry)", hostQualifiers)
                logQualificationGroup("5. 2 Andere Qualifier Sieger", otherQualifiers)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16PtOom,
                    qualifierIds = qualifiersPool
                )
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedTcOom = getPlayerListSortedByOom(tcIds, oomEntries)

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tcIds }
                val nonTcQualifiers = getPlayerListSortedByOom(nonTcPlayerIds, oomEntries).take(32)
                val final32Qualifiers = fillToTargetCount(nonTcQualifiers, 32, allPlayerIds.filterNot { it in tcIds }, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("128 Tour-Card-Inhaber (Ränge 1-128 OOM)", sortedTcOom)
                logQualificationGroup("32 Non-Tourcard Qualifikanten", final32Qualifiers)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedTcOom,
                    qualifierIds = final32Qualifiers
                )
            }

            TournamentEvent.WORLD_MATCHPLAY -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                val top16Oom = sortedOom.take(16)
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val top16Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("1. Top 16 Order of Merit (Gesetzte Köpfe)", top16Oom)
                logQualificationGroup("2. Top 16 Pro Tour Order of Merit (Ungesetzte Qualifikanten)", top16Pt)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16Pt
                )
            }

            TournamentEvent.WORLD_GRAND_PRIX -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                val top16Oom = sortedOom.take(16)
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val top16Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("1. Top 8 Order of Merit (Gesetzte Köpfe 1-8)", top16Oom.take(8))
                logQualificationGroup("2. OOM Ränge 9-16 (Ausgeloste Köpfe 9-16)", top16Oom.drop(8))
                logQualificationGroup("3. Top 16 Pro Tour Order of Merit (Ungesetzt)", top16Pt)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16Pt
                )
            }

            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                val top32Et = getPlayerListSortedByOom(allPlayerIds, etOomEntries).take(32)
                val final32Et = fillToTargetCount(top32Et, 32, allPlayerIds, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Top 32 European Tour Order of Merit", final32Et)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = final32Et
                )
            }

            TournamentEvent.QUALIFIER_GRAND_SLAM -> {
                val remainingForPt = tourCardPlayerIds.filterNot { it in top16Oom }
                val gsPreq = top16Oom + getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)
                val nonPreqGsTc = tourCardPlayerIds.filterNot { it in gsPreq }
                val sortedNonPreq = getPlayerListSortedByOom(nonPreqGsTc, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Vorqualifiziert (Top 16 OOM + Top 16 PT OOM - Befreit)", gsPreq)
                logQualificationGroup("Teilnehmende Tour-Card-Inhaber im Grand Slam Qualifier", sortedNonPreq)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedNonPreq
                )
            }

            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val pot2 = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)

                var gsTchqWinners = getQualifierWinnersFromPreceding(8)
                if (gsTchqWinners.size < 8) {
                    gsTchqWinners = fillToTargetCount(gsTchqWinners, 8, tourCardPlayerIds, oomEntries)
                }
                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds }
                val otherQuals = getPlayerListSortedByOom(nonTcPlayerIds, oomEntries).take(8)
                val pot3 = fillToTargetCount(gsTchqWinners + otherQuals, 16, allPlayerIds, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Topf 1: Top 16 Order of Merit (Gruppenköpfe)", top16Oom)
                logQualificationGroup("Topf 2: Top 16 Pro Tour Order of Merit (Major/PT Qualifikanten)", pot2)
                logQualificationGroup("Topf 3: 8 TCHQ Sieger + 8 Andere Qualifier", pot3)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = pot2,
                    qualifierIds = pot3
                )
            }

            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val top64Pc = getPlayerListSortedByOom(allPlayerIds, pcOomEntries).take(64)
                val final64Pc = fillToTargetCount(top64Pc, 64, allPlayerIds, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Top 64 Players Championship Order of Merit", final64Pc)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = final64Pc
                )
            }

            TournamentEvent.QUALIFIER_WM -> {
                val sortedTcOom = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val top40Oom = sortedTcOom.take(40)
                val remainingTcAfterTop40Oom = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40PtOom = getPlayerListSortedByOom(remainingTcAfterTop40Oom, ptOomEntries).take(40)
                val wmPreQualified = (top40Oom + top40PtOom).toSet()
                val nonPreqWmTc = tourCardPlayerIds.filterNot { it in wmPreQualified }
                val sortedNonPreq = getPlayerListSortedByOom(nonPreqWmTc, oomEntries)

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("Vorqualifiziert (Top 40 OOM + Top 40 PT OOM - Befreit)", wmPreQualified.toList())
                logQualificationGroup("Teilnehmende Tour-Card-Inhaber im WM Qualifier", sortedNonPreq)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedNonPreq
                )
            }

            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val top40Oom = sortedOomTc.take(40)

                val remainingForPt = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(40)

                val tc80 = top40Oom + top40Pt
                val sortedTc80ByOom = getPlayerListSortedByOom(tc80, oomEntries)
                val top32Oom = sortedTc80ByOom.take(32)
                val ranks33To64 = sortedTc80ByOom.drop(32).take(32)
                val remaining16 = sortedTc80ByOom.drop(64)

                val nonPreqWmTc = tourCardPlayerIds.filterNot { it in tc80 }
                var wmTchqWinners = getQualifierWinnersFromPreceding(4)
                if (wmTchqWinners.size < 4) {
                    wmTchqWinners = fillToTargetCount(wmTchqWinners, 4, nonPreqWmTc, oomEntries)
                }

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds || it in tc80 }
                val nonTc44 = getPlayerListSortedByOom(nonTcPlayerIds, oomEntries).take(44)
                val nonTc44Full = fillToTargetCount(nonTc44, 44, nonTcPlayerIds, oomEntries)

                val remaining64Pool = fillToTargetCount(
                    remaining16 + wmTchqWinners + nonTc44Full,
                    64,
                    allPlayerIds.filterNot { it in top32Oom || it in ranks33To64 },
                    oomEntries
                )

                logI("TournamentQualification", "==================================================")
                logI("TournamentQualification", "Qualifikation & Auslosung: #${tournament.order} ${tournament.name} (${tournament.type})")
                logQualificationGroup("1. Top 40 Order of Merit", top40Oom)
                logQualificationGroup("2. Top 40 Pro Tour Order of Merit", top40Pt)
                logQualificationGroup("3. 4 WM Tour Card Holder Qualifier Sieger", wmTchqWinners)
                logQualificationGroup("4. 44 Internationale Non-TC Qualifikanten", nonTc44Full)
                logI("TournamentQualification", "==================================================")

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top32Oom + ranks33To64,
                    qualifierIds = remaining64Pool
                )
            }
        }

        val tournamentMatches = matchPairings.map { pairing ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = pairing.round.name,
                matchIndex = pairing.matchIndex,
                player1Id = pairing.player1Id,
                player2Id = pairing.player2Id,
                groupName = pairing.groupName
            )
        }

        tournamentMatchDao.insertMatches(tournamentMatches)
        tournamentMatchDao.getMatchesListForTournament(tournamentId)
    }

    private fun fillToTargetCount(
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
                    .thenByDescending { oomMap[it]?.skill ?: 5 }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }
}
