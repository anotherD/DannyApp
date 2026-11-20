package com.danny.dannyapp.ui.career

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.data.db.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

sealed interface SaveCareerResult {
    data class Success(val careerName: String, val level: Int) : SaveCareerResult
    data class Error(val message: String) : SaveCareerResult
}

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

    suspend fun saveCareer(career: Career): SaveCareerResult {
        return try {
            careerRepository.saveCareer(career)
            SaveCareerResult.Success(career.name, career.level)
        } catch (e: Exception) {
            SaveCareerResult.Error(e.localizedMessage ?: getApplication<Application>().getString(R.string.error_save_career))
        }
    }

    /**
     * Updates the name of an existing career after validation.
     */
    suspend fun updateCareerName(career: Career, newName: String): SaveCareerResult {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) {
            return SaveCareerResult.Error(getApplication<Application>().getString(R.string.careers_create_error_empty_name))
        }
        return try {
            careerRepository.updateCareer(career.copy(name = trimmedName))
            SaveCareerResult.Success(trimmedName, career.level)
        } catch (exception: Exception) {
            SaveCareerResult.Error(exception.localizedMessage ?: getApplication<Application>().getString(R.string.error_save_career))
        }
    }

    suspend fun deleteCareer(careerId: Long) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }
        careerRepository.deleteCareer(careerId)
    }
}
