package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.CareerRepository
import kotlinx.coroutines.launch

class CareerDetailActivity : BaseActivity() {

    private lateinit var careerRepository: CareerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        careerRepository = CareerRepository(this)
        val careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)

        setupToolbar(
            titleText = getString(R.string.loading),
            subtitleText = getString(R.string.career),
            showBack = true,
            rootViewId = R.id.careerDetailMain,
        )

        val tvDetails: TextView = findViewById(R.id.tvCareerDetails)

        if (careerId != -1L) {
            lifecycleScope.launch {
                val careerWithProfile = careerRepository.getCareerById(careerId)
                if (careerWithProfile != null) {
                    val career = careerWithProfile.career
                    setupToolbar(
                        titleText = career.name,
                        subtitleText = getString(R.string.career),
                        showBack = true,
                        rootViewId = R.id.careerDetailMain,
                    )
                    val profileText = if (!careerWithProfile.profileName.isNullOrBlank()) {
                        "\n" + getString(R.string.career_profile_format, careerWithProfile.profileName)
                    } else ""
                    tvDetails.text = getString(R.string.career_current_format, career.name, career.level) + profileText
                } else {
                    tvDetails.text = getString(R.string.career_not_found)
                }
            }
        } else {
            tvDetails.text = getString(R.string.career_invalid_id)
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
    }
}
