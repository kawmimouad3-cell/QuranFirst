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

// Map each prayer to its own unique notification ID and emoji
private val PRAYER_NOTIFICATION_IDS = mapOf(
    "Fajr"    to 1001,
    "Shrouq"  to 1002,
    "Dhuhr"   to 1003,
    "Asr"     to 1004,
    "Maghrib" to 1005,
    "Isha"    to 1006
)

private val PRAYER_EMOJIS = mapOf(
    "Fajr"    to "🌄",
    "Shrouq"  to "🌅",
    "Dhuhr"   to "☀️",
    "Asr"     to "🌤️",
    "Maghrib" to "🌇",
    "Isha"    to "🌙"
)

private val PRAYER_ARABIC = mapOf(
    "Fajr"    to "الفجر",
    "Shrouq"  to "الشروق",
    "Dhuhr"   to "الظهر",
    "Asr"     to "العصر",
    "Maghrib" to "المغرب",
    "Isha"    to "العشاء"
)

class PrayerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Salat"
        val isPreNotification = intent.getBooleanExtra("IS_PRE_NOTIFICATION", false)

        val emoji = PRAYER_EMOJIS[prayerName] ?: "🕌"
        val arabicName = PRAYER_ARABIC[prayerName] ?: prayerName
        val notifId = PRAYER_NOTIFICATION_IDS[prayerName] ?: System.currentTimeMillis().toInt()

        if (isPreNotification) {
            // Unique reminder notification per prayer
            showPrayerNotification(
                context = context,
                notifId = notifId + 100, // offset for pre-notification
                title = "$emoji تنبيه: صلاة $arabicName",
                message = "باقي 5 دقائق على أذان $arabicName ، استعد للصلاة",
                channelId = "prayer_reminder_$prayerName"
            )
        } else {
            // Start Foreground Service to play Adhan LOUDLY
            val serviceIntent = Intent(context, AdhanForegroundService::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
                putExtra("PRAYER_EMOJI", emoji)
                putExtra("PRAYER_ARABIC", arabicName)
                putExtra("NOTIF_ID", notifId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    private fun showPrayerNotification(
        context: Context,
        notifId: Int,
        title: String,
        message: String,
        channelId: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تذكير بمواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه قبل الصلاة بـ 5 دقائق"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(notifId, notification)
    }
}

object PrayerScheduler {

    fun schedulePrayer(context: Context, prayerName: String, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Don't schedule prayers in the past
        if (timeInMillis < System.currentTimeMillis()) return

        val prayerHashCode = prayerName.hashCode()

        // ── 1. Reminder 5 minutes BEFORE prayer ──────────────────────────────
        val preIntent = Intent(context, PrayerReceiver::class.java).apply {
            action = "com.example.quranfirst.PRAYER_REMINDER_$prayerName"
            putExtra("PRAYER_NAME", prayerName)
            putExtra("IS_PRE_NOTIFICATION", true)
        }
        val prePendingIntent = PendingIntent.getBroadcast(
            context,
            prayerHashCode + 100,
            preIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis - 5 * 60 * 1000,
            prePendingIntent
        )

        // ── 2. Adhan AT prayer time ───────────────────────────────────────────
        val adhanIntent = Intent(context, PrayerReceiver::class.java).apply {
            action = "com.example.quranfirst.PRAYER_ADHAN_$prayerName"
            putExtra("PRAYER_NAME", prayerName)
            putExtra("IS_PRE_NOTIFICATION", false)
        }
        val adhanPendingIntent = PendingIntent.getBroadcast(
            context,
            prayerHashCode,
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
            if (parts.size == 2) {
                val hour = parts[0].toInt()
                val min  = parts[1].toInt()
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, min)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                schedulePrayer(context, prayerName, calendar.timeInMillis)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
