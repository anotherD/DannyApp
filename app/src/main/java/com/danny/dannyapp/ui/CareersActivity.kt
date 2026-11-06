package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Career
import com.danny.dannyapp.data.CareerRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class CareersActivity : BaseActivity() {

    private lateinit var careerRepository: CareerRepository
    private lateinit var careerAdapter: CareerAdapter
    private lateinit var rvCareers: RecyclerView
    private lateinit var tvEmptyCareers: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_careers)
        setupToolbar(getString(R.string.careers), showBack = true, rootViewId = R.id.careersMain)

        careerRepository = CareerRepository(this)

        rvCareers = findViewById(R.id.rvCareers)
        tvEmptyCareers = findViewById(R.id.tvEmptyCareers)
        val btnCreateNewCareer: Button = findViewById(R.id.btnCreateNewCareer)

        rvCareers.layoutManager = LinearLayoutManager(this)
        careerAdapter = CareerAdapter(emptyList()) { career ->
            val intent = Intent(this, CareerDetailActivity::class.java).apply {
                putExtra(CareerDetailActivity.EXTRA_CAREER_ID, career.id)
                putExtra(CareerDetailActivity.EXTRA_CAREER_NAME, career.name)
                putExtra(CareerDetailActivity.EXTRA_CAREER_LEVEL, career.level)
            }
            startActivity(intent)
        }
        rvCareers.adapter = careerAdapter

        btnCreateNewCareer.setOnClickListener {
            showNewCareerDialog()
        }

        loadCareers()
    }

    override fun onResume() {
        super.onResume()
        loadCareers()
    }

    private fun loadCareers() {
        val careers = careerRepository.getCareers()
        careerAdapter.updateData(careers)
        if (careers.isEmpty()) {
            rvCareers.visibility = RecyclerView.GONE
            tvEmptyCareers.visibility = TextView.VISIBLE
        } else {
            rvCareers.visibility = RecyclerView.VISIBLE
            tvEmptyCareers.visibility = TextView.GONE
        }
    }

    private fun showNewCareerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_career, null)
        val etCareerName = dialogView.findViewById<TextInputEditText>(R.id.etCareerName)
        val numberPickerLevel = dialogView.findViewById<NumberPicker>(R.id.numberPickerLevel).apply {
            minValue = 1
            maxValue = 20
            displayedValues = Array(20) { i -> getString(R.string.career_level_format, i + 1) }
            value = 1
            wrapSelectorWheel = false
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.careers_create_positive_button)) { _, _ ->
                val careerName = etCareerName.text.toString().trim()
                if (careerName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.careers_create_error_empty_name), Toast.LENGTH_SHORT).show()
                } else {
                    val level = numberPickerLevel.value
                    val newCareer = Career(
                        id = UUID.randomUUID().toString(),
                        name = careerName,
                        level = level
                    )
                    careerRepository.saveCareer(newCareer)
                    Toast.makeText(this, getString(R.string.careers_career_created, careerName, level), Toast.LENGTH_SHORT).show()
                    loadCareers()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
