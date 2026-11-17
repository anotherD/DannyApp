package com.danny.dannyapp.ui

import android.content.Context
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.EntrantGroupType
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.PlayerUiInfo

/**
 * UI helper class for tournament bracket previews, seeding calculation, and round sequences.
 * Delegates all qualification, seeding, sorting, and propagation logic to [TournamentQualificationUseCase].
 */
class TournamentPreviewHelper(
    private val context: Context,
    private val tournamentQualificationUseCase: TournamentQualificationUseCase = TournamentQualificationUseCase(context)
) {

    /**
     * Determines the sequence of rounds for a given [TournamentEvent].
     * If [allMatches] is provided and non-empty, truncates the sequence to start from the earliest round present in matches.
     */
    fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        return tournamentQualificationUseCase.getRoundSequence(event, allMatches)
    }

    /**
     * Maps player IDs to their seed numbers for a given [TournamentEvent] and [seasonId].
     * If [tournamentId] is provided and participants exist in DB, returns frozen seeds from DB.
     */
    suspend fun getSeededPlayerMap(
        event: TournamentEvent,
        seasonId: Long,
        tournamentId: Long = 0L
    ): Map<Long, Int> {
        return tournamentQualificationUseCase.getSeededPlayerMap(event, seasonId, tournamentId)
    }

    /**
     * Generates placeholder preview matches for a specific round of a tournament.
     */
    fun generatePreviewMatchesForRound(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent
    ): List<TournamentMatch> {
        return tournamentQualificationUseCase.generatePreviewMatchesForRound(tournamentId, roundKey, event)
    }

    /**
     * Generates preview matches for a round and propagates winner IDs or seeds from previous rounds.
     */
    suspend fun generatePreviewMatchesWithPropagation(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long,
        currentCareerId: Long
    ): List<TournamentMatch> {
        return tournamentQualificationUseCase.generatePreviewMatchesWithPropagation(
            tournamentId = tournamentId,
            roundKey = roundKey,
            event = event,
            allMatches = allMatches,
            seasonId = seasonId,
            fallbackCareerId = currentCareerId
        )
    }

    /**
     * Builds entrant group information for open draw events like UK Open or European Open.
     */
    suspend fun getUkOpenEntrantGroups(
        tournamentId: Long,
        careerId: Long,
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent,
        currentCareerId: Long
    ): List<EntrantGroupInfo> {
        val domainGroups = tournamentQualificationUseCase.getUkOpenEntrantGroups(
            tournamentId = tournamentId,
            careerId = careerId,
            roundKey = roundKey,
            allMatches = allMatches,
            event = event,
            currentCareerId = currentCareerId
        )

        return domainGroups.map { domainGroup ->
            val title = when (domainGroup.groupType) {
                EntrantGroupType.OOM_65_96 -> context.getString(R.string.entrant_reason_oom_65_96)
                EntrantGroupType.OOM_33_64 -> context.getString(R.string.entrant_reason_oom_33_64)
                EntrantGroupType.OOM_TOP_32 -> context.getString(R.string.entrant_reason_oom_top_32)
                EntrantGroupType.ROUND_WINNERS -> {
                    val prevRoundDisplayName = TournamentUiHelper.getDisplayRoundName(
                        domainGroup.prevRoundName ?: "",
                        context
                    )
                    context.getString(R.string.entrant_reason_round_winners, prevRoundDisplayName)
                }
            }
            val players = domainGroup.players.map { player ->
                PlayerUiInfo(
                    id = player.id,
                    name = player.name,
                    countryCode = player.countryCode,
                    seed = player.seed,
                    skill = player.skill
                )
            }
            EntrantGroupInfo(title = title, players = players)
        }
    }
}
