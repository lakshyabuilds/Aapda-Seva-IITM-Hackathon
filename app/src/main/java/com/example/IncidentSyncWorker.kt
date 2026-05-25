package com.example

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import kotlinx.serialization.json.Json

class IncidentSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("IncidentSyncWorker", "Starting sync job for offline incidents")
        val database = AppDatabase.getDatabase(applicationContext)
        val backupDao = database.incidentBackupDao()

        val pending = try {
            backupDao.getPendingBackups()
        } catch (e: Exception) {
            Log.e("IncidentSyncWorker", "Error getting pending backups", e)
            return Result.retry()
        }

        if (pending.isEmpty()) {
            Log.d("IncidentSyncWorker", "No pending incidents to sync")
            return Result.success()
        }

        val json = Json { ignoreUnknownKeys = true }
        var allSuccess = true

        for (incident in pending) {
            try {
                val payload = json.decodeFromString<SosPayload>(incident.payloadJson)
                // We dispatch the payload to the dashboard
                SosRetrofitClient.service.dispatchSos(payload)
                Log.d("IncidentSyncWorker", "Successfully synced incident: ${incident.incidentId}")
                // Delete from DB on success
                backupDao.deleteBackup(incident.incidentId)
            } catch (e: Exception) {
                Log.e("IncidentSyncWorker", "Failed to sync incident: ${incident.incidentId}", e)
                allSuccess = false
            }
        }

        return if (allSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
