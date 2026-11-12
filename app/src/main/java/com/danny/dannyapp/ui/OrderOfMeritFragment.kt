package com.danny.dannyapp.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.model.OrderOfMeritType
import kotlinx.coroutines.launch

class OrderOfMeritFragment : Fragment(R.layout.fragment_order_of_merit) {

    private lateinit var viewModel: OrderOfMeritViewModel
    private lateinit var adapter: OrderOfMeritAdapter
    private lateinit var rvOom: RecyclerView
    private lateinit var tvEmptyOom: TextView

    private var oomType: OrderOfMeritType = OrderOfMeritType.OOM

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[OrderOfMeritViewModel::class.java]

        oomType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_TYPE, OrderOfMeritType::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_TYPE) as? OrderOfMeritType
        } ?: OrderOfMeritType.OOM

        rvOom = view.findViewById(R.id.rvOrderOfMerit)
        tvEmptyOom = view.findViewById(R.id.tvEmptyOom)

        rvOom.layoutManager = LinearLayoutManager(requireContext())
        adapter = OrderOfMeritAdapter()
        rvOom.adapter = adapter

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val flow = when (oomType) {
                    OrderOfMeritType.OOM -> viewModel.oomEntries
                    OrderOfMeritType.PT_OOM -> viewModel.ptOomEntries
                    OrderOfMeritType.PC_OOM -> viewModel.pcOomEntries
                    OrderOfMeritType.ET_OOM -> viewModel.etOomEntries
                }

                flow.collect { entries ->
                    adapter.submitList(entries)
                    if (entries.isEmpty()) {
                        tvEmptyOom.visibility = TextView.VISIBLE
                        rvOom.visibility = RecyclerView.GONE
                    } else {
                        tvEmptyOom.visibility = TextView.GONE
                        rvOom.visibility = RecyclerView.VISIBLE
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_TYPE = "arg_type"

        fun newInstance(type: OrderOfMeritType): OrderOfMeritFragment {
            return OrderOfMeritFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_TYPE, type)
                }
            }
        }
    }
}
