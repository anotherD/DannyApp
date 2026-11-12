package com.danny.dannyapp.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class CareerDetailPagerAdapter(
    fragmentActivity: androidx.fragment.app.FragmentActivity,
    private val careerId: Long,
    private val careerName: String
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CareerOverviewFragment.newInstance(careerId, careerName)
            1 -> TournamentCalendarFragment.newInstance(careerId, careerName)
            2 -> OrderOfMeritContainerFragment.newInstance()
            else -> CareerOverviewFragment.newInstance(careerId, careerName)
        }
    }
}
