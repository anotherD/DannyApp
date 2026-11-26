package com.danny.dannyapp.ui.profile

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.domain.model.Country
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

sealed interface SaveProfileResult {
    data class Success(val profileName: String) : SaveProfileResult
    data class DuplicateName(val profileName: String) : SaveProfileResult
    data class Error(val message: String) : SaveProfileResult
}

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val userProfileRepository = application.appContainer.userProfileRepository
    private val careerRepository = application.appContainer.careerRepository

    val profilesState: StateFlow<List<UserProfile>> = userProfileRepository.userProfilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun getCareerCountForProfile(profileId: Long): Int {
        return careerRepository.getCareerCountForProfile(profileId)
    }

    suspend fun saveUserProfile(name: String, playerName: String, countryCode: String, description: String): SaveProfileResult {
        val trimmedName = name.trim()
        val trimmedPlayerName = playerName.trim().ifEmpty { trimmedName }
        val existing = userProfileRepository.getProfileByName(trimmedName)
        if (existing != null) {
            return SaveProfileResult.DuplicateName(trimmedName)
        }

        val newProfile = UserProfile(
            name = trimmedName,
            playerName = trimmedPlayerName,
            countryCode = countryCode.ifEmpty { Country.DEFAULT_USER_CODE },
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
                getApplication<Application>().appContainer.reportError("Profil speichern", e)
                SaveProfileResult.Error(e.localizedMessage ?: getApplication<Application>().getString(R.string.error_unknown))
            }
        }
    }

    suspend fun updateUserProfile(profile: UserProfile, newName: String, newPlayerName: String, newCountryCode: String, newDescription: String): SaveProfileResult {
        val trimmedName = newName.trim()
        val trimmedPlayerName = newPlayerName.trim().ifEmpty { trimmedName }
        if (trimmedName.isEmpty()) {
            return SaveProfileResult.Error(getApplication<Application>().getString(R.string.error_empty_name))
        }

        if (!trimmedName.equals(profile.name, ignoreCase = true)) {
            val existing = userProfileRepository.getProfileByName(trimmedName)
            if (existing != null && existing.id != profile.id) {
                return SaveProfileResult.DuplicateName(trimmedName)
            }
        }

        val updatedProfile = profile.copy(
            name = trimmedName,
            playerName = trimmedPlayerName,
            countryCode = newCountryCode.ifEmpty { Country.DEFAULT_USER_CODE },
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
                getApplication<Application>().appContainer.reportError("Profil speichern", e)
                SaveProfileResult.Error(e.localizedMessage ?: getApplication<Application>().getString(R.string.error_unknown))
            }
        }
    }

    suspend fun deleteUserProfile(profileId: Long) {
        require(profileId > 0L) { "Ungültige Profil-ID: $profileId" }
        try {
            userProfileRepository.deleteUserProfile(profileId)
        } catch (e: Exception) {
            getApplication<Application>().appContainer.reportError("Profil löschen", e)
            throw e
        }
    }
}
