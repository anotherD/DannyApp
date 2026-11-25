package com.danny.dannyapp.domain.config

import android.content.Context
import com.danny.dannyapp.domain.enums.MatchDistancePreset
import com.danny.dannyapp.domain.enums.MatchFormatType
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.MatchDistance
import org.json.JSONObject
import java.io.InputStream

/**
 * Zentraler Loader für die Saison-Konfiguration aus `assets/season_calendar_2027.json`.
 *
 * Parst Preisgelder, Match-Distanzen und den Saisonkalender und validiert die Daten
 * strikt (Fail-Fast): Fehlerhafte Konfigurationen werfen sofort eine [IllegalStateException].
 */
object TournamentConfigLoader {

    private const val ASSET_FILE_NAME = "season_calendar_2027.json"

    /** Gewichtete Anzahl Teilnehmer pro Runde für die Preisgeld-Plausibilität. */
    private val ROUND_MULTIPLIERS: Map<TournamentRound, Int> = mapOf(
        TournamentRound.WINNER to 1,
        TournamentRound.F to 1,
        TournamentRound.SF to 2,
        TournamentRound.QF to 4,
        TournamentRound.L16 to 8,
        TournamentRound.L32 to 16,
        TournamentRound.GROUP_2ND to 16,
        TournamentRound.GROUP_3RD to 16,
        TournamentRound.L48 to 16,
        TournamentRound.L64 to 32,
        TournamentRound.R3_96 to 32,
        TournamentRound.R2_128 to 32,
        TournamentRound.R1_160 to 32,
        TournamentRound.L128 to 64
    )

    @Volatile
    private var cachedConfig: SeasonConfig? = null

    /**
     * Lädt die Konfiguration aus den Assets (Thread-sicher, mit Speicher-Cache).
     */
    fun loadFromAssets(context: Context): SeasonConfig {
        cachedConfig?.let { return it }
        return synchronized(this) {
            cachedConfig ?: loadFromStream(context.assets.open(ASSET_FILE_NAME))
                .also { cachedConfig = it }
        }
    }

    /**
     * Lädt die Konfiguration aus einem [InputStream] (z. B. für JVM-Tests ohne Android-Context).
     */
    fun loadFromStream(inputStream: InputStream): SeasonConfig {
        val json = inputStream.bufferedReader().use { it.readText() }
        return parse(json).also { cachedConfig = it }
    }

    /**
     * Parst den JSON-Text in eine [SeasonConfig] und führt die Fail-Fast-Validierung durch.
     */
    fun parse(json: String): SeasonConfig {
        val root = JSONObject(json)
        val seasonYear = root.getInt("seasonYear")
        val source = root.getString("source")
        val events = parseEvents(root.getJSONObject("events"))
        val calendar = parseCalendar(root.getJSONArray("calendar"))
        val config = SeasonConfig(
            seasonYear = seasonYear,
            source = source,
            events = events,
            calendar = calendar
        )
        validate(config)
        return config
    }

    /**
     * Gibt die geladene Konfiguration zurück oder null, wenn sie noch nicht geladen wurde.
     */
    fun safeConfig(): SeasonConfig? = cachedConfig

    /**
     * Cache-Reset für isolierte Tests.
     */
    fun clearCache() {
        cachedConfig = null
    }

    private fun parseEvents(eventsJson: JSONObject): Map<TournamentEvent, EventRuleConfig> {
        val events = mutableMapOf<TournamentEvent, EventRuleConfig>()
        val keys = eventsJson.keys()
        while (keys.hasNext()) {
            val eventName = keys.next()
            val event = TournamentEvent.valueOf(eventName)
            val eventJson = eventsJson.getJSONObject(eventName)
            val format = MatchFormatType.valueOf(eventJson.getString("format"))
            val twoClearLegsRule = eventJson.getBoolean("twoClearLegsRule")
            events[event] = EventRuleConfig(
                totalPrizeFund = eventJson.getDouble("totalPrizeFund"),
                prizes = parsePrizes(eventJson.getJSONObject("prizes")),
                distances = parseDistances(event, eventJson.getJSONObject("distances"), format, twoClearLegsRule)
            )
        }
        return events
    }

