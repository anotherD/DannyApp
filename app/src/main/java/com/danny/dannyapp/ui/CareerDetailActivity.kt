package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.TextView
import com.danny.dannyapp.R

class CareerDetailActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        val careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: "Karriere"
        val careerLevel = intent.getIntExtra(EXTRA_CAREER_LEVEL, 1)

        setupToolbar(
            titleText = getString(R.string.career),
            subtitleText = careerName,
            showBack = true,
            rootViewId = R.id.careerDetailMain
        )

        val tvDetails: TextView = findViewById(R.id.tvCareerDetails)
        tvDetails.text = getString(R.string.career_current_format, careerName, careerLevel)
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
        const val EXTRA_CAREER_LEVEL = "extra_career_level"
    }
}
