package com.danny.dannyapp.data.db

enum class Country(
    val code: String,
    val displayName: String,
    val flagEmoji: String
) {
    ENGLAND("ENG", "England", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
    GERMANY("GER", "Deutschland", "🇩🇪"),
    NETHERLANDS("NED", "Niederlande", "🇳🇱"),
    SCOTLAND("SCO", "Schottland", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
    WALES("WAL", "Wales", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
    NORTHERN_IRELAND("NIR", "Nordirland", "🇬🇧"),
    AUSTRIA("AUT", "Österreich", "🇦🇹"),
    SWITZERLAND("SUI", "Schweiz", "🇨🇭"),
    BELGIUM("BEL", "Belgien", "🇧🇪"),
    AUSTRALIA("AUS", "Australien", "🇦🇺"),
    USA("USA", "USA", "🇺🇸"),
    IRELAND("IRL", "Irland", "🇮🇪"),
    SPAIN("ESP", "Spanien", "🇪🇸"),
    OTHER("OTH", "Sonstige", "🌐");

    companion object {
        fun fromCode(code: String): Country {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OTHER
        }
    }
}
