package com.danny.dannyapp

import android.content.Context
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.UserProfileRepository
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.CalculateGroupStandingsUseCase
import com.danny.dannyapp.domain.usecase.CompleteMatchUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import com.danny.dannyapp.domain.usecase.GetPlayersUseCase
import com.danny.dannyapp.domain.usecase.GetTournamentSummaryUseCase
import com.danny.dannyapp.domain.usecase.TournamentProgressionUseCase
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase

/**
 * Central dependency injection container for managing application-wide singletons
 * such as database, repositories, and use cases.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Singleton instance of the Room database.
     */
    val database: AppDatabase by lazy { AppDatabase.getDatabase(appContext) }

    /**
     * Singleton instance of SharedPreferences settings manager.
     */
    val settingsManager: SettingsManager by lazy { SettingsManager(appContext) }

    /**
     * Singleton instance of player repository.
     */
    val playerRepository: PlayerRepository by lazy { PlayerRepository(database.playerDao()) }

    /**
     * Singleton instance of user profile repository.
     */
    val userProfileRepository: UserProfileRepository by lazy { UserProfileRepository(database.userProfileDao()) }

    /**
     * Singleton instance of tournament repository.
     */
    val tournamentRepository: TournamentRepository by lazy {
        TournamentRepository(
            database.tournamentDao(),
            database.tournamentMatchDao(),
            database.tournamentPlacementDao(),
            database.tournamentParticipantDao(),
            database
        )
    }

    /**
     * Singleton instance of season repository.
     */
    val seasonRepository: SeasonRepository by lazy {
        SeasonRepository(
            database.seasonDao(),
            database.tourCardDao(),
            database.playerDao(),
            tournamentRepository,
            appContext
        )
    }

    /**
     * Singleton instance of career repository.
     */
    val careerRepository: CareerRepository by lazy { CareerRepository(database.careerDao(), seasonRepository) }

    /**
     * Use case for generating season calendar.
     */
    val generateSeasonCalendarUseCase: GenerateSeasonCalendarUseCase by lazy {
        GenerateSeasonCalendarUseCase(tournamentRepository)
    }

    /**
     * Use case for retrieving Order of Merit rankings.
     */
    val getOrderOfMeritUseCase: GetOrderOfMeritUseCase by lazy {
        GetOrderOfMeritUseCase(careerRepository, seasonRepository, tournamentRepository, playerRepository)
    }

    /**
     * Use case for fetching players.
     */
    val getPlayersUseCase: GetPlayersUseCase by lazy { GetPlayersUseCase(playerRepository) }

    /**
     * Use case for retrieving tournament summaries.
     */
    val getTournamentSummaryUseCase: GetTournamentSummaryUseCase by lazy { GetTournamentSummaryUseCase() }

    /**
     * Use case for calculating group standings.
     */
    val calculateGroupStandingsUseCase: CalculateGroupStandingsUseCase by lazy { CalculateGroupStandingsUseCase() }

    /**
     * Use case for tournament qualification logic.
     */
    val tournamentQualificationUseCase: TournamentQualificationUseCase by lazy {
        TournamentQualificationUseCase(
            seasonRepository,
            playerRepository,
            tournamentRepository,
            careerRepository,
            getOrderOfMeritUseCase
        )
    }

    /**
     * Use case for generating tournament brackets.
     */
    val generateBracketUseCase: GenerateBracketUseCase by lazy {
        GenerateBracketUseCase(tournamentRepository, seasonRepository, tournamentQualificationUseCase)
    }

    /**
     * Use case for advancing tournament rounds.
     */
    val advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase by lazy {
        AdvanceTournamentRoundUseCase(tournamentRepository, tournamentQualificationUseCase)
    }

    /**
     * Use case for completing matches.
     */
    val completeMatchUseCase: CompleteMatchUseCase by lazy {
        CompleteMatchUseCase(tournamentRepository, playerRepository, advanceTournamentRoundUseCase)
    }

    /**
     * Use case for overall tournament progression.
     */
    val tournamentProgressionUseCase: TournamentProgressionUseCase by lazy {
        TournamentProgressionUseCase(
            completeMatchUseCase,
            advanceTournamentRoundUseCase,
            tournamentRepository,
            generateBracketUseCase
        )
    }
}

/**
 * Extension property to easily retrieve the [AppContainer] from any [Context].
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as DannyApp).appContainer
