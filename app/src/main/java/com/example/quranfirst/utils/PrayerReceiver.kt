package com.example.quranfirst.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class PrayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Salat"
        val isPreNotification = intent.getBooleanExtra("IS_PRE_NOTIFICATION", false)

        if (isPreNotification) {
            showNotification(context, "Rappel de Prière", "Il reste 5 minutes pour la prière de $prayerName")
        } else {
            // Démarrer le Foreground Service pour l'Adhan de manière fiable
            val serviceIntent = Intent(context, AdhanForegroundService::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "prayer_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Horaires de prière", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

object PrayerScheduler {
    fun schedulePrayer(context: Context, prayerName: String, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Prevent scheduling in the past
        if (timeInMillis < System.currentTimeMillis()) return

        // 1. Notification 5 min avant
        val preIntent = Intent(context, PrayerReceiver::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            putExtra("IS_PRE_NOTIFICATION", true)
        }
        val prePendingIntent = PendingIntent.getBroadcast(
            context,
            (timeInMillis / 1000).toInt() - 5,
            preIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis - 5 * 60 * 1000,
            prePendingIntent
        )

        // 2. Notification à l'heure + Adhan
        val adhanIntent = Intent(context, PrayerReceiver::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            putExtra("IS_PRE_NOTIFICATION", false)
        }
        val adhanPendingIntent = PendingIntent.getBroadcast(
            context,
            (timeInMillis / 1000).toInt(),
            adhanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis,
            adhanPendingIntent
        )
    }
    
    fun parseAndSchedule(context: Context, prayerName: String, timeStr: String) {
        try {
            val parts = timeStr.split(":")
            if(parts.size == 2) {
                val hour = parts[0].toInt()
                val min = parts[1].toInt()
                
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, min)
                    set(Calendar.SECOND, 0)
                }
                schedulePrayer(context, prayerName, calendar.timeInMillis)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
}


