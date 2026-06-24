package com.example.quranfirst.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_times")
data class PrayerTime(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Format YYYY-MM-DD
    val dayHijri: String,
    val dayMonth: String,
    val fajr: String,
    val shrouq: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val villeId: Int
)
