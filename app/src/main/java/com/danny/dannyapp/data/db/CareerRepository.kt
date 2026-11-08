package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CareerRepository(context: Context) {

    private val careerDao: CareerDao = AppDatabase.getDatabase(context.applicationContext).careerDao()

    val careersFlow: Flow<List<CareerWithProfile>> = careerDao.getAllCareers()

    suspend fun getCareers(): List<CareerWithProfile> = withContext(Dispatchers.IO) {
        careerDao.getAllCareersList()
    }

    suspend fun getCareerById(id: Long): CareerWithProfile? = withContext(Dispatchers.IO) {
        careerDao.getCareerById(id)
    }

    suspend fun saveCareer(career: Career) = withContext(Dispatchers.IO) {
        careerDao.insertCareer(career)
    }

    suspend fun deleteCareer(id: Long) = withContext(Dispatchers.IO) {
        careerDao.deleteCareerById(id)
    }
}
