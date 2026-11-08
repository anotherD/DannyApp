package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UserProfileRepository(context: Context) {

    private val userProfileDao: UserProfileDao = AppDatabase.getDatabase(context.applicationContext).userProfileDao()

    val userProfilesFlow: Flow<List<UserProfile>> = userProfileDao.getAllUserProfiles()

    suspend fun getUserProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        userProfileDao.getAllUserProfilesList()
    }

    suspend fun getUserProfileById(id: Long): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getUserProfileById(id)
    }

    suspend fun getProfileByName(name: String): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getProfileByName(name)
    }

    suspend fun saveUserProfile(userProfile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.insertUserProfile(userProfile)
    }

    suspend fun deleteUserProfile(id: Long) = withContext(Dispatchers.IO) {
        userProfileDao.deleteUserProfileById(id)
    }
}
