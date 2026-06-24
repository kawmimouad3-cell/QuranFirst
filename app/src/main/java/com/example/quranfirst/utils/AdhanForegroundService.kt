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
import com.example.quranfirst.R

class AdhanForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val CHANNEL_ID = "AdhanServiceChannel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Salat"
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adhan en cours")
            .setContentText("C'est l'heure de $prayerName")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }

        playAdhan()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Adhan Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun playAdhan() {
        val sharedPrefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
        val isAdhanEnabled = sharedPrefs.getBoolean("adhan_enabled", true)
        if (!isAdhanEnabled) {
            stopSelf()
            return
        }

        try {
            val adhanId = resources.getIdentifier("adhan", "raw", packageName)
            if (adhanId != 0) {
                mediaPlayer = MediaPlayer.create(this, adhanId)
                mediaPlayer?.start()
                mediaPlayer?.setOnCompletionListener { 
                    stopSelf() 
                }
            } else {
                // Fallback aux sonneries par défaut si l'utilisateur n'a pas ajouté le fichier mp3
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(applicationContext, uri)
                r.play()
                stopSelf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
}
