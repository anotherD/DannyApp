package com.danny.dannyapp.domain.model

data class RelegatedPlayerSummary(
    val playerId: Long,
    val playerName: String,
    val countryCode: String,
    val oomRank: Int
)

data class SeasonSummaryData(
    val careerName: String,
    val completedSeasonNumber: Int,
    val nextSeasonNumber: Int,
    val currentCareerLevel: Int,
    // Persönliche User-Daten
    val userName: String,
    val userCountryCode: String,
    val oomRank: Int,
    val oomPrizeMoney: Double,
    val ptOomRank: Int,
    val ptOomPrizeMoney: Double,
    val pcOomRank: Int,
    val pcOomPrizeMoney: Double,
    val etOomRank: Int,
    val etOomPrizeMoney: Double,
    val tournamentsPlayed: Int,
    val matchesWon: Int,
    val matchesLost: Int,
    val bestTournamentName: String?,
    val bestReachedRoundKey: String?,
    val tourCardOutcome: UserTourCardOutcome,
    val relegatedPlayers: List<RelegatedPlayerSummary>,
    // Saison-Highlights (Alle Spieler)
    val worldChampion: MajorWinnerSummary?,
    val majorWinners: List<MajorWinnerSummary>,
    val oomLeader: MajorWinnerSummary?,
    val topClimber: ClimberSummary?
)
