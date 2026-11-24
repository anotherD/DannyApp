package com.danny.dannyapp

import android.content.Context
import android.util.Log
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
import com.danny.dannyapp.domain.usecase.InitializeCareerOomUseCase
import com.danny.dannyapp.domain.usecase.TournamentProgressionUseCase
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central dependency injection container for managing application-wide singletons
 * such as database, repositories, and use cases.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val _errorEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeErrorEvent() {
        _errorEvents.resetReplayCache()
    }

    fun reportError(actionLabel: String, throwable: Throwable) {
        if (throwable is CancellationException) {
            throw throwable
        }
        Log.e("AppError", "Fehler bei $actionLabel", throwable)
        val detail = throwable.cause?.message ?: throwable.message ?: throwable::class.java.simpleName
        val userMessage = if (settingsManager.debugMode) {
            "$actionLabel fehlgeschlagen: $detail"
        } else {
            "$actionLabel konnte nicht ausgeführt werden."
        }
        _errorEvents.tryEmit(userMessage)
    }

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
     * Use case for initializing career Order of Merit with historical placements.
     */
    val initializeCareerOomUseCase: InitializeCareerOomUseCase by lazy {
        InitializeCareerOomUseCase(
            seasonRepository = seasonRepository,
            tournamentRepository = tournamentRepository,
            playerRepository = playerRepository,
            readInitialPlacementsLines = {
                appContext.assets.open("initial_placements.csv")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readLines() }
                    .asSequence()
            }
        )
    }

    /**
     * Singleton instance of career repository.
     */
    val careerRepository: CareerRepository by lazy {
        CareerRepository(
            database.careerDao(),
            seasonRepository,
            initializeCareerOomUseCase
        )
    }

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
        AdvanceTournamentRoundUseCase(tournamentRepository)
    }

    /**
     * Use case for completing matches.
     */
    val completeMatchUseCase: CompleteMatchUseCase by lazy {
        CompleteMatchUseCase(
            tournamentRepository,
            playerRepository,
            advanceTournamentRoundUseCase,
            seasonRepository,
            careerRepository
        )
    }

    /**
     * Use case for overall tournament progression.
     */
    val tournamentProgressionUseCase: TournamentProgressionUseCase by lazy {
        TournamentProgressionUseCase(
            completeMatchUseCase,
            advanceTournamentRoundUseCase,
            tournamentRepository,
            generateBracketUseCase,
            settingsManager
        )
    }
}

/**
 * Extension property to easily retrieve the [AppContainer] from any [Context].
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as DannyApp).appContainer
