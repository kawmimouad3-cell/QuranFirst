package com.example.quranfirst

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.quranfirst.data.AppDatabase
import com.example.quranfirst.data.PrayerTime
import com.example.quranfirst.utils.CityMapper
import com.example.quranfirst.utils.HabousScraper
import com.example.quranfirst.utils.PrayerScheduler
import com.example.quranfirst.work.PrayerUpdateWorker
import com.google.android.gms.location.LocationServices
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var textClock: TextView
    private lateinit var textDate: TextView
    private lateinit var tvNextPrayer: TextView
    private lateinit var switchAdhan: SwitchMaterial
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvFajr: TextView
    private lateinit var tvShrouq: TextView
    private lateinit var tvDhuhr: TextView
    private lateinit var tvAsr: TextView
    private lateinit var tvMaghrib: TextView
    private lateinit var tvIsha: TextView

    private var currentPrayerTime: PrayerTime? = null

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateClock()
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textClock = findViewById(R.id.text_clock)
        textDate = findViewById(R.id.text_date)
        tvNextPrayer = findViewById(R.id.tvNextPrayer)
        switchAdhan = findViewById(R.id.switch_adhan)
        
        tvFajr = findViewById(R.id.tv_fajr)
        tvShrouq = findViewById(R.id.tv_shrouq)
        tvDhuhr = findViewById(R.id.tv_dhuhr)
        tvAsr = findViewById(R.id.tv_asr)
        tvMaghrib = findViewById(R.id.tv_maghrib)
        tvIsha = findViewById(R.id.tv_isha)

        // Adhan Settings switch
        val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        switchAdhan.isChecked = sharedPrefs.getBoolean("adhan_enabled", true)
        switchAdhan.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("adhan_enabled", isChecked).apply()
        }

        // Start the clock and countdown
        handler.post(updateTimeRunnable)

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_quran -> {
                    startActivity(android.content.Intent(this, SurahActivity::class.java))
                    true
                }
                else -> false
            }
        }
        
        schedulePrayerUpdateWorker(1) // will be updated after location fetch
        requestPermissionsAndFetchLocation()
    }

    private fun schedulePrayerUpdateWorker(villeId: Int) {
        val data = workDataOf("VILLE_ID" to villeId)
        val prayerUpdateWorkRequest = PeriodicWorkRequestBuilder<PrayerUpdateWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
        .setInputData(data)
        .setInitialDelay(1, TimeUnit.DAYS)
        .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "PrayerUpdateWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            prayerUpdateWorkRequest
        )
    }

    private fun requestPermissionsAndFetchLocation() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        } else {
            fetchLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            updatePrayerTimesForCity(1) 
        }
    }

    private fun fetchLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val city = CityMapper.findNearestCity(location.latitude, location.longitude)
                    schedulePrayerUpdateWorker(city.id) // update worker with real city
                    updatePrayerTimesForCity(city.id)
                } else {
                    updatePrayerTimesForCity(1)
                }
            }.addOnFailureListener {
                updatePrayerTimesForCity(1)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            updatePrayerTimesForCity(1)
        }
    }

    private fun updatePrayerTimesForCity(villeId: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val prayerTime = withContext(Dispatchers.IO) {
                    db.prayerDao().getPrayerTimesByDate(villeId, todayStr)
                }

                if (prayerTime != null) {
                    displayPrayerTimes(prayerTime)
                    scheduleAlarms(prayerTime)
                } else {
                    // Start WorkManager in background for full future parsing
                    val inputData = Data.Builder().putInt("VILLE_ID", villeId).build()
                    val fetchWork = OneTimeWorkRequestBuilder<PrayerUpdateWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(this@MainActivity).enqueue(fetchWork)
                    
                    // Immediately fetch inline because DB is empty
                    fetchPrayersImmediately(villeId, todayStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchPrayersImmediately(villeId: Int, todayStr: String) {
        withContext(Dispatchers.Main) {
            tvNextPrayer.text = "جاري تحميل أوقات الصلاة..."
        }
        withContext(Dispatchers.IO) {
            try {
                val prayers = HabousScraper.getPrayerTimes(villeId)
                if (prayers.isNotEmpty()) {
                    val db = AppDatabase.getDatabase(this@MainActivity)
                    db.prayerDao().insertAll(prayers)
                    val todayPrayer = prayers.find { it.date == todayStr }
                    withContext(Dispatchers.Main) {
                        todayPrayer?.let { 
                            displayPrayerTimes(it)
                            scheduleAlarms(it)
                        } ?: run {
                            tvNextPrayer.text = "بيانات اليوم غير متوفرة"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvNextPrayer.text = "يرجى تشغيل الإنترنت لتحديث الأوقات"
                    }
                }
            } catch (e: Exception) { 
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    tvNextPrayer.text = "حدث خطأ في الاتصال بخادم الوزارة"
                }
            }
        }
    }

    private fun displayPrayerTimes(prayerTime: PrayerTime) {
        currentPrayerTime = prayerTime
        tvFajr.text = prayerTime.fajr
        tvShrouq.text = prayerTime.shrouq
        tvDhuhr.text = prayerTime.dhuhr
        tvAsr.text = prayerTime.asr
        tvMaghrib.text = prayerTime.maghrib
        tvIsha.text = prayerTime.isha
    }

    private fun updateCountdown() {
        val prayer = currentPrayerTime ?: return
        val times = listOf(
            "Fajr"    to prayer.fajr,
            "Dhuhr"   to prayer.dhuhr,
            "Asr"     to prayer.asr,
            "Maghrib" to prayer.maghrib,
            "Isha"    to prayer.isha
        )

        val now = Calendar.getInstance()
        var nextPrayer: Pair<String, Calendar>? = null

        for (time in times) {
            val prayerCal = parseTimeToCalendar(time.second)
            if (prayerCal.after(now)) {
                nextPrayer = time.first to prayerCal
                break
            }
        }

        // If all prayers done today → show countdown to tomorrow's Fajr
        if (nextPrayer == null) {
            val tomorrowFajr = parseTimeToCalendar(prayer.fajr).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val diff = tomorrowFajr.timeInMillis - now.timeInMillis
            val hours   = diff / (1000 * 60 * 60)
            val minutes = (diff / (1000 * 60)) % 60
            val seconds = (diff / 1000) % 60
            tvNextPrayer.text = String.format(
                Locale.getDefault(),
                "🌄 الفجر غداً في %02d:%02d:%02d",
                hours, minutes, seconds
            )
            return
        }

        val diff    = nextPrayer.second.timeInMillis - now.timeInMillis
        val hours   = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60
        val seconds = (diff / 1000) % 60

        val emoji = mapOf(
            "Fajr" to "🌄", "Dhuhr" to "☀️",
            "Asr"  to "🌤️", "Maghrib" to "🌇", "Isha" to "🌙"
        )[nextPrayer.first] ?: "🕌"

        tvNextPrayer.text = String.format(
            Locale.getDefault(),
            "%s الصلاة القادمة: %s في %02d:%02d:%02d",
            emoji, nextPrayer.first, hours, minutes, seconds
        )
    }

    private fun parseTimeToCalendar(timeStr: String): Calendar {
        val parts = timeStr.split(":")
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0)
        }
    }

    private fun scheduleAlarms(prayer: PrayerTime) {
        PrayerScheduler.parseAndSchedule(this, "Fajr", prayer.fajr)
        PrayerScheduler.parseAndSchedule(this, "Dhuhr", prayer.dhuhr)
        PrayerScheduler.parseAndSchedule(this, "Asr", prayer.asr)
        PrayerScheduler.parseAndSchedule(this, "Maghrib", prayer.maghrib)
        PrayerScheduler.parseAndSchedule(this, "Isha", prayer.isha)
    }

    private fun updateClock() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        
        textClock.text = timeFormat.format(calendar.time)
        textDate.text = dateFormat.format(calendar.time)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
    }
}
