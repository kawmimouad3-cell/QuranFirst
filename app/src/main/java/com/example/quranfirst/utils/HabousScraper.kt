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
            
            val calendar = Calendar.getInstance()
            // On commence au 1er jour du mois affiché sur le site
            // Le site affiche généralement le mois en cours
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            
            for (i in 1 until rows.size) { // On saute l'entête
                val row = rows[i]
                val cols = row.select("td")
                
                if (cols.size < 9) continue
                
                // On récupère les colonnes par index
                val dayHijri = cols[1].text().trim()
                val dayMonth = cols[2].text().trim() // Ex: "01"
                val fajr = cols[3].text().trim()
                val shrouq = cols[4].text().trim()
                val dhuhr = cols[5].text().trim()
                val asr = cols[6].text().trim()
                val maghrib = cols[7].text().trim()
                val isha = cols[8].text().trim()

                // On ajuste le calendrier au jour indiqué dans la colonne
                try {
                    val dayInt = dayMonth.toInt()
                    calendar.set(Calendar.DAY_OF_MONTH, dayInt)
                } catch (e: Exception) {
                    // Si le parsing échoue, on continue simplement
                }

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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext prayerData
    }
}
