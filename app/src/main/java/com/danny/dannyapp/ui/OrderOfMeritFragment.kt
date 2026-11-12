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
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class OrderOfMeritFragment : Fragment(R.layout.fragment_order_of_merit) {

    private lateinit var viewModel: OrderOfMeritViewModel
    private lateinit var adapter: OrderOfMeritAdapter
    private lateinit var rvOom: RecyclerView
    private lateinit var tvEmptyOom: TextView

    private lateinit var cardStickyUser: MaterialCardView
    private lateinit var tvStickyRank: TextView
    private lateinit var tvStickyPlayerName: TextView
    private lateinit var tvStickyPrizeMoney: TextView

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

        cardStickyUser = requireActivity().findViewById(R.id.cardStickyUser)
        tvStickyRank = requireActivity().findViewById(R.id.tvStickyRank)
        tvStickyPlayerName = requireActivity().findViewById(R.id.tvStickyPlayerName)
        tvStickyPrizeMoney = requireActivity().findViewById(R.id.tvStickyPrizeMoney)

        val layoutManager = LinearLayoutManager(requireContext())
        rvOom.layoutManager = layoutManager
        adapter = OrderOfMeritAdapter()
        rvOom.adapter = adapter

        rvOom.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateStickyUserVisibility(layoutManager)
            }
        })

        observeData()
    }

    private fun updateStickyUserVisibility(layoutManager: LinearLayoutManager) {
        val entries = adapter.currentList
        val userEntry = entries.find { it.isUser }
        if (userEntry == null || entries.isEmpty()) {
            cardStickyUser.visibility = View.GONE
            return
        }

        val userIndex = entries.indexOf(userEntry)
        if (userIndex == -1) {
            cardStickyUser.visibility = View.GONE
            return
        }

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (userIndex < firstVisible || userIndex > lastVisible) {
            tvStickyRank.text = "#${userEntry.rank}"
            val flagEmoji = Country.getFlagEmoji(userEntry.countryCode)
            tvStickyPlayerName.text = "$flagEmoji ${userEntry.name}"
            tvStickyPrizeMoney.text = "£%,.0f".format(userEntry.prizeMoney)

            cardStickyUser.visibility = View.VISIBLE
            cardStickyUser.setOnClickListener {
                val lm = rvOom.layoutManager as? LinearLayoutManager
                lm?.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
            }
        } else {
            cardStickyUser.visibility = View.GONE
        }
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
                        cardStickyUser.visibility = View.GONE
                    } else {
                        tvEmptyOom.visibility = TextView.GONE
                        rvOom.visibility = RecyclerView.VISIBLE
                        updateStickyUserVisibility(rvOom.layoutManager as LinearLayoutManager)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cardStickyUser.visibility = View.GONE
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
