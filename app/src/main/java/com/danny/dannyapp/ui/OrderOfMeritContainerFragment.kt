package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OrderOfMeritContainerFragment : Fragment(R.layout.fragment_order_of_merit_container) {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPagerOomContainer)
        tabLayout = view.findViewById(R.id.tabLayoutOomContainer)

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

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Find fragment at position and update sticky card
                val fragment = childFragmentManager.findFragmentByTag("f$position") as? OrderOfMeritFragment
                fragment?.updateStickyCard()
            }
        })
    }

    companion object {
        fun newInstance(): OrderOfMeritContainerFragment {
            return OrderOfMeritContainerFragment()
        }
    }
}
