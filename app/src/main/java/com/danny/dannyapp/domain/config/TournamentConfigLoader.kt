package com.danny.dannyapp.domain.config

import android.content.Context
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
        return parse(json)
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
            events[event] = EventRuleConfig(
                totalPrizeFund = eventJson.getDouble("totalPrizeFund"),
                prizes = parsePrizes(eventJson.getJSONObject("prizes")),
                distances = parseDistances(eventJson.getJSONObject("distances"))
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

    private fun parseDistances(distancesJson: JSONObject): Map<TournamentRound, MatchDistance> {
        val distances = mutableMapOf<TournamentRound, MatchDistance>()
        val keys = distancesJson.keys()
        while (keys.hasNext()) {
            val roundName = keys.next()
            val distanceJson = distancesJson.getJSONObject(roundName)
            distances[TournamentRound.valueOf(roundName)] = MatchDistance(
                bestOf = distanceJson.getInt("bestOf"),
                format = MatchFormatType.valueOf(distanceJson.getString("format")),
                legsPerSet = distanceJson.getInt("legsPerSet"),
                twoClearLegsRule = distanceJson.getBoolean("twoClearLegsRule")
            )
        }
        return distances
    }

    private fun parseCalendar(calendarJson: org.json.JSONArray): List<CalendarEntryConfig> {
        val calendar = mutableListOf<CalendarEntryConfig>()
        for (index in 0 until calendarJson.length()) {
            val entryJson = calendarJson.getJSONObject(index)
            calendar.add(
                CalendarEntryConfig(
                    order = entryJson.getInt("order"),
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

    private fun validateDistances(event: TournamentEvent, distances: Map<TournamentRound, MatchDistance>) {
        for (round in event.getRoundSequence()) {
            if (round !in distances) {
                throw IllegalStateException(
                    "Distanz für Runde '$round' im Turnier '$event' fehlt im distances-Block."
                )
            }
        }
        val unknownRounds = distances.keys - event.getRoundSequence().toSet()
        if (unknownRounds.isNotEmpty()) {
            throw IllegalStateException(
                "Unbekannte Runden im distances-Block für '$event': " +
                    unknownRounds.joinToString { it.name } + "."
            )
        }
    }

    private fun validatePrizes(event: TournamentEvent, ruleConfig: EventRuleConfig) {
        if (ruleConfig.totalPrizeFund <= 0.0) return

        val expectedRounds = event.getRoundSequence() + TournamentRound.WINNER
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
        val distances: Map<TournamentRound, MatchDistance>
    )

    data class SeasonConfig(
        val seasonYear: Int,
        val source: String,
        val events: Map<TournamentEvent, EventRuleConfig>,
        val calendar: List<CalendarEntryConfig>
    )
}
