package com.danny.dannyapp.data.db

import java.util.Locale

data class Country(
    val code: String,
    val displayName: String,
    val flagEmoji: String
) {
    companion object {
        // Vordefinierte Darts & Haupt-Sportnationen (Alpha-3 / IOC Standard)
        val ENGLAND = Country("ENG", "England", "🏴󠁧󠁢󠁥󠁮󠁧󠁿")
        val SCOTLAND = Country("SCO", "Schottland", "🏴󠁧󠁢󠁳󠁣󠁴󠁿")
        val WALES = Country("WAL", "Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿")
        val NORTHERN_IRELAND = Country("NIR", "Nordirland", "🇬🇧")
        val GERMANY = Country("GER", "Deutschland", "🇩🇪")
        val NETHERLANDS = Country("NED", "Niederlande", "🇳🇱")
        val AUSTRIA = Country("AUT", "Österreich", "🇦🇹")
        val SWITZERLAND = Country("SUI", "Schweiz", "🇨🇭")
        val BELGIUM = Country("BEL", "Belgien", "🇧🇪")
        val AUSTRALIA = Country("AUS", "Australien", "🇦🇺")
        val USA = Country("USA", "USA", "🇺🇸")
        val IRELAND = Country("IRL", "Irland", "🇮🇪")
        val SPAIN = Country("ESP", "Spanien", "🇪🇸")
        val OTHER = Country("OTH", "Sonstige", "🌐")

        private val SPECIAL_COUNTRIES = listOf(
            ENGLAND, SCOTLAND, WALES, NORTHERN_IRELAND,
            GERMANY, NETHERLANDS, AUSTRIA, SWITZERLAND,
            BELGIUM, AUSTRALIA, USA, IRELAND, SPAIN
        )

        /**
         * Liefert die vollständige Liste aller Länder der Welt (ISO 3166 + Darts Home Nations).
         */
        fun getAllCountries(): List<Country> {
            val list = mutableListOf<Country>()
            list.addAll(SPECIAL_COUNTRIES)

            // Dynamische ISO 3166-1 Länder hinzufügen
            val isoCodes = Locale.getISOCountries()
            for (iso2 in isoCodes) {
                val locale = Locale.Builder().setRegion(iso2).build()
                val iso3 = try { locale.isO3Country.uppercase() } catch (_: Exception) { iso2 }
                if (list.none { it.code.equals(iso3, ignoreCase = true) || it.code.equals(iso2, ignoreCase = true) }) {
                    val name = locale.getDisplayCountry(Locale.getDefault())
                    val flag = getFlagEmojiFromIso2(iso2)
                    if (name.isNotEmpty()) {
                        list.add(Country(iso3, name, flag))
                    }
                }
            }

            return list.sortedBy { it.displayName }
        }

        /**
         * Findet ein Land anhand seines 2- oder 3-stelligen Ländercodes.
         */
        fun fromCode(code: String): Country {
            val upper = code.trim().uppercase()
            SPECIAL_COUNTRIES.firstOrNull { it.code == upper }?.let { return it }

            return getAllCountries().firstOrNull {
                it.code.equals(upper, ignoreCase = true)
            } ?: OTHER
        }

        private fun getFlagEmojiFromIso2(iso2: String): String {
            if (iso2.length != 2) return "🌐"
            val upper = iso2.uppercase()
            val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        }
    }
}
