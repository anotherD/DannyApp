package com.danny.dannyapp.domain.model

import androidx.annotation.StringRes
import com.danny.dannyapp.R

enum class TournamentRound(@get:StringRes val displayNameResId: Int) {
    QUAL_PRELIM(R.string.round_qual_prelim),
    QUAL_R1(R.string.round_qual_r1),
    QUAL_R2(R.string.round_qual_r2),
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
        fun fromKey(roundKey: String): TournamentRound? {
            return entries.find { it.name == roundKey }
        }
    }
}
