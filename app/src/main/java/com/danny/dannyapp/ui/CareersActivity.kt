package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.data.db.CareerRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

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
        careerAdapter = CareerAdapter(
            onItemClick = { career ->
                val intent = Intent(this, CareerDetailActivity::class.java).apply {
                    putExtra(CareerDetailActivity.EXTRA_CAREER_ID, career.id)
                }
                startActivity(intent)
            },
            onItemLongClick = { career ->
                showDeleteCareerDialog(career)
            }
        )
        rvCareers.adapter = careerAdapter

        btnCreateNewCareer.setOnClickListener {
            showNewCareerDialog()
        }

        observeCareers()
    }

    private fun observeCareers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                careerRepository.careersFlow.collect { careers ->
                    careerAdapter.submitList(careers)
                    if (careers.isEmpty()) {
                        rvCareers.visibility = RecyclerView.GONE
                        tvEmptyCareers.visibility = TextView.VISIBLE
                    } else {
                        rvCareers.visibility = RecyclerView.VISIBLE
                        tvEmptyCareers.visibility = TextView.GONE
                    }
                }
            }
        }
    }

    private fun showDeleteCareerDialog(career: Career) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.career_delete_title))
            .setMessage(getString(R.string.career_delete_message, career.name))
            .setPositiveButton(getString(R.string.career_delete_confirm)) { _, _ ->
                lifecycleScope.launch {
                    careerRepository.deleteCareer(career.id)
                    Toast.makeText(this@CareersActivity, getString(R.string.career_deleted_msg, career.name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.careers_create_positive_button), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                val careerName = etCareerName.text.toString().trim()
                if (careerName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.careers_create_error_empty_name), Toast.LENGTH_SHORT).show()
                } else {
                    val level = numberPickerLevel.value
                    val newCareer = Career(
                        name = careerName,
                        level = level
                    )
                    lifecycleScope.launch {
                        careerRepository.saveCareer(newCareer)
                        Toast.makeText(this@CareersActivity, getString(R.string.careers_career_created, careerName, level), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }
}