    private fun parsePrizes(prizesJson: JSONObject): Map<TournamentRound, Double> {
        val prizes = mutableMapOf<TournamentRound, Double>()
        val keys = prizesJson.keys()
        while (keys.hasNext()) {
            val roundName = keys.next()
            prizes[TournamentRound.valueOf(roundName)] = prizesJson.getDouble(roundName)
        }
        return prizes
    }

    private fun parseDistances(
        event: TournamentEvent,
        distancesJson: JSONObject,
        format: MatchFormatType,
        twoClearLegsRule: Boolean
    ): Map<TournamentRound, Map<MatchDistancePreset, MatchDistance>> {
        val distances = mutableMapOf<TournamentRound, Map<MatchDistancePreset, MatchDistance>>()
        val keys = distancesJson.keys()
        while (keys.hasNext()) {
            val roundName = keys.next()
            val roundJson = distancesJson.getJSONObject(roundName)
            val presetMap = mutableMapOf<MatchDistancePreset, MatchDistance>()
            val presetKeys = roundJson.keys()
            while (presetKeys.hasNext()) {
                val presetName = presetKeys.next()
                val preset = MatchDistancePreset.valueOf(presetName)
                val value = roundJson.get(presetName)
                val bestOf: Int
                val legsPerSet: Int

                if (format == MatchFormatType.LEGS) {
                    if (value is org.json.JSONArray) {
                        throw IllegalStateException(
                            "Erwartete Ganzzahl für bestOf bei LEGS-Format in Preset '$presetName', Runde '$roundName', Turnier '$event', aber JSONArray erhalten."
                        )
                    }
                    bestOf = roundJson.getInt(presetName)
                    legsPerSet = 1
                } else {
                    if (value !is org.json.JSONArray) {
                        throw IllegalStateException(
                            "Erwartete JSONArray [bestOf, legsPerSet] für Set-Format '$format' in Preset '$presetName', Runde '$roundName', Turnier '$event'."
                        )
                    }
                    val arr = roundJson.getJSONArray(presetName)
                    if (arr.length() != 2) {
                        throw IllegalStateException(
                            "JSONArray für Set-Format '$format' muss genau 2 Elemente [bestOf, legsPerSet] enthalten, hat aber ${arr.length()} bei Preset '$presetName', Runde '$roundName' im Turnier '$event'."
                        )
                    }
                    bestOf = arr.getInt(0)
                    legsPerSet = arr.getInt(1)
                }

                presetMap[preset] = MatchDistance(
                    bestOf = bestOf,
                    format = format,
                    legsPerSet = legsPerSet,
                    twoClearLegsRule = twoClearLegsRule
                )
            }
            distances[TournamentRound.valueOf(roundName)] = presetMap
        }
        return distances
    }

    private fun parseCalendar(calendarJson: org.json.JSONArray): List<CalendarEntryConfig> {
        val calendar = mutableListOf<CalendarEntryConfig>()
        for (index in 0 until calendarJson.length()) {
            val entryJson = calendarJson.getJSONObject(index)
            require(!entryJson.has("order")) {
                "Redundantes 'order'-Feld im Kalender-Eintrag '${entryJson.optString("name")}' ist nicht mehr erlaubt"
            }
            calendar.add(
                CalendarEntryConfig(
                    order = index + 1,
                    name = entryJson.getString("name"),
                    type = TournamentEvent.valueOf(entryJson.getString("type")),
                    hostCountry = entryJson.getString("hostCountry"),
                    hostCity = entryJson.getString("hostCity")
                )
            )
        }
        return calendar
    }

    /**
     * Strikte Fail-Fast-Validierung der kompletten Saison-Konfiguration.
     * Wirft bei jedem Fehler eine sprechende [IllegalStateException].
     */
    fun validate(config: SeasonConfig) {
        validateEventsPresent(config)
        for ((event, ruleConfig) in config.events) {
            validateDistances(event, ruleConfig.distances)
            validatePrizes(event, ruleConfig)
        }
        validateCalendar(config.calendar)
    }

    private fun validateEventsPresent(config: SeasonConfig) {
        for (event in TournamentEvent.entries) {
            if (event !in config.events) {
                throw IllegalStateException(
                    "Turnierevent '$event' fehlt im events-Block der Saison-Konfiguration."
                )
            }
        }
    }

