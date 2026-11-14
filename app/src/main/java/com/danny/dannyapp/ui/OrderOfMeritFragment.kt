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
        cardStickyUserTop = rootView.findViewById(R.id.cardStickyUserTop) ?: activity?.findViewById(R.id.cardStickyUserTop)
        shadowStickyTop = rootView.findViewById(R.id.shadowStickyTop) ?: activity?.findViewById(R.id.shadowStickyTop)
        tvStickyRankTop = rootView.findViewById(R.id.tvStickyRankTop) ?: activity?.findViewById(R.id.tvStickyRankTop)
        tvStickyPlayerNameTop = rootView.findViewById(R.id.tvStickyPlayerNameTop) ?: activity?.findViewById(R.id.tvStickyPlayerNameTop)
        tvStickyPrizeMoneyTop = rootView.findViewById(R.id.tvStickyPrizeMoneyTop) ?: activity?.findViewById(R.id.tvStickyPrizeMoneyTop)

        cardStickyUserBottom = rootView.findViewById(R.id.cardStickyUserBottom) ?: activity?.findViewById(R.id.cardStickyUserBottom)
        shadowStickyBottom = rootView.findViewById(R.id.shadowStickyBottom) ?: activity?.findViewById(R.id.shadowStickyBottom)
        tvStickyRankBottom = rootView.findViewById(R.id.tvStickyRankBottom) ?: activity?.findViewById(R.id.tvStickyRankBottom)
        tvStickyPlayerNameBottom = rootView.findViewById(R.id.tvStickyPlayerNameBottom) ?: activity?.findViewById(R.id.tvStickyPlayerNameBottom)
        tvStickyPrizeMoneyBottom = rootView.findViewById(R.id.tvStickyPrizeMoneyBottom) ?: activity?.findViewById(R.id.tvStickyPrizeMoneyBottom)

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
        if (!isResumed) return
        if (isAdded && ::rvOom.isInitialized) {
            val lm = rvOom.layoutManager as? LinearLayoutManager
            if (lm != null) {
                updateStickyUserVisibility(lm)
            }
        }
    }

    private fun updateStickyUserVisibility(layoutManager: LinearLayoutManager) {
        val topCard = cardStickyUserTop
        val topShadow = shadowStickyTop
        val bottomCard = cardStickyUserBottom
        val bottomShadow = shadowStickyBottom

        val entries = adapter.currentList
        val userEntry = entries.find { it.isUser }
        if (userEntry == null || entries.isEmpty()) {
            topCard?.visibility = View.GONE
            topShadow?.visibility = View.GONE
            bottomCard?.visibility = View.GONE
            bottomShadow?.visibility = View.GONE
            return
        }

        val userIndex = entries.indexOf(userEntry)
        if (userIndex == -1) {
            topCard?.visibility = View.GONE
            topShadow?.visibility = View.GONE
            bottomCard?.visibility = View.GONE
            bottomShadow?.visibility = View.GONE
            return
        }

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            topCard?.visibility = View.GONE
            topShadow?.visibility = View.GONE
            bottomCard?.visibility = View.GONE
            bottomShadow?.visibility = View.GONE
            return
        }

        val rankText = "#${userEntry.rank}"
        val moneyText = "£%,.0f".format(userEntry.prizeMoney)

        when {
            userIndex < firstVisible -> {
                tvStickyRankTop?.text = rankText
                tvStickyPlayerNameTop?.let {
                    UiHelper.formatFlagAndText(it, userEntry.countryCode, userEntry.name)
                }
                tvStickyPrizeMoneyTop?.text = moneyText

                topCard?.visibility = View.VISIBLE
                topShadow?.visibility = View.VISIBLE
                topCard?.setOnClickListener {
                    layoutManager.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
                }
                bottomCard?.visibility = View.GONE
                bottomShadow?.visibility = View.GONE
            }
            userIndex > lastVisible -> {
                tvStickyRankBottom?.text = rankText
                tvStickyPlayerNameBottom?.let {
                    UiHelper.formatFlagAndText(it, userEntry.countryCode, userEntry.name)
                }
                tvStickyPrizeMoneyBottom?.text = moneyText

                bottomCard?.visibility = View.VISIBLE
                bottomShadow?.visibility = View.VISIBLE
                bottomCard?.setOnClickListener {
                    layoutManager.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
                }
                topCard?.visibility = View.GONE
                topShadow?.visibility = View.GONE
            }
            else -> {
                topCard?.visibility = View.GONE
                topShadow?.visibility = View.GONE
                bottomCard?.visibility = View.GONE
                bottomShadow?.visibility = View.GONE
            }
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
                        cardStickyUserTop?.visibility = View.GONE
                        shadowStickyTop?.visibility = View.GONE
                        cardStickyUserBottom?.visibility = View.GONE
                        shadowStickyBottom?.visibility = View.GONE
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
        cardStickyUserTop?.visibility = View.GONE
        shadowStickyTop?.visibility = View.GONE
        cardStickyUserBottom?.visibility = View.GONE
        shadowStickyBottom?.visibility = View.GONE
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
