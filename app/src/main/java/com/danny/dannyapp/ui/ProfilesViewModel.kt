package com.danny.dannyapp.ui

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.data.db.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SaveProfileResult {
    data class Success(val profileName: String) : SaveProfileResult
    data class DuplicateName(val profileName: String) : SaveProfileResult
    data class Error(val message: String) : SaveProfileResult
}

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val userProfileRepository = UserProfileRepository(application)
    private val careerRepository = CareerRepository(application)

    val profilesState: StateFlow<List<UserProfile>> = userProfileRepository.userProfilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getCareerCountForProfile(profileId: Long): Int {
        return careerRepository.getCareerCountForProfile(profileId)
    }

    fun saveUserProfile(name: String, description: String, onResult: (SaveProfileResult) -> Unit) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val existing = userProfileRepository.getProfileByName(trimmedName)
            if (existing != null) {
                onResult(SaveProfileResult.DuplicateName(trimmedName))
                return@launch
            }

            val newProfile = UserProfile(
                name = trimmedName,
                description = description.trim()
            )

            try {
                userProfileRepository.saveUserProfile(newProfile)
                onResult(SaveProfileResult.Success(trimmedName))
            } catch (e: Exception) {
                val isDuplicate = e is SQLiteConstraintException ||
                    e.message?.contains("UNIQUE constraint", ignoreCase = true) == true

                if (isDuplicate) {
                    onResult(SaveProfileResult.DuplicateName(trimmedName))
                } else {
                    onResult(SaveProfileResult.Error(e.localizedMessage ?: "Unbekannter Fehler"))
                }
            }
        }
    }

    fun updateUserProfile(profile: UserProfile, newName: String, newDescription: String, onResult: (SaveProfileResult) -> Unit) {
        viewModelScope.launch {
            val trimmedName = newName.trim()
            if (trimmedName.isEmpty()) {
                onResult(SaveProfileResult.Error("Name darf nicht leer sein"))
                return@launch
            }

            if (!trimmedName.equals(profile.name, ignoreCase = true)) {
                val existing = userProfileRepository.getProfileByName(trimmedName)
                if (existing != null && existing.id != profile.id) {
                    onResult(SaveProfileResult.DuplicateName(trimmedName))
                    return@launch
                }
            }

            val updatedProfile = profile.copy(
                name = trimmedName,
                description = newDescription.trim()
            )

            try {
                userProfileRepository.updateUserProfile(updatedProfile)
                onResult(SaveProfileResult.Success(trimmedName))
            } catch (e: Exception) {
                val isDuplicate = e is SQLiteConstraintException ||
                    e.message?.contains("UNIQUE constraint", ignoreCase = true) == true

                if (isDuplicate) {
                    onResult(SaveProfileResult.DuplicateName(trimmedName))
                } else {
                    onResult(SaveProfileResult.Error(e.localizedMessage ?: "Unbekannter Fehler"))
                }
            }
        }
    }

    fun deleteUserProfile(profileId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            userProfileRepository.deleteUserProfile(profileId)
            onSuccess()
        }
    }
}
