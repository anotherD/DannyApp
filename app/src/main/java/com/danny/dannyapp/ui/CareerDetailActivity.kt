package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.CareerRepository
import kotlinx.coroutines.launch

class CareerDetailActivity : BaseActivity() {

    private lateinit var careerRepository: CareerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        careerRepository = CareerRepository(this)
        val careerId = intent.getStringExtra(EXTRA_CAREER_ID)

        setupToolbar(
            titleText = getString(R.string.career),
            subtitleText = "Lade...",
            showBack = true,
            rootViewId = R.id.careerDetailMain
        )

        val tvDetails: TextView = findViewById(R.id.tvCareerDetails)

        if (careerId != null) {
            lifecycleScope.launch {
                val career = careerRepository.getCareerById(careerId)
                if (career != null) {
                    setupToolbar(
                        titleText = getString(R.string.career),
                        subtitleText = career.name,
                        showBack = true,
                        rootViewId = R.id.careerDetailMain
                    )
                    tvDetails.text = getString(R.string.career_current_format, career.name, career.level)
                } else {
                    tvDetails.text = "Karriere nicht gefunden."
                }
            }
        } else {
            tvDetails.text = "Ungültige Karriere-ID."
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
    }
}
