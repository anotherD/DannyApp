package com.danny.dannyapp.ui.oom

import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class OrderOfMeritContainerFragment : Fragment(R.layout.fragment_order_of_merit_container) {

    private lateinit var chipGroupOomTypes: ChipGroup
    private lateinit var scrollOomChips: HorizontalScrollView
    private lateinit var viewPagerOom: ViewPager2
    private lateinit var viewModel: OrderOfMeritViewModel

    private var isInitialSelection = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[OrderOfMeritViewModel::class.java]
        chipGroupOomTypes = view.findViewById(R.id.chipGroupOomTypes)
        scrollOomChips = view.findViewById(R.id.scrollOomChips)
        viewPagerOom = view.findViewById(R.id.viewPagerOom)

        viewPagerOom.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = OrderOfMeritType.entries.size

            override fun createFragment(position: Int): Fragment {
                val type = OrderOfMeritType.entries[position]
                return OrderOfMeritFragment.newInstance(type)
            }
        }

        viewPagerOom.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val type = OrderOfMeritType.entries[position]
                if (viewModel.selectedOomType.value != type) {
                    viewModel.selectOomType(type)
                }
            }
        })

        chipGroupOomTypes.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val type = when (checkedId) {
                R.id.chipOomMain -> OrderOfMeritType.OOM
                R.id.chipOomProTour -> OrderOfMeritType.PT_OOM
                R.id.chipOomPlayersChamp -> OrderOfMeritType.PC_OOM
                R.id.chipOomEuropeanTour -> OrderOfMeritType.ET_OOM
                else -> OrderOfMeritType.OOM
            }
            if (viewModel.selectedOomType.value != type) {
                viewModel.selectOomType(type)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedOomType.collect { type ->
                    val chipId = when (type) {
                        OrderOfMeritType.OOM -> R.id.chipOomMain
                        OrderOfMeritType.PT_OOM -> R.id.chipOomProTour
                        OrderOfMeritType.PC_OOM -> R.id.chipOomPlayersChamp
                        OrderOfMeritType.ET_OOM -> R.id.chipOomEuropeanTour
                    }
                    if (chipGroupOomTypes.checkedChipId != chipId) {
                        chipGroupOomTypes.check(chipId)
                    }
                    val index = OrderOfMeritType.entries.indexOf(type)
                    if (viewPagerOom.currentItem != index) {
                        viewPagerOom.setCurrentItem(index, !isInitialSelection)
                    }
                    isInitialSelection = false

                    val selectedChip = chipGroupOomTypes.findViewById<View>(chipId)
                    selectedChip?.post {
                        val scrollX = selectedChip.left - (scrollOomChips.width - selectedChip.width) / 2
                        scrollOomChips.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(): OrderOfMeritContainerFragment {
            return OrderOfMeritContainerFragment()
        }
    }
}
