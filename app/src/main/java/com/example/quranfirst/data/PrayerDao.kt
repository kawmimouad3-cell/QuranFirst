package com.example.quranfirst.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_times WHERE villeId = :villeId AND date = :date LIMIT 1")
    fun getPrayerTimesByDate(villeId: Int, date: String): PrayerTime?
    
    @Query("SELECT * FROM prayer_times WHERE villeId = :villeId AND date >= :startDate ORDER BY date ASC")
    fun getFuturePrayerTimes(villeId: Int, startDate: String): List<PrayerTime>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(prayerTimes: List<PrayerTime>)

    @Query("DELETE FROM prayer_times")
    fun clearAll()
}