    private fun validateDistances(event: TournamentEvent, distances: Map<TournamentRound, Map<MatchDistancePreset, MatchDistance>>) {
        val roundsToValidate: List<TournamentRound> = if (event.isQualifier) {
            if (TournamentRound.QUAL_FINAL !in distances) {
                throw IllegalStateException(
                    "Qualifier-Event '$event' muss mindestens die Runde 'QUAL_FINAL' in distances definieren."
                )
            }
            val invalidRounds = distances.keys.filterNot { TournamentRound.isQualifierRoundKey(it.name) }
            if (invalidRounds.isNotEmpty()) {
                throw IllegalStateException(
                    "Ungültige Runden im distances-Block für Qualifier-Event '$event': " +
                        invalidRounds.joinToString { it.name } + "."
                )
            }
            val sortedDefinedRounds = TournamentRound.entries.filter { it in distances.keys }
            if (sortedDefinedRounds.last() != TournamentRound.QUAL_FINAL) {
                throw IllegalStateException(
                    "QUAL_FINAL muss die letzte definierte Runde im Qualifier-Event '$event' sein."
                )
            }
            sortedDefinedRounds
        } else {
            val roundSequence = event.getRoundSequence()
            val unknownRounds = distances.keys - roundSequence.toSet()
            if (unknownRounds.isNotEmpty()) {
                throw IllegalStateException(
                    "Unbekannte Runden im distances-Block für '$event': " +
                        unknownRounds.joinToString { it.name } + "."
                )
            }
            roundSequence
        }

        for (round in roundsToValidate) {
            val presetMap = distances[round]
                ?: throw IllegalStateException(
                    "Distanzen für Runde '$round' im Turnier '$event' fehlen im distances-Block."
                )
            for (preset in MatchDistancePreset.entries) {
                val distance = presetMap[preset]
                    ?: throw IllegalStateException(
                        "Distanz für Preset '$preset' in Runde '$round' im Turnier '$event' fehlt."
                    )
                if (distance.bestOf < 3 || distance.bestOf % 2 != 1) {
                    throw IllegalStateException(
                        "Ungültiger bestOf-Wert '${distance.bestOf}' für Preset '$preset' in Runde '$round' im Turnier '$event' (muss >= 3 und ungerade sein)."
                    )
                }
                if (distance.format == MatchFormatType.LEGS) {
                    if (distance.legsPerSet != 1) {
                        throw IllegalStateException(
                            "Ungültiger legsPerSet-Wert '${distance.legsPerSet}' für Format LEGS (muss 1 sein) bei Preset '$preset' in Runde '$round' im Turnier '$event'."
                        )
                    }
                } else {
                    if (distance.legsPerSet < 3 || distance.legsPerSet % 2 != 1) {
                        throw IllegalStateException(
                            "Ungültiger legsPerSet-Wert '${distance.legsPerSet}' für Set-Format '${distance.format}' (muss >= 3 und ungerade sein) bei Preset '$preset' in Runde '$round' im Turnier '$event'."
                        )
                    }
                }
            }
            val unknownPresets = presetMap.keys - MatchDistancePreset.entries.toSet()
            if (unknownPresets.isNotEmpty()) {
                throw IllegalStateException(
                    "Unbekannte Presets im distances-Block für Runde '$round' in '$event': " +
                        unknownPresets.joinToString { it.name } + "."
                )
            }
        }
    }

    private fun validatePrizes(event: TournamentEvent, ruleConfig: EventRuleConfig) {
        if (ruleConfig.totalPrizeFund <= 0.0) return

        val expectedRounds = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            listOf(
                TournamentRound.GROUP_2ND,
                TournamentRound.GROUP_3RD,
                TournamentRound.L16,
                TournamentRound.QF,
                TournamentRound.SF,
                TournamentRound.F,
                TournamentRound.WINNER
            )
        } else {
            event.getRoundSequence() + TournamentRound.WINNER
        }
        for (round in expectedRounds) {
            if (round !in ruleConfig.prizes) {
                throw IllegalStateException(
                    "Preisgeld für Runde '$round' im Turnier '$event' fehlt im prizes-Block."
                )
            }
        }
        val unknownRounds = ruleConfig.prizes.keys - expectedRounds.toSet()
        if (unknownRounds.isNotEmpty()) {
            throw IllegalStateException(
                "Unbekannte Runden im prizes-Block für '$event': " +
                    unknownRounds.joinToString { it.name } + "."
            )
        }

