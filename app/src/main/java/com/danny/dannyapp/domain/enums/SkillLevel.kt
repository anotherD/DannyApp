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

        /**
         * Returns the corresponding [SkillLevel] for the given integer value,
         * or [DEFAULT] if the value is outside the 1..10 range.
         */
        fun fromValue(value: Int): SkillLevel {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}
