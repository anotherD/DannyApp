package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CareerRepository(
    private val careerDao: CareerDao,
    private val seasonRepository: SeasonRepository
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).careerDao(),
        SeasonRepository(context.applicationContext)
    )

    val careersFlow: Flow<List<CareerWithProfile>> = careerDao.getAllCareers()

    suspend fun getCareers(): List<CareerWithProfile> = withContext(Dispatchers.IO) {
        careerDao.getAllCareersList()
    }

    suspend fun getCareerById(id: Long): CareerWithProfile? = withContext(Dispatchers.IO) {
        careerDao.getCareerById(id)
    }

    suspend fun getCareerCountForProfile(profileId: Long): Int = withContext(Dispatchers.IO) {
        careerDao.getCareerCountForProfile(profileId)
    }

    suspend fun saveCareer(career: Career): Long = withContext(Dispatchers.IO) {
        val isNewCareer = career.id == 0L
        val careerId = careerDao.insertCareer(career)
        if (isNewCareer) {
            seasonRepository.createSeason(careerId)
        }
        careerId
    }

    suspend fun deleteCareer(id: Long) = withContext(Dispatchers.IO) {
        careerDao.deleteCareerById(id)
    }
}
