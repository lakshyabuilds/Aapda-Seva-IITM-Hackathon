package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_services")
data class EmergencyServiceEntity(
    @PrimaryKey
    val id: String, // Constructed as source_originalId to ensure uniqueness
    val name: String,
    val type: String, // e.g., "Hospital", "Police", "Rescue", "Towing"
    val lat: Double,
    val lon: Double,
    val phone: String,
    val source: String, // "Overpass", "Wikidata", "Nominatim", etc.
    val timestamp: Long = System.currentTimeMillis()
)
