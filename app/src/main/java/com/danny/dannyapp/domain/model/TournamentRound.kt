package com.danny.dannyapp.domain.model

enum class TournamentRound(val displayName: String) {
    GROUP("Gruppenphase (Match)"),
    GROUP_3RD("Gruppenphase (Platz 3)"),
    GROUP_2ND("Gruppenphase (Platz 2)"),
    R1_160("Runde 1 (Last 160)"),
    R2_128("Runde 2 (Last 128)"),
    L128("Last 128"),
    R3_96("Runde 3 (Last 96)"),
    L64("Last 64"),
    L48("Last 48"),
    L32("Last 32"),
    L16("Achtelfinale (Last 16)"),
    QF("Viertelfinale"),
    SF("Halbfinale"),
    F("Finale"),
    WINNER("Turniersieger")
}
