package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SosBackgroundService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "SosBackgroundServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_SOS = "com.example.ACTION_SOS"
        private const val SHAKE_THRESHOLD = 12f
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Shake tracking
    private var mAccel = 0f
    private var mAccelCurrent = SensorManager.GRAVITY_EARTH
    private var mAccelLast = SensorManager.GRAVITY_EARTH
    private var lastShakeTime = 0L

    // Fall tracking
    private var lastFreeFallTime = 0L
    private var lastImpactTime = 0L

    // Volume tracking
    private var volumeClickCount = 0
    private var lastVolumeClickTime = 0L

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastVolumeClickTime > 3000) {
                    volumeClickCount = 0
                }
                volumeClickCount++
                lastVolumeClickTime = currentTime

                if (volumeClickCount >= 3) {
                    triggerSosFromBackground()
                    volumeClickCount = 0
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            volumeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(volumeReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SOS) {
            triggerSosFromBackground()
        }

        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun triggerSosFromBackground() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("TRIGGER_SOS", true)
        }
        startActivity(mainIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate G-Force
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH
            val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            // Shake detection (High frequency change)
            mAccelLast = mAccelCurrent
            mAccelCurrent = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = mAccelCurrent - mAccelLast
            mAccel = mAccel * 0.9f + delta

            val now = System.currentTimeMillis()
            if (mAccel > SHAKE_THRESHOLD) {
                if (now - lastShakeTime > 2000) {
                    lastShakeTime = now
                    triggerSosFromBackground()
                }
            }

            // Fall detection heuristic
            // 1. Free fall (near 0G)
            if (gForce < 0.3f) {
                if (now - lastFreeFallTime > 1000) {
                    lastFreeFallTime = now
                }
            }
            // 2. Sudden impact (high G) shortly after free fall
            if (gForce > 2.5f) {
                if (now - lastFreeFallTime < 1500) {
                    if (now - lastImpactTime > 5000) {
                        lastImpactTime = now
                        triggerSosFromBackground()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SOS Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val sosIntent = Intent(this, SosBackgroundService::class.java).apply {
            action = ACTION_SOS
        }
        val sosPendingIntent = PendingIntent.getService(
            this, 0, sosIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Service Active")
            .setContentText("Emergency tracking active. Sensors and Volume buttons monitored.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_dialog_alert, "SOS NOW", sosPendingIntent)
            .setOngoing(true)
            .build()
    }
}
