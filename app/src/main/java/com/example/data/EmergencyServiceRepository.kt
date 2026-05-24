package com.example.data

import kotlinx.coroutines.flow.Flow

class EmergencyServiceRepository(private val dao: EmergencyServiceDao) {
    val allServices: Flow<List<EmergencyServiceEntity>> = dao.getAllServices()

    fun getServicesByType(type: String): Flow<List<EmergencyServiceEntity>> {
        return dao.getServicesByType(type)
    }

    suspend fun insertServices(services: List<EmergencyServiceEntity>) {
        dao.insertServices(services)
    }

    suspend fun clearOldCache() {
        val expirationTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 1 day
        dao.deleteOldServices(expirationTime)
    }
}
