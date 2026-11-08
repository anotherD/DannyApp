package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.CareerWithProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CareerDetailState {
    object Loading : CareerDetailState
    object InvalidId : CareerDetailState
    object NotFound : CareerDetailState
    data class Success(val careerWithProfile: CareerWithProfile) : CareerDetailState
}

class CareerDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val careerRepository = CareerRepository(application)
    private val _careerState = MutableStateFlow<CareerDetailState>(CareerDetailState.Loading)
    val careerState: StateFlow<CareerDetailState> = _careerState.asStateFlow()

    fun loadCareer(careerId: Long) {
        if (careerId <= 0L) {
            _careerState.value = CareerDetailState.InvalidId
            return
        }

        viewModelScope.launch {
            _careerState.value = CareerDetailState.Loading
            val careerWithProfile = careerRepository.getCareerById(careerId)
            if (careerWithProfile != null) {
                _careerState.value = CareerDetailState.Success(careerWithProfile)
            } else {
                _careerState.value = CareerDetailState.NotFound
            }
        }
    }
}
