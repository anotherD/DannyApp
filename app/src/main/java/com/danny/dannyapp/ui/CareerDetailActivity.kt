package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import kotlinx.coroutines.launch

class CareerDetailActivity : BaseActivity() {

    private val viewModel: CareerDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        val careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)

        setupToolbar(
            titleText = getString(R.string.loading),
            subtitleText = getString(R.string.career),
            showBack = true,
            rootViewId = R.id.careerDetailMain,
        )

        val tvDetails: TextView = findViewById(R.id.tvCareerDetails)
        val btnTournaments: Button = findViewById(R.id.btnTournaments)
        val btnOrderOfMerit: Button = findViewById(R.id.btnOrderOfMerit)

        btnTournaments.setOnClickListener {
            val intent = Intent(this, TournamentCalendarActivity::class.java).apply {
                putExtra(TournamentCalendarActivity.EXTRA_CAREER_ID, careerId)
            }
            startActivity(intent)
        }

        btnOrderOfMerit.setOnClickListener {
            val intent = Intent(this, OrderOfMeritActivity::class.java).apply {
                putExtra(OrderOfMeritActivity.EXTRA_CAREER_ID, careerId)
            }
            startActivity(intent)
        }

        viewModel.loadCareer(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.careerState.collect { state ->
                    when (state) {
                        is CareerDetailState.Loading -> {
                            tvDetails.text = getString(R.string.loading)
                        }
                        is CareerDetailState.InvalidId -> {
                            tvDetails.text = getString(R.string.career_invalid_id)
                        }
                        is CareerDetailState.NotFound -> {
                            tvDetails.text = getString(R.string.career_not_found)
                        }
                        is CareerDetailState.Success -> {
                            val careerWithProfile = state.careerWithProfile
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
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
    }
}
