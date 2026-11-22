package com.danny.dannyapp.ui.settings

import android.content.Intent
import android.os.Bundle
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.BaseActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Activity for managing application settings such as debug mode, simulation settings, and player configuration.
 */
class SettingsActivity : BaseActivity() {

    private lateinit var switchDebugMode: MaterialSwitch
    private lateinit var switchAutoSimulateCpu: MaterialSwitch
    private lateinit var switchAutoAdvanceRound: MaterialSwitch
    private lateinit var switchModernMatchCard: MaterialSwitch
    private lateinit var cardPlayers: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbar(getString(R.string.config_title), showBack = true, rootViewId = R.id.configurationMain, menuResId = 0)

        switchDebugMode = findViewById(R.id.switchDebugMode)
        switchAutoSimulateCpu = findViewById(R.id.switchAutoSimulateCpu)
        switchAutoAdvanceRound = findViewById(R.id.switchAutoAdvanceRound)
        switchModernMatchCard = findViewById(R.id.switchModernMatchCard)
        cardPlayers = findViewById(R.id.cardPlayers)

        switchDebugMode.isChecked = debugMode
        switchAutoSimulateCpu.isChecked = autoSimulateCpu
        switchAutoAdvanceRound.isChecked = autoAdvanceRound
        switchModernMatchCard.isChecked = settingsManager.useModernMatchCardStyle

        switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            debugMode = isChecked
            showMessage(R.string.config_saved)
        }

        switchAutoSimulateCpu.setOnCheckedChangeListener { _, isChecked ->
            autoSimulateCpu = isChecked
            showMessage(R.string.config_saved)
        }

        switchAutoAdvanceRound.setOnCheckedChangeListener { _, isChecked ->
            autoAdvanceRound = isChecked
            showMessage(R.string.config_saved)
        }

        switchModernMatchCard.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.useModernMatchCardStyle = isChecked
            showMessage(R.string.config_saved)
        }

        cardPlayers.setOnClickListener {
            val intent = Intent(this, SettingsPlayersActivity::class.java)
            startActivity(intent)
        }
    }
}
