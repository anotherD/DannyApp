package com.danny.dannyapp.domain.enums

enum class SkillLevel(val value: Int) {
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3),
    LEVEL_4(4),
    LEVEL_5(5),
    LEVEL_6(6),
    LEVEL_7(7),
    LEVEL_8(8),
    LEVEL_9(9),
    LEVEL_10(10);

    companion object {
        val DEFAULT = LEVEL_5

        const val MIN_VALUE = 1
        const val MAX_VALUE = 10

        /**
         * Returns the corresponding [SkillLevel] for the given integer value.
         * Throws [IllegalArgumentException] if the value is outside the [MIN_VALUE]..[MAX_VALUE] range.
         *
         * @param value The integer skill value (1..10).
         * @return The matching [SkillLevel].
         */
        fun fromValue(value: Int): SkillLevel {
            require(value in MIN_VALUE..MAX_VALUE) { "Ungültiges SkillLevel: $value" }
            return entries.first { it.value == value }
        }
    }
}
