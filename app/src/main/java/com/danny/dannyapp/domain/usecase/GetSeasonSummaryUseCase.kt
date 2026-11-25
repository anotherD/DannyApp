package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.ClimberSummary
import com.danny.dannyapp.domain.model.MajorWinnerSummary
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.SeasonSummaryData
import com.danny.dannyapp.domain.model.UserTourCardOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase zur Berechnung der Saison-Zusammenfassung nach Abschluss aller Turniere einer Saison.
 */
class GetSeasonSummaryUseCase(
    private val careerRepository: CareerRepository,
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {

    suspend operator fun invoke(careerId: Long): SeasonSummaryData = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }

        val careerWithProfile = careerRepository.getCareerById(careerId)
        val career = careerWithProfile.career

        val seasons = seasonRepository.getSeasonsListForCareer(careerId)
        val currentSeason = requireNotNull(seasons.maxByOrNull { it.seasonNumber }) {
            "Keine Saison für Karriere $careerId gefunden"
        }

        val tournaments = tournamentRepository.getTournamentsListForSeason(currentSeason.id)
        check(tournaments.isNotEmpty() && tournaments.all { it.isCompleted }) {
            "Saison-Zusammenfassung kann erst nach Abschluss aller Turniere der aktuellen Saison erstellt werden"
        }

        // 1. Ranglisten (OOM, PT_OOM, PC_OOM, ET_OOM)
        val oomList = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomList = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomList = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomList = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        val userOom = requireNotNull(oomList.find { it.isUser }) { "User-Eintrag in OOM nicht gefunden" }
        val userPtOom = requireNotNull(ptOomList.find { it.isUser }) { "User-Eintrag in PT_OOM nicht gefunden" }
        val userPcOom = requireNotNull(pcOomList.find { it.isUser }) { "User-Eintrag in PC_OOM nicht gefunden" }
        val userEtOom = requireNotNull(etOomList.find { it.isUser }) { "User-Eintrag in ET_OOM nicht gefunden" }

        val oomLeaderEntry = oomList.firstOrNull()
        val oomLeader = oomLeaderEntry?.let { entry ->
            MajorWinnerSummary(
                tournamentName = "Order of Merit",
                winnerId = entry.playerId,
                winnerName = entry.name,
                winnerCountryCode = entry.countryCode,
                prizeMoney = entry.prizeMoney
            )
        }

        // 2. User-Match-Statistiken aus der aktuellen Saison
        val seasonMatches = tournamentRepository.getMatchesListForSeason(currentSeason.id)
        val userMatches = seasonMatches.filter { it.isCompleted && (it.player1Id == USER_ID || it.player2Id == USER_ID) }
        val matchesWon = userMatches.count { it.winnerId == USER_ID }
        val matchesLost = userMatches.count { it.winnerId != null && it.winnerId != USER_ID }

        // 3. Tournaments Played & Bestes Ergebnis
        val seasonPlacements = tournamentRepository.getPlacementsForSeasons(listOf(currentSeason.id))
        val tournamentMap = tournaments.associateBy { it.id }
        val allPlayers = playerRepository.getAllPlayersList()
        val playerMap = allPlayers.associateBy { it.id }

        val userMainPlacements = seasonPlacements.filter { placement ->
            if (placement.playerId != USER_ID) return@filter false
            val t = tournamentMap[placement.tournamentId] ?: return@filter false
            val event = TournamentEvent.valueOf(t.type)
            !event.isQualifier
        }

        val tournamentsPlayed = userMainPlacements.map { it.tournamentId }.distinct().size

        val roundRankMap = mapOf(
            TournamentRound.WINNER.name to 13,
            TournamentRound.F.name to 12,
            TournamentRound.SF.name to 11,
            TournamentRound.QF.name to 10,
            TournamentRound.L16.name to 9,
            TournamentRound.L32.name to 8,
            TournamentRound.L48.name to 7,
            TournamentRound.L64.name to 6,
            TournamentRound.R3_96.name to 5,
            TournamentRound.R2_128.name to 4,
            TournamentRound.R1_160.name to 3,
            TournamentRound.GROUP_2ND.name to 2,
            TournamentRound.GROUP_3RD.name to 1
        )

        val bestPlacement = userMainPlacements.maxWithOrNull(
            compareBy<TournamentPlacement> { roundRankMap[it.reachedRound] ?: 0 }
                .thenBy { it.prizeMoney }
        )

        val bestTournamentName = bestPlacement?.let { tournamentMap[it.tournamentId]?.name }
        val bestReachedRoundKey = bestPlacement?.reachedRound

        // 4. Tour Card Outcome
        val seasonTourCards = seasonRepository.getTourCardsListForSeason(currentSeason.id)
        val userTourCardWithPlayer = seasonTourCards.find { it.tourCard.playerId == USER_ID }
        val userTourCard = requireNotNull(userTourCardWithPlayer?.tourCard) {
            "Tour Card für User in Saison ${currentSeason.seasonNumber} nicht gefunden"
        }

        val userInTop64 = userOom.rank in 1..64
        val tourCardOutcome = when {
            userInTop64 -> UserTourCardOutcome.RETAINED_TOP_64
            userTourCard.tourCardYear == 1 -> UserTourCardOutcome.SECOND_YEAR_ACTIVE
            else -> UserTourCardOutcome.LOST_AND_REGAINED_Q_SCHOOL
        }

        // 5. Major Winners & World Champion
        val majorTournaments = tournaments
            .filter { TournamentEvent.valueOf(it.type).isMajor }
            .sortedBy { it.order }

        val majorWinners = mutableListOf<MajorWinnerSummary>()
        var worldChampion: MajorWinnerSummary? = null

        for (majorTournament in majorTournaments) {
            val placements = tournamentRepository.getPlacementsListForTournament(majorTournament.id)
            val winnerPlacement = placements.find { it.reachedRound == TournamentRound.WINNER.name }
            if (winnerPlacement != null) {
                val winnerId = winnerPlacement.playerId
                val (winnerName, winnerCountry) = if (winnerId == USER_ID) {
                    careerWithProfile.playerName to careerWithProfile.countryCode
                } else {
                    val p = requireNotNull(playerMap[winnerId]) { "Spieler mit ID $winnerId nicht gefunden" }
                    p.name to p.countryCode
                }
                val summary = MajorWinnerSummary(
                    tournamentName = majorTournament.name,
                    winnerId = winnerId,
                    winnerName = winnerName,
                    winnerCountryCode = winnerCountry,
                    prizeMoney = winnerPlacement.prizeMoney
                )
                majorWinners.add(summary)
                if (majorTournament.type == TournamentEvent.WORLD_CHAMPIONSHIP.name) {
                    worldChampion = summary
                }
            }
        }

        // 6. Top Climber Calculation
        val topClimber = calculateTopClimber(
            currentSeasonId = currentSeason.id,
            seasons = seasons,
            postSeasonOom = oomList,
            seasonPlacements = seasonPlacements,
            playerMap = playerMap,
            userName = careerWithProfile.playerName,
            userCountryCode = careerWithProfile.countryCode,
            userSkill = SkillLevel.DEFAULT.value
        )

        SeasonSummaryData(
            careerName = career.name,
            completedSeasonNumber = currentSeason.seasonNumber,
            nextSeasonNumber = currentSeason.seasonNumber + 1,
            currentCareerLevel = career.level,
            userName = careerWithProfile.playerName,
            userCountryCode = careerWithProfile.countryCode,
            oomRank = userOom.rank,
            oomPrizeMoney = userOom.prizeMoney,
            ptOomRank = userPtOom.rank,
            ptOomPrizeMoney = userPtOom.prizeMoney,
            pcOomRank = userPcOom.rank,
            pcOomPrizeMoney = userPcOom.prizeMoney,
            etOomRank = userEtOom.rank,
            etOomPrizeMoney = userEtOom.prizeMoney,
            tournamentsPlayed = tournamentsPlayed,
            matchesWon = matchesWon,
            matchesLost = matchesLost,
            bestTournamentName = bestTournamentName,
            bestReachedRoundKey = bestReachedRoundKey,
            tourCardOutcome = tourCardOutcome,
            worldChampion = worldChampion,
            majorWinners = majorWinners,
            oomLeader = oomLeader,
            topClimber = topClimber
        )
    }

    private suspend fun calculateTopClimber(
        currentSeasonId: Long,
        seasons: List<Season>,
        postSeasonOom: List<OrderOfMeritEntry>,
        seasonPlacements: List<TournamentPlacement>,
        playerMap: Map<Long, Player>,
        userName: String,
        userCountryCode: String,
        userSkill: Int
    ): ClimberSummary? {
        val allSeasonIds = seasons.map { it.id }
        val allTournaments = tournamentRepository.getTournamentsListForSeasons(allSeasonIds)
        val allPlacements = tournamentRepository.getPlacementsForSeasons(allSeasonIds)

        val seasonMap = seasons.associateBy { it.id }

        val preSeasonTournaments = allTournaments.filter { it.seasonId != currentSeasonId }
        val preSeasonPlacements = allPlacements.filter { placement ->
            val t = allTournaments.find { it.id == placement.tournamentId }
            t != null && t.seasonId != currentSeasonId
        }

        val tournamentChronologicalDescendingComparator = compareByDescending<Tournament> {
            val season = requireNotNull(seasonMap[it.seasonId])
            season.seasonNumber
        }.thenByDescending { it.order }
            .thenByDescending { it.id }

        val startedOrCompletedPreSeasonTournaments = preSeasonTournaments.filter {
            it.isCompleted || it.currentRound != null || seasonMap[it.seasonId]?.isCompleted == true
        }

        val oomTournaments = startedOrCompletedPreSeasonTournaments.groupBy { it.name }
            .flatMap { (_, tList) ->
                tList.sortedWith(tournamentChronologicalDescendingComparator).take(2)
            }
            .sortedWith(tournamentChronologicalDescendingComparator)

        val selectedTournamentIds = oomTournaments.map { it.id }.toSet()
        val filteredPlacements = preSeasonPlacements.filter { it.tournamentId in selectedTournamentIds }

        val prizeMoneyMap = mutableMapOf<Long, Double>()
        prizeMoneyMap[USER_ID] = 0.0

        for (placement in filteredPlacements) {
            val current = prizeMoneyMap[placement.playerId] ?: 0.0
            prizeMoneyMap[placement.playerId] = current + placement.prizeMoney
        }

        val preSeasonEntries = prizeMoneyMap.map { (playerId, prizeMoney) ->
            val isUser = playerId == USER_ID
            val name: String
            val countryCode: String
            val skill: Int

            if (isUser) {
                name = userName
                countryCode = userCountryCode
                skill = userSkill
            } else {
                val player = requireNotNull(playerMap[playerId]) { "CPU-Spieler mit ID $playerId nicht gefunden" }
                name = player.name
                countryCode = player.countryCode
                skill = player.skill
            }

            OrderOfMeritEntry(
                playerId = playerId,
                name = name,
                countryCode = countryCode,
                skill = skill,
                prizeMoney = prizeMoney,
                rank = 0,
                isUser = isUser
            )
        }

        val preSeasonRankMap: Map<Long, Int> = if (preSeasonTournaments.isEmpty()) {
            postSeasonOom.associate { it.playerId to postSeasonOom.size }
        } else {
            val shuffledPlayerIds = preSeasonEntries.map { it.playerId }.shuffled()
            val randomTieBreakerMap = shuffledPlayerIds.mapIndexed { index, playerId -> playerId to index }.toMap()

            val sortedPreSeason = preSeasonEntries.sortedWith { entryA, entryB ->
                val prizeDiff = entryB.prizeMoney.compareTo(entryA.prizeMoney)
                if (prizeDiff != 0) return@sortedWith prizeDiff

                val tieBreakerA = requireNotNull(randomTieBreakerMap[entryA.playerId])
                val tieBreakerB = requireNotNull(randomTieBreakerMap[entryB.playerId])
                tieBreakerA.compareTo(tieBreakerB)
            }

            sortedPreSeason.mapIndexed { index, entry ->
                entry.playerId to (index + 1)
            }.toMap()
        }

        val postSeasonRankMap = postSeasonOom.associate { it.playerId to it.rank }

        val currentSeasonTournamentIds = allTournaments.filter { it.seasonId == currentSeasonId }.map { it.id }.toSet()
        val seasonPrizeMoneyMap = seasonPlacements
            .filter { it.tournamentId in currentSeasonTournamentIds }
            .groupBy { it.playerId }
            .mapValues { (_, placements) -> placements.sumOf { it.prizeMoney } }

        var bestClimber: ClimberSummary? = null
        var maxRankDelta = 0
        var maxSeasonPrizeMoney = -1.0

        val allPlayerIds = (preSeasonRankMap.keys + postSeasonRankMap.keys).distinct()

        for (pId in allPlayerIds) {
            val rankBefore = preSeasonRankMap[pId] ?: continue
            val rankAfter = postSeasonRankMap[pId] ?: continue

            val rankDelta = rankBefore - rankAfter
            if (rankDelta <= 0) continue

            val seasonPrizeMoney = seasonPrizeMoneyMap[pId] ?: 0.0

            if (rankDelta > maxRankDelta || (rankDelta == maxRankDelta && seasonPrizeMoney > maxSeasonPrizeMoney)) {
                maxRankDelta = rankDelta
                maxSeasonPrizeMoney = seasonPrizeMoney

                val (pName, pCountry) = if (pId == USER_ID) {
                    userName to userCountryCode
                } else {
                    val p = playerMap[pId] ?: continue
                    p.name to p.countryCode
                }

                bestClimber = ClimberSummary(
                    playerId = pId,
                    playerName = pName,
                    countryCode = pCountry,
                    rankDelta = rankDelta,
                    prizeMoneyDelta = seasonPrizeMoney
                )
            }
        }

        return bestClimber
    }
}
