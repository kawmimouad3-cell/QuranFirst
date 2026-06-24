package com.example.quranfirst.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AdhanForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayerName   = intent?.getStringExtra("PRAYER_NAME")   ?: "Salat"
        val prayerEmoji  = intent?.getStringExtra("PRAYER_EMOJI")  ?: "🕌"
        val prayerArabic = intent?.getStringExtra("PRAYER_ARABIC") ?: prayerName
        val notifId      = intent?.getIntExtra("NOTIF_ID", 2000)   ?: 2000

        val channelId = "adhan_service_$prayerName"
        createNotificationChannel(channelId, prayerArabic)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("$prayerEmoji حان وقت صلاة $prayerArabic")
            .setContentText("اللهُ أكبر • اللهُ أكبر • حيَّ على الصلاة")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notifId, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notifId, notification)
        }

        playAdhan()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun createNotificationChannel(channelId: String, prayerArabic: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "أذان $prayerArabic",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل الأذان لصلاة $prayerArabic"
                setSound(null, null) // Sound handled by MediaPlayer
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun playAdhan() {
        // Check if user enabled adhan
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        if (!prefs.getBoolean("adhan_enabled", true)) {
            stopSelf()
            return
        }

        try {
            val adhanId = resources.getIdentifier("adhan", "raw", packageName)
            if (adhanId != 0) {
                mediaPlayer = MediaPlayer.create(this, adhanId)
                mediaPlayer?.setOnCompletionListener { stopSelf() }
                mediaPlayer?.start()
            } else {
                // Fallback to default notification sound
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(applicationContext, uri)?.play()
                stopSelf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
}
