package com.example.quranfirst.utils

import com.example.quranfirst.data.PrayerTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

object HabousScraper {
    
    suspend fun getPrayerTimes(villeId: Int): List<PrayerTime> = withContext(Dispatchers.IO) {
        val prayerData = mutableListOf<PrayerTime>()
        try {
            val url = "https://www.habous.gov.ma/prieres/index.php?ville=$villeId"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()
            
            val table = doc.select("table").first() ?: return@withContext emptyList()
            val rows = table.select("tr")
            
            // Assume the site gives times for current and next month
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            var todayFound = false
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH).toString()
            
            for (i in 1 until rows.size) { // Skip header row
                val row = rows[i]
                val cols = row.select("td")
                
                if (cols.size < 9) continue
                
                val dayMonth = cols[2].text().trim()
                
                // Very basic calendar sync logic - just a fallback for storing linearly
                val dayHijri = cols[1].text().trim()
                val fajr = cols[3].text().trim()
                val shrouq = cols[4].text().trim()
                val dhuhr = cols[5].text().trim()
                val asr = cols[6].text().trim()
                val maghrib = cols[7].text().trim()
                val isha = cols[8].text().trim()
                
                // If it matches today's print, start aligning date from today
                if (!todayFound && row.hasClass("cournt")) {
                    todayFound = true
                }
                
                if (todayFound) {
                    val dateString = dateFormat.format(calendar.time)
                    
                    prayerData.add(
                        PrayerTime(
                            date = dateString,
                            dayHijri = dayHijri,
                            dayMonth = dayMonth,
                            fajr = fajr,
                            shrouq = shrouq,
                            dhuhr = dhuhr,
                            asr = asr,
                            maghrib = maghrib,
                            isha = isha,
                            villeId = villeId
                        )
                    )
                    
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext prayerData
    }
}
