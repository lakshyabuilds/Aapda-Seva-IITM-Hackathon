package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyServiceDao {
    @Query("SELECT * FROM emergency_services ORDER BY timestamp DESC")
    fun getAllServices(): Flow<List<EmergencyServiceEntity>>

    @Query("SELECT * FROM emergency_services WHERE type = :type ORDER BY timestamp DESC")
    fun getServicesByType(type: String): Flow<List<EmergencyServiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(services: List<EmergencyServiceEntity>)

    @Query("DELETE FROM emergency_services WHERE timestamp < :expirationTime")
    suspend fun deleteOldServices(expirationTime: Long)
}
