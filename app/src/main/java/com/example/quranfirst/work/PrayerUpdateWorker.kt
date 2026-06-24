package com.example.quranfirst.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.quranfirst.data.AppDatabase
import com.example.quranfirst.utils.HabousScraper

class PrayerUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val villeId = inputData.getInt("VILLE_ID", -1)
        if (villeId == -1) return Result.failure()

        return try {
            // Scrape les 2 mois
            val prayers = HabousScraper.getPrayerTimes(villeId)
            
            if (prayers.isNotEmpty()) {
                val db = AppDatabase.getDatabase(applicationContext)
                // Optionnel : db.prayerDao().clearAll() si on ne garde que la ville actuelle
                db.prayerDao().insertAll(prayers)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
