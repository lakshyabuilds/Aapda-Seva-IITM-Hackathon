package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incident_backups")
data class IncidentBackupEntity(
    @PrimaryKey
    val incidentId: String,
    val timestamp: String,
    val payloadJson: String
)
