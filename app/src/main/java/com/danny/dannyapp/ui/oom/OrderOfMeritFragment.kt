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

        oomType = arguments?.getSerializable(ARG_TYPE, OrderOfMeritType::class.java)
            ?: OrderOfMeritType.OOM

        rvOom = view.findViewById(R.id.rvOrderOfMerit)
        tvEmptyOom = view.findViewById(R.id.tvEmptyOom)

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
        if (!isResumed || viewModel.selectedOomType.value != oomType) return
        if (isAdded && ::rvOom.isInitialized) {
            val lm = rvOom.layoutManager as? LinearLayoutManager
            if (lm != null) {
                updateStickyUserVisibility(lm)
            }
        }
    }

    private fun updateStickyUserVisibility(layoutManager: LinearLayoutManager) {
        val entries = adapter.currentList
        val userEntry = entries.find { it.isUser }
        if (userEntry == null || entries.isEmpty()) {
            viewModel.updateStickyUserState(StickyUserState.Hidden)
            return
        }

        val userIndex = entries.indexOf(userEntry)
        if (userIndex == -1) {
            viewModel.updateStickyUserState(StickyUserState.Hidden)
            return
        }

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            viewModel.updateStickyUserState(StickyUserState.Hidden)
            return
        }

        when {
            userIndex < firstVisible -> {
                viewModel.updateStickyUserState(StickyUserState.VisibleTop(userEntry, userIndex))
            }
            userIndex > lastVisible -> {
                viewModel.updateStickyUserState(StickyUserState.VisibleBottom(userEntry, userIndex))
            }
            else -> {
                viewModel.updateStickyUserState(StickyUserState.Hidden)
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
                    } else {
                        tvEmptyOom.visibility = TextView.GONE
                        rvOom.visibility = RecyclerView.VISIBLE
                        updateStickyCard()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scrollToPositionEvent.collect { userIndex ->
                    if (viewModel.selectedOomType.value == oomType) {
                        val lm = rvOom.layoutManager as? LinearLayoutManager
                        lm?.scrollToPositionWithOffset(userIndex, rvOom.height / 2 - 100)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.updateStickyUserState(StickyUserState.Hidden)
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
