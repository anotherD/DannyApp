package com.danny.dannyapp.ui.oom

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.career.CareerDetailActivity
import com.danny.dannyapp.ui.career.CareerDetailState
import com.danny.dannyapp.ui.career.CareerDetailViewModel
import com.danny.dannyapp.ui.navigation.AppScreen
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class OrderOfMeritActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val oomViewModel: OrderOfMeritViewModel by viewModels()

    private lateinit var chipGroupOomTypes: ChipGroup
    private lateinit var scrollOomChips: HorizontalScrollView
    private lateinit var viewPagerOom: ViewPager2

    private var careerId: Long = -1L
    private var careerName: String = ""
    private var isInitialSelection = true

    override fun buildUpNavigationExtras(parentScreen: AppScreen, intent: Intent) {
        if (parentScreen == AppScreen.CAREER_OVERVIEW) {
            intent.putExtra(CareerDetailActivity.EXTRA_CAREER_ID, careerId)
            intent.putExtra(CareerDetailActivity.EXTRA_CAREER_NAME, careerName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_of_merit)

        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: ""
        val oomTypeName = intent.getStringExtra(EXTRA_OOM_TYPE)

        setupCareerNavigationToolbar(AppScreen.ORDER_OF_MERIT, careerName, rootViewId = R.id.orderOfMeritMain)

        chipGroupOomTypes = findViewById(R.id.chipGroupOomTypes)
        scrollOomChips = findViewById(R.id.scrollOomChips)
        viewPagerOom = findViewById(R.id.viewPagerOom)

        updateChipStyles(chipGroupOomTypes.checkedChipId)

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
                if (oomViewModel.selectedOomType.value != type) {
                    oomViewModel.selectOomType(type)
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
            if (oomViewModel.selectedOomType.value != type) {
                oomViewModel.selectOomType(type)
            }
        }

        if (savedInstanceState == null) {
            oomTypeName?.let { typeName ->
                OrderOfMeritType.entries.find { it.name == typeName }?.let { initialType ->
                    oomViewModel.selectOomType(initialType)
                }
            }
        }

        careerViewModel.loadCareer(careerId)
        oomViewModel.loadOrderOfMerit(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                careerViewModel.careerState.collect { state ->
                    when (state) {
                        is CareerDetailState.Success -> {
                            val career = state.careerWithProfile.career
                            careerName = career.name
                            setupCareerNavigationToolbar(AppScreen.ORDER_OF_MERIT, careerName, career.season, rootViewId = R.id.orderOfMeritMain)
                        }
                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                oomViewModel.selectedOomType.collect { type ->
                    val chipId = when (type) {
                        OrderOfMeritType.OOM -> R.id.chipOomMain
                        OrderOfMeritType.PT_OOM -> R.id.chipOomProTour
                        OrderOfMeritType.PC_OOM -> R.id.chipOomPlayersChamp
                        OrderOfMeritType.ET_OOM -> R.id.chipOomEuropeanTour
                    }
                    if (chipGroupOomTypes.checkedChipId != chipId) {
                        chipGroupOomTypes.check(chipId)
                    }
                    updateChipStyles(chipId)
                    val index = OrderOfMeritType.entries.indexOf(type)
                    if (viewPagerOom.currentItem != index) {
                        viewPagerOom.setCurrentItem(index, !isInitialSelection)
                    }
                    isInitialSelection = false

                    val selectedChip = chipGroupOomTypes.findViewById<View>(chipId)
                    selectedChip?.let {
                        UIHelper.centerViewInHorizontalScroll(scrollOomChips, it)
                    }
                }
            }
        }
    }

    private fun updateChipStyles(selectedChipId: Int) {
        for (i in 0 until chipGroupOomTypes.childCount) {
            val chip = chipGroupOomTypes.getChildAt(i) as? Chip ?: continue
            UIHelper.styleSelectionChip(chip, chip.id == selectedChipId)
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
        const val EXTRA_OOM_TYPE = "extra_oom_type"
    }
}
