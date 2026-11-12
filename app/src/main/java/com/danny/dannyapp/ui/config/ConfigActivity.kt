package com.danny.dannyapp.ui.config

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.BaseActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class ConfigActivity : BaseActivity() {

    private lateinit var switchDebugMode: MaterialSwitch
    private lateinit var switchAutoSimulateCpu: MaterialSwitch
    private lateinit var switchAutoAdvanceRound: MaterialSwitch
    private lateinit var cardPlayers: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        setupToolbar(getString(R.string.config_title), showBack = true, rootViewId = R.id.configurationMain, menuResId = 0)

        switchDebugMode = findViewById(R.id.switchDebugMode)
        switchAutoSimulateCpu = findViewById(R.id.switchAutoSimulateCpu)
        switchAutoAdvanceRound = findViewById(R.id.switchAutoAdvanceRound)
        cardPlayers = findViewById(R.id.cardPlayers)

        switchDebugMode.isChecked = debugMode
        switchAutoSimulateCpu.isChecked = autoSimulateCpu
        switchAutoAdvanceRound.isChecked = autoAdvanceRound

        switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            debugMode = isChecked
            Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }

        switchAutoSimulateCpu.setOnCheckedChangeListener { _, isChecked ->
            autoSimulateCpu = isChecked
            Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }

        switchAutoAdvanceRound.setOnCheckedChangeListener { _, isChecked ->
            autoAdvanceRound = isChecked
            Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }

        cardPlayers.setOnClickListener {
            val intent = Intent(this, ConfigPlayersActivity::class.java)
            startActivity(intent)
        }
    }
}
