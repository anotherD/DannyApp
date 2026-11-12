package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.TextView
import com.danny.dannyapp.R

class TournamentDetailActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_detail)

        val tournamentId = intent.getLongExtra(EXTRA_TOURNAMENT_ID, -1L)
        val careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)

        setupToolbar(
            titleText = getString(R.string.tournament_detail),
            subtitleText = "ID: $tournamentId",
            showBack = true,
            rootViewId = R.id.tournamentDetailMain
        )

        val tv: TextView = findViewById(R.id.tvPlaceholder)
        tv.text = "Turnier-ID: $tournamentId\nKarriere-ID: $careerId\n\n(Hier entsteht die vollständige Turnieransicht mit Runden, Gruppenphase und Match-Steuerung)"
    }

    companion object {
        const val EXTRA_TOURNAMENT_ID = "extra_tournament_id"
        const val EXTRA_CAREER_ID = "extra_career_id"
    }
}
