package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.Contact
import com.example.ContactDao

@Database(entities = [EmergencyServiceEntity::class, Contact::class, UserProfileEntity::class, IncidentBackupEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyServiceDao(): EmergencyServiceDao
    abstract fun contactDao(): ContactDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun incidentBackupDao(): IncidentBackupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emergency_app_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
