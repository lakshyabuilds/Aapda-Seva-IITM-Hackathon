package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentBackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: IncidentBackupEntity)

    @Query("SELECT * FROM incident_backups ORDER BY timestamp DESC")
    fun getAllBackups(): Flow<List<IncidentBackupEntity>>

    @Query("SELECT * FROM incident_backups ORDER BY timestamp DESC")
    suspend fun getPendingBackups(): List<IncidentBackupEntity>

    @Query("DELETE FROM incident_backups WHERE incidentId = :incidentId")
    suspend fun deleteBackup(incidentId: String)
}
