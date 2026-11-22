package com.danny.dannyapp.ui.tournament

import android.content.Context
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.MatchFormatType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.GroupStandingEntry
import com.danny.dannyapp.domain.model.MatchDistance
import com.danny.dannyapp.domain.usecase.BracketGenerator
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.model.GrandSlamGroupItem
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import java.util.Locale

/**
 * Mapper helper providing stateless mapping, formatting and placeholder resolution methods
 * for tournament details UI.
 */
object TournamentDetailMapper {

    /**
     * Resolves placeholder player names for TBD open draws or previous round winners.
     */
    fun resolvePlaceholderPlayerName(
        context: Context,
        playerId: Long,
        matchIndex: Int,
        isPlayer1: Boolean,
        event: TournamentEvent,
        roundKey: String
    ): String? {
        return when {
            playerId == PLACEHOLDER_OPEN_DRAW_ID -> context.getString(R.string.match_tbd_open_draw)
            playerId == PLACEHOLDER_PREVIOUS_WINNER_ID -> {
                if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && roundKey == TournamentRound.GROUP.name) {
                    context.getString(R.string.match_tbd_open_draw)
                } else if (event == TournamentEvent.EUROPEAN_TOUR && roundKey == TournamentRound.L32.name) {
                    if (isPlayer1) {
                        val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
                        val seedRank = seedOrder[matchIndex] + 1
                        context.getString(R.string.tournament_placeholder_top16_seed, seedRank)
                    } else {
                        val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
                        val seedIndex = seedOrder[matchIndex]
                        val sourceLabel = "${TournamentUiHelper.getRoundPrefix(TournamentRound.L48.name)}-${16 - seedIndex}"
                        context.getString(R.string.tournament_placeholder_winner, sourceLabel)
                    }
                } else {
                    val sequence = event.getRoundSequence()
                    val currentIndex = sequence.indexOfFirst { round -> round.name == roundKey }
                    val prevRoundKey = if (currentIndex > 0) sequence[currentIndex - 1].name else roundKey
                    val sourceLabel = "${TournamentUiHelper.getRoundPrefix(prevRoundKey)}-${2 * matchIndex + if (isPlayer1) 1 else 2}"
                    context.getString(R.string.tournament_placeholder_winner, sourceLabel)
                }
            }
            playerId <= -1000L && playerId > -2000L -> {
                val prelimIndex = (-1000L - playerId).toInt() + 1
                val sourceLabel = "${TournamentUiHelper.getRoundPrefix(TournamentRound.QUAL_PRELIM.name)}-$prelimIndex"
                context.getString(R.string.tournament_placeholder_winner, sourceLabel)
            }
            playerId <= -9000L && playerId > -9020L -> {
                val groupIndex = (-9000L - playerId).toInt()
                val groupLetter = ('A' + groupIndex).toString()
                context.getString(R.string.grand_slam_group_winner_format, groupLetter)
            }
            playerId < 0L && playerId != USER_ID -> {
                context.getString(R.string.match_tbd_open_draw)
            }
            else -> null
        }
    }

    /**
     * Creates a [MatchUiModel] from a [TournamentMatch] entity.
     */
    fun createMatchUiModel(
        context: Context,
        match: TournamentMatch,
        careerWithProfile: CareerWithProfile,
        allPlayersMap: Map<Long, Player>,
        roundKey: String,
        event: TournamentEvent,
        seededPlayerMap: Map<Long, Int> = emptyMap()
    ): MatchUiModel {
        val isP1User = (match.player1Id == USER_ID)
        val isP2User = (match.player2Id == USER_ID)
        val isUserMatch = isP1User || isP2User

        val p1Placeholder = resolvePlaceholderPlayerName(context, match.player1Id, match.matchIndex, true, event, roundKey)
        val p1Seed = if (match.player1Id > 0L || match.player1Id == USER_ID) {
            seededPlayerMap[match.player1Id]
        } else {
            null
        }
        val player1 = if (p1Placeholder != null) {
            PlayerUiInfo(
                id = match.player1Id,
                name = p1Placeholder,
                seed = p1Seed,
                skill = SkillLevel.DEFAULT.value
            )
        } else {
            UIHelper.resolvePlayerUiInfo(match.player1Id, allPlayersMap[match.player1Id], careerWithProfile, p1Seed)
        }

        val p2Placeholder = resolvePlaceholderPlayerName(context, match.player2Id, match.matchIndex, false, event, roundKey)
        val p2Seed = if (match.player2Id > 0L || match.player2Id == USER_ID) {
            seededPlayerMap[match.player2Id]
        } else {
            null
        }
        val player2 = if (p2Placeholder != null) {
            PlayerUiInfo(
                id = match.player2Id,
                name = p2Placeholder,
                seed = p2Seed,
                skill = SkillLevel.DEFAULT.value
            )
        } else {
            UIHelper.resolvePlayerUiInfo(match.player2Id, allPlayersMap[match.player2Id], careerWithProfile, p2Seed)
        }

        val scoreText = if (match.isCompleted) "${match.player1Score} : ${match.player2Score}" else "- : -"
        val roundDisplayName = TournamentUiHelper.getDisplayRoundName(roundKey, context)

        return MatchUiModel(
            match = match,
            player1 = player1,
            player2 = player2,
            isUserMatch = isUserMatch,
            isCompleted = match.isCompleted,
            winnerId = match.winnerId,
            scoreText = scoreText,
            roundDisplayName = roundDisplayName
        )
    }

    /**
     * Formats the match distance for display.
     */
    fun formatMatchDistance(context: Context, dist: MatchDistance): String {
        val formatText = when (dist.format) {
            MatchFormatType.LEGS -> context.getString(R.string.match_format_legs)
            MatchFormatType.SETS_BEST_OF_5_LEGS -> context.getString(R.string.match_format_sets_5legs)
            MatchFormatType.SETS_BEST_OF_3_LEGS -> context.getString(R.string.match_format_sets_3legs)
            MatchFormatType.SETS_DOUBLE_IN_OUT -> context.getString(R.string.match_format_sets_double_in_out)
        }
        val clearText = if (dist.twoClearLegsRule) context.getString(R.string.match_format_two_clear_legs) else ""
        return context.getString(R.string.match_format_best_of, dist.bestOf, formatText, clearText)
    }

    /**
     * Builds prize money and match distance items for the tournament info dialog.
     */
    fun buildPrizeMoneyItems(
        context: Context,
        event: TournamentEvent,
        sequence: List<TournamentRound>
    ): List<PrizeMoneyItem> {
        val reversedSequence = sequence.reversed()
        val winnerPrize = event.getRankingPrizeMoney(TournamentRound.WINNER)
        val winnerPrizeText = if (winnerPrize > 0) "£%,d".format(Locale.US, winnerPrize.toLong()) else "£0"
        val winnerDistance = event.getMatchDistance(TournamentRound.F)
        val winnerItem = PrizeMoneyItem(
            roundName = TournamentUiHelper.getDisplayRoundName(TournamentRound.WINNER.name, context),
            matchDistanceText = formatMatchDistance(context, winnerDistance),
            prizeMoneyText = winnerPrizeText
        )

        val roundItems = reversedSequence.map { round ->
            val prize = event.getRankingPrizeMoney(round)
            val prizeText = if (prize > 0) "£%,d".format(Locale.US, prize.toLong()) else "£0"
            val distance = event.getMatchDistance(round)
            PrizeMoneyItem(
                roundName = TournamentUiHelper.getDisplayRoundName(round.name, context),
                matchDistanceText = formatMatchDistance(context, distance),
                prizeMoneyText = prizeText
            )
        }
        return listOf(winnerItem) + roundItems
    }

    /**
     * Maps domain group standings to [GrandSlamGroupItem] UI models.
     */
    fun mapGrandSlamGroupStandings(
        standingsByGroup: Map<String, List<GroupStandingEntry>>,
        careerWithProfile: CareerWithProfile,
        allPlayersMap: Map<Long, Player>,
        seededPlayerMap: Map<Long, Int>
    ): Map<String, List<GrandSlamGroupItem>> {
        return ('A'..'P').mapNotNull { groupChar ->
            val groupLetter = groupChar.toString()
            val entries = standingsByGroup[groupLetter] ?: return@mapNotNull null
            val items = entries.map { entry ->
                val playerSeed = seededPlayerMap[entry.playerId]
                val playerUiInfo = UIHelper.resolvePlayerUiInfo(
                    playerId = entry.playerId,
                    player = allPlayersMap[entry.playerId],
                    careerWithProfile = careerWithProfile,
                    seed = playerSeed
                )
                GrandSlamGroupItem(
                    rank = entry.rank,
                    player = playerUiInfo,
                    played = entry.played,
                    legDiff = entry.legDiff,
                    points = entry.points
                )
            }
            groupLetter to items
        }.toMap()
    }
}