        val weightedSum = ruleConfig.prizes.entries.sumOf { (round, prize) ->
            prize * (ROUND_MULTIPLIERS[round] ?: 0)
        }
        if (abs(weightedSum - ruleConfig.totalPrizeFund) > 0.001) {
            throw IllegalStateException(
                "Gewichtete Preisgeldsumme für '$event' beträgt $weightedSum und weicht " +
                    "vom Gesamtpreisgeld ${ruleConfig.totalPrizeFund} ab."
            )
        }
    }

    private fun validateCalendar(calendar: List<CalendarEntryConfig>) {
        require(calendar.isNotEmpty()) { "Der Saisonkalender darf nicht leer sein." }
        val orders = calendar.map { it.order }
        val expectedOrders = (1..calendar.size).toSet()
        if (orders.toSet() != expectedOrders) {
            val missing = expectedOrders - orders.toSet()
            val duplicates = orders.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            throw IllegalStateException(
                "Kalender-Reihenfolgen sind nicht lückenlos 1..${calendar.size}. " +
                    "Fehlend: $missing, Duplikate: $duplicates."
            )
        }

        for (entry in calendar) {
            if (!Country.isValidCode(entry.hostCountry)) {
                throw IllegalStateException(
                    "Ungültiger hostCountry-Code '${entry.hostCountry}' bei Kalendereintrag " +
                        "${entry.order} ('${entry.name}')."
                )
            }
            if (entry.hostCity.isBlank()) {
                throw IllegalStateException(
                    "hostCity darf für Kalendereintrag ${entry.order} ('${entry.name}') nicht leer sein."
                )
            }
        }

        val duplicateNames = calendar.map { it.name }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys
        if (duplicateNames.isNotEmpty()) {
            throw IllegalStateException(
                "Duplizierte Turniernamen im Kalender gefunden: ${duplicateNames.joinToString()}."
            )
        }

        val majorEvents = listOf(
            TournamentEvent.WORLD_MASTERS,
            TournamentEvent.UK_OPEN,
            TournamentEvent.EUROPEAN_OPEN,
            TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX,
            TournamentEvent.EUROPEAN_CHAMPIONSHIP,
            TournamentEvent.GRAND_SLAM_OF_DARTS,
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS,
            TournamentEvent.WORLD_CHAMPIONSHIP
        )
        for (major in majorEvents) {
            val count = calendar.count { it.type == major }
            if (count != 1) {
                throw IllegalStateException(
                    "Major-Event '$major' muss exakt 1x im Kalender vorkommen, Ist: $count."
                )
            }
        }

        val europeanTourEntries = calendar.filter { it.type == TournamentEvent.EUROPEAN_TOUR }
        val qualifierEtEntries = calendar.filter { it.type == TournamentEvent.QUALIFIER_ET }
        if (europeanTourEntries.size != qualifierEtEntries.size) {
            throw IllegalStateException(
                "Anzahl der EUROPEAN_TOUR-Events (${europeanTourEntries.size}) und QUALIFIER_ET-Events (${qualifierEtEntries.size}) stimmt nicht überein."
            )
        }
        val etQualifierMap = qualifierEtEntries.associateBy { it.name }
        for (etEntry in europeanTourEntries) {
            val expectedQualifierName = "${etEntry.name} Qualifier"
            val qualifierEntry = etQualifierMap[expectedQualifierName]
                ?: throw IllegalStateException(
                    "Zu European Tour Event '${etEntry.name}' fehlt der Qualifier '$expectedQualifierName'."
                )
            if (qualifierEntry.order >= etEntry.order) {
                throw IllegalStateException(
                    "Qualifier '${qualifierEntry.name}' (order=${qualifierEntry.order}) muss vor dem Hauptturnier '${etEntry.name}' (order=${etEntry.order}) stattfinden."
                )
            }
        }

        val grandSlamQualifiers = calendar.filter { it.type == TournamentEvent.QUALIFIER_GRAND_SLAM }
        if (grandSlamQualifiers.size != 1) {
            throw IllegalStateException(
                "Es muss genau einen QUALIFIER_GRAND_SLAM im Kalender geben, Ist: ${grandSlamQualifiers.size}."
            )
        }
        val grandSlamEvent = calendar.single { it.type == TournamentEvent.GRAND_SLAM_OF_DARTS }
        if (grandSlamQualifiers[0].order >= grandSlamEvent.order) {
            throw IllegalStateException(
                "QUALIFIER_GRAND_SLAM (order=${grandSlamQualifiers[0].order}) muss vor GRAND_SLAM_OF_DARTS (order=${grandSlamEvent.order}) stattfinden."
            )
        }

        val wmQualifiers = calendar.filter { it.type == TournamentEvent.QUALIFIER_WM }
        if (wmQualifiers.size != 1) {
            throw IllegalStateException(
                "Es muss genau einen QUALIFIER_WM im Kalender geben, Ist: ${wmQualifiers.size}."
            )
        }
        val wmEvent = calendar.single { it.type == TournamentEvent.WORLD_CHAMPIONSHIP }
        if (wmQualifiers[0].order >= wmEvent.order) {
            throw IllegalStateException(
                "QUALIFIER_WM (order=${wmQualifiers[0].order}) muss vor WORLD_CHAMPIONSHIP (order=${wmEvent.order}) stattfinden."
            )
        }

        val worldMastersQualifiers = calendar.filter { it.type == TournamentEvent.QUALIFIER_WORLD_MASTERS }
        if (worldMastersQualifiers.size != 1) {
            throw IllegalStateException(
                "Es muss genau einen QUALIFIER_WORLD_MASTERS im Kalender geben, Ist: ${worldMastersQualifiers.size}."
            )
        }
        val worldMastersEvent = calendar.single { it.type == TournamentEvent.WORLD_MASTERS }
        if (worldMastersQualifiers[0].order >= worldMastersEvent.order) {
            throw IllegalStateException(
                "QUALIFIER_WORLD_MASTERS (order=${worldMastersQualifiers[0].order}) muss vor WORLD_MASTERS (order=${worldMastersEvent.order}) stattfinden."
            )
        }

        val europeanChampionship = calendar.single { it.type == TournamentEvent.EUROPEAN_CHAMPIONSHIP }
        val maxEtOrder = europeanTourEntries.maxOfOrNull { it.order } ?: 0
        if (europeanChampionship.order <= maxEtOrder) {
            throw IllegalStateException(
                "EUROPEAN_CHAMPIONSHIP (order=${europeanChampionship.order}) muss nach dem letzten EUROPEAN_TOUR-Event (order=$maxEtOrder) stattfinden."
            )
        }

        val playersChampionshipFinals = calendar.single { it.type == TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS }
        val lastPcOrder = calendar.filter { it.type == TournamentEvent.PLAYERS_CHAMPIONSHIP }.maxOfOrNull { it.order } ?: 0
        if (playersChampionshipFinals.order <= lastPcOrder) {
            throw IllegalStateException(
                "PLAYERS_CHAMPIONSHIP_FINALS (order=${playersChampionshipFinals.order}) muss nach dem letzten PLAYERS_CHAMPIONSHIP-Event (order=$lastPcOrder) stattfinden."
            )
        }
    }

    private fun abs(value: Double): Double = if (value < 0) -value else value

    // Datenklassen der Saison-Konfiguration

    data class CalendarEntryConfig(
        val order: Int,
        val name: String,
        val type: TournamentEvent,
        val hostCountry: String,
        val hostCity: String
    )

    data class EventRuleConfig(
        val totalPrizeFund: Double,
        val prizes: Map<TournamentRound, Double>,
        val distances: Map<TournamentRound, Map<MatchDistancePreset, MatchDistance>>
    ) {
        val definedRounds: List<TournamentRound>
            get() = TournamentRound.entries.filter { it in distances.keys }
    }

    data class SeasonConfig(
        val seasonYear: Int,
        val source: String,
        val events: Map<TournamentEvent, EventRuleConfig>,
        val calendar: List<CalendarEntryConfig>
    )
}
