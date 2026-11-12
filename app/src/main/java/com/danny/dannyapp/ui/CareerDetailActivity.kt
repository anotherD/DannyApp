package com.danny.dannyapp.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class CareerDetailActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val calendarViewModel: TournamentCalendarViewModel by viewModels()
    private val oomViewModel: OrderOfMeritViewModel by viewModels()

    private lateinit var tabLayoutCareer: TabLayout
    private lateinit var viewPagerCareer: ViewPager2

    private var careerId: Long = -1L
    private var careerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)

        setupToolbar(
            titleText = getString(R.string.loading),
            subtitleText = getString(R.string.career),
            showBack = true,
            rootViewId = R.id.careerDetailMain,
        )

        tabLayoutCareer = findViewById(R.id.tabLayoutCareer)
        viewPagerCareer = findViewById(R.id.viewPagerCareer)

        viewPagerCareer.adapter = CareerDetailPagerAdapter(this, careerId, careerName)

        val titles = listOf(
            getString(R.string.tab_overview),
            getString(R.string.tab_calendar),
            getString(R.string.tab_oom)
        )

        TabLayoutMediator(tabLayoutCareer, viewPagerCareer) { tab, position ->
            tab.text = titles.getOrElse(position) { "" }
        }.attach()

        careerViewModel.loadCareer(careerId)
        calendarViewModel.loadTournaments(careerId)
        oomViewModel.loadOrderOfMerit(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                careerViewModel.careerState.collect { state ->
                    when (state) {
                        is CareerDetailState.Success -> {
                            val career = state.careerWithProfile.career
                            val newName = career.name
                            if (newName != careerName) {
                                careerName = newName
                                viewPagerCareer.adapter = CareerDetailPagerAdapter(this@CareerDetailActivity, careerId, careerName)
                            }
                            setupToolbar(
                                titleText = career.name,
                                subtitleText = getString(R.string.breadcrumb_format, careerName),
                                showBack = true,
                                rootViewId = R.id.careerDetailMain,
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
    }
}
