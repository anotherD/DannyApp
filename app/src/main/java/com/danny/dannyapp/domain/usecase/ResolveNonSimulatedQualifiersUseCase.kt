package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.domain.model.OrderOfMeritEntry

class ResolveNonSimulatedQualifiersUseCase {

    fun resolveRegional(
        regionCountries: Set<String>,
        count: Int,
        excludedPlayerIds: Set<Long>,
        allPlayers: List<Player>,
        oomList: List<OrderOfMeritEntry>
    ): List<Long> {
        val upperRegionCountries = regionCountries.map { it.uppercase() }.toSet()
        val nonTcPlayers = allPlayers.filter { it.tourCardYear == 0 && it.id !in excludedPlayerIds }

        val regionalPool = nonTcPlayers.filter { it.countryCode.uppercase() in upperRegionCountries }.map { it.id }
        val sortedRegional = QualificationHelper.sortPlayerIdsByOom(regionalPool, oomList)

        val selected = sortedRegional.take(count).toMutableList()
        if (selected.size < count) {
            val needed = count - selected.size
            val currentExcluded = excludedPlayerIds + selected.toSet()
            val internationalPool = allPlayers
                .filter { it.tourCardYear == 0 && it.id !in currentExcluded }
                .map { it.id }
            val sortedInternational = QualificationHelper.sortPlayerIdsByOom(internationalPool, oomList)
            val filled = sortedInternational.take(needed)
            selected.addAll(filled)
        }
        return selected
    }

    fun resolveUnrankedPool(
        count: Int,
        excludedPlayerIds: Set<Long>,
        allPlayers: List<Player>
    ): List<Long> {
        return allPlayers
            .filter { it.tourCardYear == 0 && it.id !in excludedPlayerIds }
            .map { it.id }
            .shuffled()
            .take(count)
    }

    companion object {
        val NORDIC_BALTIC = setOf("SWE", "FIN", "DNK", "NOR", "ISL", "EST", "LVA", "LTU", "FRO", "GRL")
        val EAST_SOUTH_EAST_EUROPE = setOf("POL", "HUN", "CZE", "SVK", "HRV", "SVN", "ROU", "BGR", "UKR", "SRB", "BIH", "MKD", "MNE", "ALB")
        val MEDITERRANEAN = setOf("ITA", "ESP", "PRT", "GRC", "CYP", "MLT", "TUR", "AND", "SMR", "MCO")
        val NLD_BEL = setOf("NLD", "BEL")
        val DACH = setOf("DEU", "AUT", "CHE", "LIE", "LUX")
        val ASIAN_TOUR = setOf("PHL", "JPN", "CHN", "HKG", "TWN", "SGP", "MYS", "THA", "IND", "KOR", "IDN", "VNM", "ARE", "BHR", "KAZ", "MNG")
        val CDC_NORTH_AMERICA = setOf("USA", "CAN")
        val CDLC_LATIN_AMERICA = setOf("BRA", "ARG", "CHL", "COL", "CRI", "MEX", "PAN", "BHS", "BRB", "GUY", "JAM", "TTO")
        val OCEANIA = setOf("AUS", "NZL")
        val AFRICA = setOf("ZAF", "KEN", "UGA", "NGA", "GHA", "EGY", "MAR", "TUN", "NAM", "BWA", "ZWE", "ZMB")
    }
}
