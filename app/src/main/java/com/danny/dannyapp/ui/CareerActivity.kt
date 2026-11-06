package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import com.danny.dannyapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class CareerActivity : BaseActivity() {

    private lateinit var tvActiveCareer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career)
        setupToolbar(getString(R.string.career), showBack = true, rootViewId = R.id.careerMain)

        tvActiveCareer = findViewById(R.id.tvActiveCareer)
        val btnStartNewCareer: Button = findViewById(R.id.btnStartNewCareer)

        btnStartNewCareer.setOnClickListener {
            showNewCareerDialog()
        }
    }

    private fun showNewCareerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_career, null)
        val etCareerName = dialogView.findViewById<TextInputEditText>(R.id.etCareerName)
        val numberPickerLevel = dialogView.findViewById<NumberPicker>(R.id.numberPickerLevel).apply {
            minValue = 1
            maxValue = 20
            displayedValues = Array(20) { i -> getString(R.string.career_create_level_format_item, i + 1) }
            value = 1
            wrapSelectorWheel = false
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.career_create_positive_button)) { _, _ ->
                val careerName = etCareerName.text.toString().trim()
                if (careerName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.career_create_error_empty_name), Toast.LENGTH_SHORT).show()
                } else {
                    val level = numberPickerLevel.value
                    Toast.makeText(this, getString(R.string.career_started_msg, careerName, level), Toast.LENGTH_SHORT).show()
                    tvActiveCareer.text = getString(R.string.career_active_format, careerName, level)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
