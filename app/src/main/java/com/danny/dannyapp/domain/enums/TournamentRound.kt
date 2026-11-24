package com.danny.dannyapp.domain.enums

import androidx.annotation.StringRes
import com.danny.dannyapp.R

enum class TournamentRound(@get:StringRes val displayNameResId: Int) {
    QUAL_PRELIM(R.string.round_qual_prelim),
    QUAL_R1(R.string.round_qual_r1),
    QUAL_R2(R.string.round_qual_r2),
    QUAL_R3(R.string.round_qual_r3),
    QUAL_FINAL(R.string.round_qual_final),
    R1_160(R.string.round_r1_160),
    R2_128(R.string.round_r2_128),
    R3_96(R.string.round_r3_96),
    L128(R.string.round_l128),
    L64(R.string.round_l64),
    L48(R.string.round_l48),
    L32(R.string.round_l32),
    L16(R.string.round_l16),
    QF(R.string.round_qf),
    SF(R.string.round_sf),
    F(R.string.round_f),
    GROUP(R.string.round_group),
    GROUP_2ND(R.string.round_group_2nd),
    GROUP_3RD(R.string.round_group_3rd),
    WINNER(R.string.round_winner);

    companion object {
        const val QUALIFIER_ROUND_PREFIX = "QUAL_R"

        fun fromKey(roundKey: String): TournamentRound? {
            return entries.find { it.name == roundKey }
        }

        fun qualifierRoundKey(roundNumber: Int): String {
            require(roundNumber >= 1) { "roundNumber muss >= 1 sein, war: $roundNumber" }
            return "$QUALIFIER_ROUND_PREFIX$roundNumber"
        }

        fun parseQualifierRoundNumber(roundKey: String): Int? {
            if (!roundKey.startsWith(QUALIFIER_ROUND_PREFIX)) return null
            return roundKey.removePrefix(QUALIFIER_ROUND_PREFIX).toIntOrNull()?.takeIf { it >= 1 }
        }

        fun isQualifierRoundKey(roundKey: String): Boolean {
            return roundKey == QUAL_FINAL.name || roundKey == QUAL_PRELIM.name || parseQualifierRoundNumber(roundKey) != null
        }

        fun buildQualifierRoundSequenceKeys(boardSizePerBoard: Int): List<String> {
            require(boardSizePerBoard >= 2 && (boardSizePerBoard and (boardSizePerBoard - 1)) == 0) {
                "boardSizePerBoard muss eine Zweierpotenz >= 2 sein, war: $boardSizePerBoard"
            }
            val roundCount = Integer.numberOfTrailingZeros(boardSizePerBoard)
            val roundKeys = ArrayList<String>(roundCount)
            for (roundIndex in 1 until roundCount) {
                roundKeys.add(qualifierRoundKey(roundIndex))
            }
            roundKeys.add(QUAL_FINAL.name)
            return roundKeys
        }
    }
}
