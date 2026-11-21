package com.danny.dannyapp.ui.oom

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.ui.UIHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class OrderOfMeritContainerFragment : Fragment(R.layout.fragment_order_of_merit_container) {

    private lateinit var chipGroupOomTypes: ChipGroup
    private lateinit var viewModel: OrderOfMeritViewModel

    private var cardStickyUserTop: MaterialCardView? = null
    private var shadowStickyTop: View? = null
    private var tvStickyRankTop: TextView? = null
    private var tvStickyPlayerNameTop: TextView? = null
    private var tvStickyPrizeMoneyTop: TextView? = null

    private var cardStickyUserBottom: MaterialCardView? = null
    private var shadowStickyBottom: View? = null
    private var tvStickyRankBottom: TextView? = null
    private var tvStickyPlayerNameBottom: TextView? = null
    private var tvStickyPrizeMoneyBottom: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[OrderOfMeritViewModel::class.java]
        chipGroupOomTypes = view.findViewById(R.id.chipGroupOomTypes)

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

        observeStickyUserState()
    }

    private fun observeStickyUserState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stickyUserState.collect { state ->
                    when (state) {
                        is StickyUserState.Hidden -> {
                            cardStickyUserTop?.visibility = View.GONE
                            shadowStickyTop?.visibility = View.GONE
                            cardStickyUserBottom?.visibility = View.GONE
                            shadowStickyBottom?.visibility = View.GONE
                        }
                        is StickyUserState.VisibleTop -> {
                            val rankText = getString(R.string.rank_format, state.entry.rank)
                            val moneyText = getString(R.string.prize_money_format, state.entry.prizeMoney)

                            tvStickyRankTop?.text = rankText
                            tvStickyPlayerNameTop?.let {
                                UIHelper.formatFlagAndText(it, state.entry.countryCode, state.entry.name)
                            }
                            tvStickyPrizeMoneyTop?.text = moneyText

                            cardStickyUserTop?.visibility = View.VISIBLE
                            shadowStickyTop?.visibility = View.VISIBLE
                            cardStickyUserTop?.setOnClickListener {
                                viewModel.requestScrollToUser(state.userIndex)
                            }

                            cardStickyUserBottom?.visibility = View.GONE
                            shadowStickyBottom?.visibility = View.GONE
                        }
                        is StickyUserState.VisibleBottom -> {
                            val rankText = getString(R.string.rank_format, state.entry.rank)
                            val moneyText = getString(R.string.prize_money_format, state.entry.prizeMoney)

                            tvStickyRankBottom?.text = rankText
                            tvStickyPlayerNameBottom?.let {
                                UIHelper.formatFlagAndText(it, state.entry.countryCode, state.entry.name)
                            }
                            tvStickyPrizeMoneyBottom?.text = moneyText

                            cardStickyUserBottom?.visibility = View.VISIBLE
                            shadowStickyBottom?.visibility = View.VISIBLE
                            cardStickyUserBottom?.setOnClickListener {
                                viewModel.requestScrollToUser(state.userIndex)
                            }

                            cardStickyUserTop?.visibility = View.GONE
                            shadowStickyTop?.visibility = View.GONE
                        }
                    }
                }
            }
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
