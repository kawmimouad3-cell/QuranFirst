package com.example.quranfirst

import android.Manifest
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
import com.example.quranfirst.utils.PrayerScheduler
import com.example.quranfirst.work.PrayerUpdateWorker
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var textClock: TextView
    private lateinit var textDate: TextView
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvFajr: TextView
    private lateinit var tvShrouq: TextView
    private lateinit var tvDhuhr: TextView
    private lateinit var tvAsr: TextView
    private lateinit var tvMaghrib: TextView
    private lateinit var tvIsha: TextView

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textClock = findViewById(R.id.text_clock)
        textDate = findViewById(R.id.text_date)
        tvFajr = findViewById(R.id.tv_fajr)
        tvShrouq = findViewById(R.id.tv_shrouq)
        tvDhuhr = findViewById(R.id.tv_dhuhr)
        tvAsr = findViewById(R.id.tv_asr)
        tvMaghrib = findViewById(R.id.tv_maghrib)
        tvIsha = findViewById(R.id.tv_isha)

        // Start the clock
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
        
        requestPermissionsAndFetchLocation()
    }

    private fun requestPermissionsAndFetchLocation() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Request POST_NOTIFICATIONS for Android 13+
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
            // Defaulting to Rabat if location denied
            updatePrayerTimesForCity(1) 
        }
    }

    private fun fetchLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val city = CityMapper.findNearestCity(location.latitude, location.longitude)
                    updatePrayerTimesForCity(city.id)
                } else {
                    updatePrayerTimesForCity(1) // Rabat par défaut
                }
            }.addOnFailureListener {
                updatePrayerTimesForCity(1)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updatePrayerTimesForCity(villeId: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@MainActivity)
            var prayerTime = withContext(Dispatchers.IO) {
                db.prayerDao().getPrayerTimesByDate(villeId, todayStr)
            }

            if (prayerTime != null) {
                displayPrayerTimes(prayerTime)
                scheduleAlarms(prayerTime)
            } else {
                // Fetch from WorkManager
                val inputData = Data.Builder().putInt("VILLE_ID", villeId).build()
                val fetchWork = OneTimeWorkRequestBuilder<PrayerUpdateWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(this@MainActivity).enqueue(fetchWork)
                
                WorkManager.getInstance(this@MainActivity).getWorkInfoByIdLiveData(fetchWork.id)
                    .observe(this@MainActivity) { workInfo ->
                        if (workInfo != null && workInfo.state == WorkInfo.State.SUCCEEDED) {
                            lifecycleScope.launch {
                                val fetchedPrayer = withContext(Dispatchers.IO) {
                                    db.prayerDao().getPrayerTimesByDate(villeId, todayStr)
                                }
                                fetchedPrayer?.let {
                                    displayPrayerTimes(it)
                                    scheduleAlarms(it)
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun displayPrayerTimes(prayerTime: PrayerTime) {
        tvFajr.text = prayerTime.fajr
        tvShrouq.text = prayerTime.shrouq
        tvDhuhr.text = prayerTime.dhuhr
        tvAsr.text = prayerTime.asr
        tvMaghrib.text = prayerTime.maghrib
        tvIsha.text = prayerTime.isha
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
