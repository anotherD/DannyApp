package com.danny.dannyapp.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CareerRepository(
    private val careerDao: CareerDao,
    private val seasonRepository: SeasonRepository
) {

    val careersFlow: Flow<List<CareerWithProfile>> = careerDao.getAllCareers()

    suspend fun getCareers(): List<CareerWithProfile> = withContext(Dispatchers.IO) {
        careerDao.getAllCareersList()
    }

    /**
     * Retrieves a career with profile by its ID.
     * Throws [IllegalArgumentException] if ID is invalid or [IllegalStateException] if career is not found.
     *
     * @param id The career ID.
     * @return The [CareerWithProfile] instance.
     */
    suspend fun getCareerById(id: Long): CareerWithProfile = withContext(Dispatchers.IO) {
        require(id > 0L) { "Ungültige Karriere-ID: $id" }
        requireNotNull(careerDao.getCareerById(id)) { "Karriere mit ID $id nicht gefunden" }
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

    /**
     * Updates an existing career in the database.
     */
    suspend fun updateCareer(career: Career) = withContext(Dispatchers.IO) {
        require(career.id > 0L) { "Cannot update career without valid ID" }
        careerDao.insertCareer(career)
    }

    suspend fun deleteCareer(id: Long) = withContext(Dispatchers.IO) {
        careerDao.deleteCareerById(id)
    }
}
