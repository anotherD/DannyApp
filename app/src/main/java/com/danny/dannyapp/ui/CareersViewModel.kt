package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.data.db.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CareersViewModel(application: Application) : AndroidViewModel(application) {

    private val careerRepository = CareerRepository(application)
    private val userProfileRepository = UserProfileRepository(application)

    val careersState: StateFlow<List<CareerWithProfile>> = careerRepository.careersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getUserProfiles(): List<UserProfile> {
        return userProfileRepository.getUserProfiles()
    }

    fun saveCareer(career: Career, onSuccess: () -> Unit) {
        viewModelScope.launch {
            careerRepository.saveCareer(career)
            onSuccess()
        }
    }

    fun deleteCareer(careerId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            careerRepository.deleteCareer(careerId)
            onSuccess()
        }
    }
}
