package com.danny.dannyapp.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.google.android.material.chip.ChipGroup

class OrderOfMeritContainerFragment : Fragment(R.layout.fragment_order_of_merit_container) {

    private lateinit var chipGroupOomTypes: ChipGroup

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chipGroupOomTypes = view.findViewById(R.id.chipGroupOomTypes)

        if (savedInstanceState == null) {
            showOomFragment(OrderOfMeritType.OOM)
        }

        chipGroupOomTypes.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val type = when (checkedId) {
                R.id.chipOomMain -> OrderOfMeritType.OOM
                R.id.chipOomProTour -> OrderOfMeritType.PT_OOM
                R.id.chipOomPlayersChamp -> OrderOfMeritType.PC_OOM
                R.id.chipOomEuropeanTour -> OrderOfMeritType.ET_OOM
                else -> OrderOfMeritType.OOM
            }
            showOomFragment(type)
        }
    }

    private fun showOomFragment(type: OrderOfMeritType) {
        childFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerOom, OrderOfMeritFragment.newInstance(type))
            .commit()
    }

    companion object {
        fun newInstance(): OrderOfMeritContainerFragment {
            return OrderOfMeritContainerFragment()
        }
    }
}
