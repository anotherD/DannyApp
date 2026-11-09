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

    suspend fun saveUserProfile(name: String, description: String): SaveProfileResult {
        val trimmedName = name.trim()
        val existing = userProfileRepository.getProfileByName(trimmedName)
        if (existing != null) {
            return SaveProfileResult.DuplicateName(trimmedName)
        }

        val newProfile = UserProfile(
            name = trimmedName,
            description = description.trim()
        )

        return try {
            userProfileRepository.saveUserProfile(newProfile)
            SaveProfileResult.Success(trimmedName)
        } catch (e: Exception) {
            val isDuplicate = e is SQLiteConstraintException ||
                e.message?.contains("UNIQUE constraint", ignoreCase = true) == true

            if (isDuplicate) {
                SaveProfileResult.DuplicateName(trimmedName)
            } else {
                SaveProfileResult.Error(e.localizedMessage ?: "Unbekannter Fehler")
            }
        }
    }

    suspend fun updateUserProfile(profile: UserProfile, newName: String, newDescription: String): SaveProfileResult {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) {
            return SaveProfileResult.Error("Name darf nicht leer sein")
        }

        if (!trimmedName.equals(profile.name, ignoreCase = true)) {
            val existing = userProfileRepository.getProfileByName(trimmedName)
            if (existing != null && existing.id != profile.id) {
                return SaveProfileResult.DuplicateName(trimmedName)
            }
        }

        val updatedProfile = profile.copy(
            name = trimmedName,
            description = newDescription.trim()
        )

        return try {
            userProfileRepository.updateUserProfile(updatedProfile)
            SaveProfileResult.Success(trimmedName)
        } catch (e: Exception) {
            val isDuplicate = e is SQLiteConstraintException ||
                e.message?.contains("UNIQUE constraint", ignoreCase = true) == true

            if (isDuplicate) {
                SaveProfileResult.DuplicateName(trimmedName)
            } else {
                SaveProfileResult.Error(e.localizedMessage ?: "Unbekannter Fehler")
            }
        }
    }

    suspend fun deleteUserProfile(profileId: Long): Boolean {
        return try {
            userProfileRepository.deleteUserProfile(profileId)
            true
        } catch (e: Exception) {
            false
        }
    }
}
