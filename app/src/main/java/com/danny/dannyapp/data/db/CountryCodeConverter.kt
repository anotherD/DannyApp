package com.danny.dannyapp.data.db

import androidx.room.TypeConverter

class CountryCodeConverter {

    @TypeConverter
    fun toCountryCode(country: Country): String {
        return country.code
    }

    @TypeConverter
    fun fromCountryCode(code: String): Country {
        return Country.fromCode(code)
    }
}
