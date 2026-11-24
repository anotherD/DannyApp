package com.danny.dannyapp.ui.career

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CareerDetailState {
    object Loading : CareerDetailState
    object InvalidId : CareerDetailState
    data class Success(val careerWithProfile: CareerWithProfile) : CareerDetailState
}

class CareerDetailViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val careerRepository = application.appContainer.careerRepository
    private val _careerState = MutableStateFlow<CareerDetailState>(CareerDetailState.Loading)
    val careerState: StateFlow<CareerDetailState> = _careerState.asStateFlow()

    private val _openRankingEvent = MutableSharedFlow<OrderOfMeritType>(extraBufferCapacity = 1)
    val openRankingEvent: SharedFlow<OrderOfMeritType> = _openRankingEvent.asSharedFlow()

    private val _openCalendarEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openCalendarEvent: SharedFlow<Unit> = _openCalendarEvent.asSharedFlow()

    fun requestOpenRanking(type: OrderOfMeritType) {
        _openRankingEvent.tryEmit(type)
    }

    fun requestOpenCalendar() {
        _openCalendarEvent.tryEmit(Unit)
    }

    init {
        val restoredCareerId = savedStateHandle.get<Long>("state_career_id") ?: -1L
        if (restoredCareerId > 0L) {
            loadCareer(restoredCareerId)
        }
    }

    fun loadCareer(careerId: Long) {
        savedStateHandle["state_career_id"] = careerId
        if (careerId <= 0L) {
            _careerState.value = CareerDetailState.InvalidId
            return
        }

        viewModelScope.launch {
            _careerState.value = CareerDetailState.Loading
            try {
                val careerWithProfile = careerRepository.getCareerById(careerId)
                _careerState.value = CareerDetailState.Success(careerWithProfile)
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Karriere laden", e)
            }
        }
    }
}
