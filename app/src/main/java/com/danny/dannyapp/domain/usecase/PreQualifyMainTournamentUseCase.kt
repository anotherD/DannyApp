package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ermittelt Haupt- und Qualifier-Turniere und friert vorqualifizierte Teilnehmer
 * des Hauptturniers vor Beginn des Qualifiers sauber ein (Idempotent).
 */
class PreQualifyMainTournamentUseCase(
    private val tournamentRepository: TournamentRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase,
    private val resolveNonSimulatedQualifiersUseCase: ResolveNonSimulatedQualifiersUseCase,
    private val grandSlamQualificationResolver: GrandSlamQualificationResolver,
    private val resolvePrequalifiedTourCardIds: (TournamentEvent, List<Long>, List<OrderOfMeritEntry>, List<OrderOfMeritEntry>, List<Player>) -> Pair<List<Long>, List<Long>>,
    private val sortPlayerIdsByOom: (List<Long>, List<OrderOfMeritEntry>, List<Player>) -> List<Long>
) {

    constructor(
        context: Context,
        grandSlamQualificationResolver: GrandSlamQualificationResolver,
        resolvePrequalifiedTourCardIds: (TournamentEvent, List<Long>, List<OrderOfMeritEntry>, List<OrderOfMeritEntry>, List<Player>) -> Pair<List<Long>, List<Long>>,
        sortPlayerIdsByOom: (List<Long>, List<OrderOfMeritEntry>, List<Player>) -> List<Long>
    ) : this(
        TournamentRepository(context),
        SeasonRepository(context),
        PlayerRepository(context),
        GetOrderOfMeritUseCase(context),
        ResolveNonSimulatedQualifiersUseCase(),
        grandSlamQualificationResolver,
        resolvePrequalifiedTourCardIds,
        sortPlayerIdsByOom
    )

    /**
     * Ermittelt das zugehörige Hauptturnier zu einem Qualifier-Turnier eindeutig aus der Liste der Saisonturniere.
     * @throws IllegalStateException falls das Hauptturnier nicht gefunden wird.
     */
    fun findMainTournamentForQualifier(
        qualifierTournament: Tournament,
        seasonTournaments: List<Tournament>
    ): Tournament {
        val qualifierEvent = try {
            TournamentEvent.valueOf(qualifierTournament.type)
        } catch (_: Exception) {
            throw IllegalStateException("Unbekannter Qualifier-Typ: ${qualifierTournament.type}")
        }

        return when (qualifierEvent) {
            TournamentEvent.QUALIFIER_ET -> {
                val expectedMainName = qualifierTournament.name.removeSuffix(" Qualifier").trim()
                seasonTournaments.firstOrNull {
                    it.type == TournamentEvent.EUROPEAN_TOUR.name && it.name == expectedMainName
                } ?: throw IllegalStateException("Hauptturnier '$expectedMainName' für European Tour Qualifier (${qualifierTournament.id}) nicht gefunden.")
            }
            TournamentEvent.QUALIFIER_GRAND_SLAM -> {
                seasonTournaments.firstOrNull {
                    it.type == TournamentEvent.GRAND_SLAM_OF_DARTS.name
                } ?: throw IllegalStateException("Hauptturnier GRAND_SLAM_OF_DARTS für Qualifier (${qualifierTournament.id}) nicht gefunden.")
            }
            TournamentEvent.QUALIFIER_WM -> {
                seasonTournaments.firstOrNull {
                    it.type == TournamentEvent.WORLD_CHAMPIONSHIP.name
                } ?: throw IllegalStateException("Hauptturnier WORLD_CHAMPIONSHIP für Qualifier (${qualifierTournament.id}) nicht gefunden.")
            }
            else -> throw IllegalStateException("Turnier ${qualifierTournament.name} (Typ: ${qualifierTournament.type}) ist kein vorgeschalteter Qualifier.")
        }
    }

    /**
     * Ermittelt das zugehörige Qualifier-Turnier zu einem Hauptturnier aus der Liste der Saisonturniere.
     */
    fun findQualifierTournamentForMain(
        mainTournament: Tournament,
        seasonTournaments: List<Tournament>
    ): Tournament? {
        val mainEvent = try {
            TournamentEvent.valueOf(mainTournament.type)
        } catch (_: Exception) {
            return null
        }
        val qualifierEvent = mainEvent.qualifierEventForMain ?: return null
        return when (qualifierEvent) {
            TournamentEvent.QUALIFIER_ET -> {
                val expectedQualName = "${mainTournament.name} Qualifier"
                seasonTournaments.firstOrNull {
                    it.type == TournamentEvent.QUALIFIER_ET.name && it.name == expectedQualName
                }
            }
            TournamentEvent.QUALIFIER_GRAND_SLAM -> {
                seasonTournaments.firstOrNull { it.type == TournamentEvent.QUALIFIER_GRAND_SLAM.name }
            }
            TournamentEvent.QUALIFIER_WM -> {
                seasonTournaments.firstOrNull { it.type == TournamentEvent.QUALIFIER_WM.name }
            }
            else -> null
        }
    }

    /**
     * Ermittelt alle vorqualifizierten Teilnehmer des Hauptturniers zum aktuellen Stand VOR dem Qualifier
     * und speichert sie in tournament_participants für mainTournament.id (Idempotent).
     */
    suspend fun freezePreQualifiedParticipantsForMainTournament(
        qualifierTournament: Tournament,
        seasonId: Long,
        careerId: Long,
        persistToDb: Boolean = true
    ): List<TournamentParticipant> = withContext(Dispatchers.IO) {
        val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
        val mainTournament = findMainTournamentForQualifier(qualifierTournament, seasonTournaments)

        val existingParticipants = tournamentRepository.getParticipantsForTournament(mainTournament.id)
        if (existingParticipants.isNotEmpty()) {
            return@withContext existingParticipants
        }

        val tourCardPlayerIds = seasonRepository.getTourCardPlayerIdsForSeason(seasonId)
        val allPlayers = playerRepository.getAllPlayersList()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)

        val mainEvent = TournamentEvent.valueOf(mainTournament.type)
        val participants = mutableListOf<TournamentParticipant>()

        when (mainEvent) {
            TournamentEvent.EUROPEAN_TOUR -> {
                val (top16Oom, top16PtOom) = resolvePrequalifiedTourCardIds(
                    mainEvent,
                    tourCardPlayerIds,
                    oomEntries,
                    ptOomEntries,
                    allPlayers
                )

                top16Oom.forEachIndexed { index, playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = index + 1,
                            qualificationType = "OOM"
                        )
                    )
                }

                top16PtOom.forEachIndexed { index, playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = index + 1,
                            qualificationType = "PT_OOM"
                        )
                    )
                }

                val effectiveHostCountry = requireNotNull(mainTournament.hostCountry?.takeIf { it.isNotBlank() }) { "Gastgeberland fehlt für Turnier ${mainTournament.name}" }
                val excludedBase = tourCardPlayerIds.toSet() + top16Oom.toSet() + top16PtOom.toSet() + USER_ID
                val hostQualifiers = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                    setOf(effectiveHostCountry), 4, excludedBase, allPlayers, oomEntries
                )

                val excludedAfterHost = excludedBase + hostQualifiers.toSet()
                val nordicBalticQualifiers = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                    ResolveNonSimulatedQualifiersUseCase.NORDIC_BALTIC, 1, excludedAfterHost, allPlayers, oomEntries
                )
                val excludedAfterNb = excludedAfterHost + nordicBalticQualifiers.toSet()
                val eastEuropeQualifiers = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                    ResolveNonSimulatedQualifiersUseCase.EAST_SOUTH_EAST_EUROPE, 1, excludedAfterNb, allPlayers, oomEntries
                )

                hostQualifiers.forEach { playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = null,
                            qualificationType = "HOST_NATION"
                        )
                    )
                }

                (nordicBalticQualifiers + eastEuropeQualifiers).forEach { playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = null,
                            qualificationType = "QUALIFIER"
                        )
                    )
                }
            }

            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val grandSlamPreQualified = grandSlamQualificationResolver.resolvePreQualifiedBeforeQualifier(
                    mainTournamentId = mainTournament.id,
                    seasonId = seasonId,
                    tourCardPlayerIds = tourCardPlayerIds,
                    allPlayers = allPlayers,
                    oomEntries = oomEntries
                )
                participants.addAll(grandSlamPreQualified)
            }

            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val (top40Oom, top40PtOom) = resolvePrequalifiedTourCardIds(
                    mainEvent,
                    tourCardPlayerIds,
                    oomEntries,
                    ptOomEntries,
                    allPlayers
                )

                val tc80 = top40Oom + top40PtOom
                val sortedTc80ByOom = sortPlayerIdsByOom(tc80, oomEntries, allPlayers)
                val top32Oom = sortedTc80ByOom.take(32)
                val ranks33To64 = sortedTc80ByOom.drop(32).take(32)
                val ranks65To80 = sortedTc80ByOom.drop(64)

                top32Oom.forEachIndexed { index, playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = index + 1,
                            qualificationType = "OOM"
                        )
                    )
                }

                ranks33To64.forEach { playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = null,
                            qualificationType = "PT_OOM"
                        )
                    )
                }

                ranks65To80.forEach { playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = null,
                            qualificationType = "QUALIFIER"
                        )
                    )
                }

                val excludedBase = tourCardPlayerIds.toSet() + tc80.toSet() + USER_ID
                val currentExcluded = excludedBase.toMutableSet()

                val wmQualifiers = mutableListOf<Long>()
                fun addRegional(countries: Set<String>, count: Int) {
                    val resolved = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                        countries, count, currentExcluded, allPlayers, oomEntries
                    )
                    wmQualifiers.addAll(resolved)
                    currentExcluded.addAll(resolved)
                }

                addRegional(ResolveNonSimulatedQualifiersUseCase.DACH, 2)
                addRegional(ResolveNonSimulatedQualifiersUseCase.NORDIC_BALTIC, 2)
                addRegional(ResolveNonSimulatedQualifiersUseCase.EAST_SOUTH_EAST_EUROPE, 2)
                addRegional(setOf("CZE"), 1)
                addRegional(setOf("POL"), 1)
                addRegional(setOf("HUN"), 1)
                addRegional(ResolveNonSimulatedQualifiersUseCase.MEDITERRANEAN, 1)
                addRegional(ResolveNonSimulatedQualifiersUseCase.NLD_BEL, 2)
                addRegional(ResolveNonSimulatedQualifiersUseCase.ASIAN_TOUR, 4)
                addRegional(setOf("CHN"), 1)
                addRegional(setOf("JPN"), 1)
                addRegional(setOf("IND"), 1)
                addRegional(ResolveNonSimulatedQualifiersUseCase.CDC_NORTH_AMERICA, 2)
                addRegional(ResolveNonSimulatedQualifiersUseCase.CDLC_LATIN_AMERICA, 1)
                addRegional(ResolveNonSimulatedQualifiersUseCase.OCEANIA, 2)
                addRegional(ResolveNonSimulatedQualifiersUseCase.AFRICA, 2)

                val unranked20 = resolveNonSimulatedQualifiersUseCase.resolveUnrankedPool(
                    20, currentExcluded, allPlayers
                )
                wmQualifiers.addAll(unranked20)

                wmQualifiers.forEach { playerId ->
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournament.id,
                            playerId = playerId,
                            seedNumber = null,
                            qualificationType = "QUALIFIER"
                        )
                    )
                }
            }

            else -> {
                throw IllegalArgumentException("Event ${mainTournament.type} hat keinen vorgeschalteten Qualifier.")
            }
        }

        if (persistToDb) {
            tournamentRepository.insertParticipants(participants)
        }
        participants
    }
}
