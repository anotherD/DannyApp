package com.danny.dannyapp.data

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CareerRepository(context: Context) {

    private val careerDao: CareerDao = AppDatabase.getDatabase(context).careerDao()

    val careersFlow: Flow<List<Career>> = careerDao.getAllCareers()

    suspend fun getCareers(): List<Career> = withContext(Dispatchers.IO) {
        careerDao.getAllCareersList()
    }

    suspend fun saveCareer(career: Career) = withContext(Dispatchers.IO) {
        careerDao.insertCareer(career)
    }

    suspend fun deleteCareer(id: String) = withContext(Dispatchers.IO) {
        careerDao.deleteCareerById(id)
    }
}
