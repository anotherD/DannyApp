package com.danny.dannyapp.domain.enums

import androidx.annotation.ColorRes
import com.danny.dannyapp.R

enum class SkillLevel(val value: Int, @get:ColorRes val colorResId: Int) {
    LEVEL_1(1, R.color.skill_level_1),
    LEVEL_2(2, R.color.skill_level_2),
    LEVEL_3(3, R.color.skill_level_3),
    LEVEL_4(4, R.color.skill_level_4),
    LEVEL_5(5, R.color.skill_level_5),
    LEVEL_6(6, R.color.skill_level_6),
    LEVEL_7(7, R.color.skill_level_7),
    LEVEL_8(8, R.color.skill_level_8),
    LEVEL_9(9, R.color.skill_level_9),
    LEVEL_10(10, R.color.skill_level_10);

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
