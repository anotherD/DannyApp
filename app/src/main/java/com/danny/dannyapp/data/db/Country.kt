package com.danny.dannyapp.data.db

import java.util.Locale

data class Country(
    val code: String,
    val displayName: String,
    val flagEmoji: String
) {
    companion object {
        private val HOME_NATIONS = mapOf(
            "ENG" to Pair("England", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
            "SCO" to Pair("Schottland", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
            "WAL" to Pair("Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
            "NIR" to Pair("Nordirland", "🇬🇧")
        )

        fun getDisplayName(code: String): String {
            val upper = code.trim().uppercase()
            HOME_NATIONS[upper]?.let { return it.first }
            return getLocaleFromCode(upper)?.getDisplayCountry(Locale.getDefault()).orEmpty().ifEmpty { upper }
        }

        fun getFlagEmoji(code: String): String {
            val upper = code.trim().uppercase()
            HOME_NATIONS[upper]?.let { return it.second }
            val iso2 = getIso2FromCode(upper)
            return getFlagEmojiFromIso2(iso2)
        }

        fun getAllCountries(): List<Country> {
            val list = mutableListOf<Country>()
            HOME_NATIONS.forEach { (code, pair) ->
                list.add(Country(code, pair.first, pair.second))
            }
            for (iso2 in Locale.getISOCountries()) {
                val locale = Locale.Builder().setRegion(iso2).build()
                val iso3 = try { locale.isO3Country.uppercase() } catch (_: Exception) { iso2 }
                if (list.none { it.code.equals(iso3, ignoreCase = true) }) {
                    val name = locale.getDisplayCountry(Locale.getDefault())
                    if (name.isNotEmpty()) {
                        list.add(Country(iso3, name, getFlagEmojiFromIso2(iso2)))
                    }
                }
            }
            return list.sortedBy { it.displayName }
        }

        private fun getLocaleFromCode(code: String): Locale? {
            val iso2 = getIso2FromCode(code)
            return if (iso2.isNotEmpty()) Locale.Builder().setRegion(iso2).build() else null
        }

        private fun getIso2FromCode(code: String): String {
            if (code.length == 2) return code.uppercase()
            val localeIso2 = Locale.getISOCountries().firstOrNull { iso2 ->
                try {
                    Locale.Builder().setRegion(iso2).build().isO3Country.equals(code, ignoreCase = true)
                } catch (_: Exception) {
                    false
                }
            }
            return localeIso2 ?: ""
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
