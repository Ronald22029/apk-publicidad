package com.santidad.app.app_movil_publi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log

class BootService : Service() {
    companion object {
        private const val TAG = "BootService"
        private const val CHANNEL_ID = "santidad_tv_autostart"
        private const val NOTIFICATION_ID = 6102
        private val LAUNCH_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scheduledTasks = mutableListOf<Runnable>()
    private var launchSequenceScheduled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!launchSequenceScheduled) {
            launchSequenceScheduled = true
            LAUNCH_DELAYS_MS.forEachIndexed { index, delay ->
                schedule(delay) { launchMainActivity("intento ${index + 1}") }
            }
            schedule(LAUNCH_DELAYS_MS.last() + 5_000L) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun schedule(delayMillis: Long, action: () -> Unit) {
        val task = Runnable { action() }
        scheduledTasks += task
        handler.postDelayed(task, delayMillis)
    }

    private fun launchMainActivity(attempt: String) {
        val overlayGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        Log.i(TAG, "Auto-inicio $attempt; permiso de superposicion=$overlayGranted")

        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_AUTOSTART_LAUNCH, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            startActivity(launchIntent)
            Log.i(TAG, "Solicitud de apertura enviada al sistema ($attempt)")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo solicitar la apertura ($attempt)", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Inicio automatico Santidad TV",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Prepara el reproductor despues de encender el televisor"
            setShowBadge(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Santidad TV")
            .setContentText("Preparando el reproductor...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scheduledTasks.forEach { handler.removeCallbacks(it) }
        scheduledTasks.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
