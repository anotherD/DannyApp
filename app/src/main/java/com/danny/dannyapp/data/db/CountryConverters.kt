package com.danny.dannyapp.data.db

import androidx.room.TypeConverter

class CountryConverters {

    @TypeConverter
    fun fromCountry(country: Country): String {
        return country.code
    }

    @TypeConverter
    fun toCountry(code: String): Country {
        return Country.fromCode(code)
    }
}
