package com.danny.dannyapp.ui.oom

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
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.ui.UIHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class OrderOfMeritFragment : Fragment(R.layout.fragment_order_of_merit) {

    private lateinit var viewModel: OrderOfMeritViewModel
    private lateinit var adapter: OrderOfMeritAdapter
    private lateinit var rvOom: RecyclerView
    private lateinit var tvEmptyOom: TextView

    private lateinit var cardStickyUserTop: MaterialCardView
    private lateinit var shadowStickyTop: View
    private lateinit var tvStickyRankTop: TextView
    private lateinit var tvStickyPlayerNameTop: TextView
    private lateinit var tvStickyPrizeMoneyTop: TextView

    private lateinit var cardStickyUserBottom: MaterialCardView
    private lateinit var shadowStickyBottom: View
    private lateinit var tvStickyRankBottom: TextView
    private lateinit var tvStickyPlayerNameBottom: TextView
    private lateinit var tvStickyPrizeMoneyBottom: TextView

    private var oomType: OrderOfMeritType = OrderOfMeritType.OOM

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[OrderOfMeritViewModel::class.java]

        oomType = arguments?.getSerializable(ARG_TYPE, OrderOfMeritType::class.java)
            ?: OrderOfMeritType.OOM

        rvOom = view.findViewById(R.id.rvOrderOfMerit)
        tvEmptyOom = view.findViewById(R.id.tvEmptyOom)

        cardStickyUserTop = view.findViewById(R.id.cardStickyUserTop)
        shadowStickyTop = view.findViewById(R.id.shadowStickyTop)
        tvStickyRankTop = view.findViewById(R.id.tvStickyRankTop)
        tvStickyPlayerNameTop = view.findViewById(R.id.tvStickyPlayerNameTop)
        tvStickyPrizeMoneyTop = view.findViewById(R.id.tvStickyPrizeMoneyTop)

        cardStickyUserBottom = view.findViewById(R.id.cardStickyUserBottom)
        shadowStickyBottom = view.findViewById(R.id.shadowStickyBottom)
        tvStickyRankBottom = view.findViewById(R.id.tvStickyRankBottom)
        tvStickyPlayerNameBottom = view.findViewById(R.id.tvStickyPlayerNameBottom)
        tvStickyPrizeMoneyBottom = view.findViewById(R.id.tvStickyPrizeMoneyBottom)

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

        rvOom.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            rvOom.post { updateStickyCard() }
        }

        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateStickyCard()
    }

    fun updateStickyCard() {
        if (!isAdded || !::rvOom.isInitialized) return
        val layoutManager = rvOom.layoutManager as? LinearLayoutManager ?: return

        val entries = adapter.currentList
        val userEntry = entries.find { it.isUser }
        if (userEntry == null || entries.isEmpty()) {
            hideAllStickyViews()
            return
        }

        val userIndex = entries.indexOf(userEntry)
        if (userIndex == -1) {
            hideAllStickyViews()
            return
        }

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            hideAllStickyViews()
            return
        }

        when {
            userIndex < firstVisible -> {
                val rankText = getString(R.string.rank_format, userEntry.rank)
                val moneyText = getString(R.string.prize_money_format, userEntry.prizeMoney)

                tvStickyRankTop.text = rankText
                UIHelper.formatFlagAndText(tvStickyPlayerNameTop, userEntry.countryCode, userEntry.name)
                tvStickyPrizeMoneyTop.text = moneyText

                cardStickyUserTop.visibility = View.VISIBLE
                shadowStickyTop.visibility = View.VISIBLE
                cardStickyUserTop.setOnClickListener {
                    layoutManager.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
                }

                cardStickyUserBottom.visibility = View.INVISIBLE
                shadowStickyBottom.visibility = View.INVISIBLE
            }
            userIndex > lastVisible -> {
                val rankText = getString(R.string.rank_format, userEntry.rank)
                val moneyText = getString(R.string.prize_money_format, userEntry.prizeMoney)

                tvStickyRankBottom.text = rankText
                UIHelper.formatFlagAndText(tvStickyPlayerNameBottom, userEntry.countryCode, userEntry.name)
                tvStickyPrizeMoneyBottom.text = moneyText

                cardStickyUserBottom.visibility = View.VISIBLE
                shadowStickyBottom.visibility = View.VISIBLE
                cardStickyUserBottom.setOnClickListener {
                    layoutManager.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
                }

                cardStickyUserTop.visibility = View.INVISIBLE
                shadowStickyTop.visibility = View.INVISIBLE
            }
            else -> {
                hideAllStickyViews()
            }
        }
    }

    private fun hideAllStickyViews() {
        cardStickyUserTop.visibility = View.INVISIBLE
        shadowStickyTop.visibility = View.INVISIBLE
        cardStickyUserBottom.visibility = View.INVISIBLE
        shadowStickyBottom.visibility = View.INVISIBLE
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
                    if (entries.isEmpty()) {
                        tvEmptyOom.visibility = TextView.VISIBLE
                        rvOom.visibility = RecyclerView.GONE
                        hideAllStickyViews()
                    } else {
                        tvEmptyOom.visibility = TextView.GONE
                        rvOom.visibility = RecyclerView.VISIBLE
                    }
                    adapter.submitList(entries) {
                        if (isAdded) {
                            rvOom.post { updateStickyCard() }
                        }
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
