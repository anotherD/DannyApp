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

    private var cardStickyUser: MaterialCardView? = null
    private var tvStickyRank: TextView? = null
    private var tvStickyPlayerName: TextView? = null
    private var tvStickyPrizeMoney: TextView? = null

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

        val rootView = view.rootView
        cardStickyUser = rootView.findViewById(R.id.cardStickyUser) ?: activity?.findViewById(R.id.cardStickyUser)
        tvStickyRank = rootView.findViewById(R.id.tvStickyRank) ?: activity?.findViewById(R.id.tvStickyRank)
        tvStickyPlayerName = rootView.findViewById(R.id.tvStickyPlayerName) ?: activity?.findViewById(R.id.tvStickyPlayerName)
        tvStickyPrizeMoney = rootView.findViewById(R.id.tvStickyPrizeMoney) ?: activity?.findViewById(R.id.tvStickyPrizeMoney)

        val layoutManager = LinearLayoutManager(requireContext())
        rvOom.layoutManager = layoutManager
        adapter = OrderOfMeritAdapter()
        rvOom.adapter = adapter

        rvOom.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateStickyCard()
            }
        })

        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateStickyCard()
    }

    fun updateStickyCard() {
        if (isAdded && ::rvOom.isInitialized) {
            val lm = rvOom.layoutManager as? LinearLayoutManager
            if (lm != null) {
                updateStickyUserVisibility(lm)
            }
        }
    }

    private fun updateStickyUserVisibility(layoutManager: LinearLayoutManager) {
        val card = cardStickyUser ?: return
        val rankTv = tvStickyRank ?: return
        val nameTv = tvStickyPlayerName ?: return
        val moneyTv = tvStickyPrizeMoney ?: return

        val entries = adapter.currentList
        val userEntry = entries.find { it.isUser }
        if (userEntry == null || entries.isEmpty()) {
            card.visibility = View.GONE
            return
        }

        val userIndex = entries.indexOf(userEntry)
        if (userIndex == -1) {
            card.visibility = View.GONE
            return
        }

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (userIndex < firstVisible || userIndex > lastVisible) {
            rankTv.text = "#${userEntry.rank}"
            val flagEmoji = Country.getFlagEmoji(userEntry.countryCode)
            nameTv.text = "$flagEmoji ${userEntry.name}"
            moneyTv.text = "£%,.0f".format(userEntry.prizeMoney)

            card.visibility = View.VISIBLE
            card.setOnClickListener {
                layoutManager.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
            }
        } else {
            card.visibility = View.GONE
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
                        cardStickyUser?.visibility = View.GONE
                    } else {
                        tvEmptyOom.visibility = TextView.GONE
                        rvOom.visibility = RecyclerView.VISIBLE
                        updateStickyCard()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cardStickyUser?.visibility = View.GONE
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
