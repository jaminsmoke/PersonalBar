package com.jaminsmoke.personalbar.lan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jaminsmoke.personalbar.MainActivity
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R

/**
 * Servicio en primer plano «Local activo»: mantiene el nodo LAN vivo con pantalla
 * bloqueada. Es dueño del ciclo del server (arranca/para [PersonalBarApp.startLocal]/[stopLocal])
 * y sujeta un partial WakeLock + WifiLock mientras corre.
 */
class BarLanService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForegroundCompat(buildNotification())
        acquireLocks()
        PersonalBarApp.get().startLocal()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        PersonalBarApp.get().stopLocal()
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_local_activo)
            .setContentTitle(getString(R.string.local_activo_notif_titulo))
            .setContentText(getString(R.string.local_activo_notif_texto))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.canal_local),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    // El wakelock se mantiene vivo a propósito mientras el nodo está activo (se suelta en onDestroy).
    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG)
        }
        wakeLock?.acquire()

        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, LOCK_TAG)
        }
        wifiLock?.acquire()
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val CHANNEL_ID = "local_activo"
        const val NOTIFICATION_ID = 8787
        private const val LOCK_TAG = "com.jaminsmoke.personalbar:local_activo"

        /** Arranca el nodo como foreground service (API 26+ exige startForeground en <5 s). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BarLanService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BarLanService::class.java))
        }
    }
}
