package com.danny.dannyapp.ui

import android.content.Context
import com.danny.dannyapp.R

object TournamentUiHelper {
    fun getDisplayRoundName(roundKey: String, context: Context): String {
        return when (roundKey) {
            "WINNER" -> context.getString(R.string.round_winner)
            "F" -> context.getString(R.string.round_f)
            "SF" -> context.getString(R.string.round_sf)
            "QF" -> context.getString(R.string.round_qf)
            "L16" -> context.getString(R.string.round_l16)
            "L32" -> context.getString(R.string.round_l32)
            "L48" -> context.getString(R.string.round_l48)
            "L64" -> context.getString(R.string.round_l64)
            "L128" -> context.getString(R.string.round_l128)
            "R3_96" -> context.getString(R.string.round_r3_96)
            "R2_128" -> context.getString(R.string.round_r2_128)
            "R1_160" -> context.getString(R.string.round_r1_160)
            "GROUP_2ND" -> context.getString(R.string.round_group_2nd)
            "GROUP_3RD" -> context.getString(R.string.round_group_3rd)
            "GROUP" -> context.getString(R.string.round_group)
            else -> roundKey
        }
    }
}
