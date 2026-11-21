package com.danny.dannyapp.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UserProfileRepository(private val userProfileDao: UserProfileDao) {

    val userProfilesFlow: Flow<List<UserProfile>> = userProfileDao.getAllUserProfiles()

    suspend fun getUserProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        userProfileDao.getAllUserProfilesList()
    }

    suspend fun getProfileByName(name: String): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getProfileByName(name)
    }

    suspend fun saveUserProfile(userProfile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.insertUserProfile(userProfile)
    }

    suspend fun updateUserProfile(userProfile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.updateUserProfile(userProfile)
    }

    suspend fun deleteUserProfile(id: Long) = withContext(Dispatchers.IO) {
        userProfileDao.deleteUserProfileById(id)
    }
}
