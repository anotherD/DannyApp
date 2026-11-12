package com.danny.dannyapp.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.danny.dannyapp.domain.model.OrderOfMeritType

class OrderOfMeritPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val types = listOf(
        OrderOfMeritType.OOM,
        OrderOfMeritType.PT_OOM,
        OrderOfMeritType.PC_OOM,
        OrderOfMeritType.ET_OOM
    )

    override fun getItemCount(): Int = types.size

    override fun createFragment(position: Int): Fragment {
        return OrderOfMeritFragment.newInstance(types[position])
    }
}
