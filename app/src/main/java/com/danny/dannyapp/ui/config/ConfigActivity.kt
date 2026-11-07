package com.danny.dannyapp.ui.config

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.BaseActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class ConfigActivity : BaseActivity() {

    private lateinit var switchConfig1: MaterialSwitch
    private lateinit var switchConfig2: MaterialSwitch
    private lateinit var cardPlayers: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        setupToolbar(getString(R.string.config_title), showBack = true, rootViewId = R.id.configurationMain)

        switchConfig1 = findViewById(R.id.switchConfig1)
        switchConfig2 = findViewById(R.id.switchConfig2)
        cardPlayers = findViewById(R.id.cardPlayers)

        switchConfig1.isChecked = config1Checked
        switchConfig2.isChecked = config2Checked

        switchConfig1.setOnCheckedChangeListener { _, isChecked ->
            config1Checked = isChecked
            Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }

        switchConfig2.setOnCheckedChangeListener { _, isChecked ->
            config2Checked = isChecked
            Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
        }

        cardPlayers.setOnClickListener {
            val intent = Intent(this, ConfigPlayersActivity::class.java)
            startActivity(intent)
        }
    }
}
