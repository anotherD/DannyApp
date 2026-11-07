package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.Toast
import com.danny.dannyapp.R
import com.google.android.material.materialswitch.MaterialSwitch

class ConfigurationActivity : BaseActivity() {

    private lateinit var switchConfig1: MaterialSwitch
    private lateinit var switchConfig2: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        setupToolbar(getString(R.string.configuration), showBack = true, rootViewId = R.id.configurationMain)

        switchConfig1 = findViewById(R.id.switchConfig1)
        switchConfig2 = findViewById(R.id.switchConfig2)

        switchConfig1.isChecked = config1Checked
        switchConfig2.isChecked = config2Checked

        switchConfig1.setOnCheckedChangeListener { _, isChecked ->
            config1Checked = isChecked
            Toast.makeText(this, "Konfiguration gespeichert", Toast.LENGTH_SHORT).show()
        }

        switchConfig2.setOnCheckedChangeListener { _, isChecked ->
            config2Checked = isChecked
            Toast.makeText(this, "Konfiguration gespeichert", Toast.LENGTH_SHORT).show()
        }
    }
}
