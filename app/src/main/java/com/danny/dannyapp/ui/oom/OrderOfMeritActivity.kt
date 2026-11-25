package com.danny.dannyapp.ui.oom

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.career.CareerDetailActivity
import com.danny.dannyapp.ui.career.CareerDetailState
import com.danny.dannyapp.ui.career.CareerDetailViewModel
import com.danny.dannyapp.ui.navigation.AppScreen
import kotlinx.coroutines.launch

class OrderOfMeritActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val oomViewModel: OrderOfMeritViewModel by viewModels()

    private var careerId: Long = -1L
    private var careerName: String = ""

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

        if (savedInstanceState == null) {
            oomTypeName?.let { typeName ->
                OrderOfMeritType.entries.find { it.name == typeName }?.let { initialType ->
                    oomViewModel.selectOomType(initialType)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerOomActivity, OrderOfMeritContainerFragment.newInstance())
                .commit()
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
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
        const val EXTRA_OOM_TYPE = "extra_oom_type"
    }
}
