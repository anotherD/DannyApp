package com.danny.dannyapp.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OrderOfMeritActivity : BaseActivity() {

    private val viewModel: OrderOfMeritViewModel by viewModels()
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_of_merit)

        val careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        val careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: "Karriere"

        setupToolbar(
            titleText = getString(R.string.tab_oom),
            subtitleText = getString(R.string.breadcrumb_format, careerName),
            showBack = true,
            rootViewId = R.id.orderOfMeritMain
        )

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        viewPager.adapter = OrderOfMeritPagerAdapter(this)

        val titles = listOf(
            getString(R.string.oom_tab_oom),
            getString(R.string.oom_tab_pt),
            getString(R.string.oom_tab_pc),
            getString(R.string.oom_tab_et)
        )

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = titles.getOrElse(position) { "OOM" }
        }.attach()

        if (careerId > 0L) {
            viewModel.loadOrderOfMerit(careerId)
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
    }
}
