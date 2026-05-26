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
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class SosBackgroundService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "SosBackgroundServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_SOS = "com.example.ACTION_SOS"
        private const val SHAKE_THRESHOLD = 12f // Lowered to 12f so a 2-second shake is easily maintained
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var accelerometer: Sensor? = null
    private var vibrator: Vibrator? = null

    // Location
    private var lastKnownLocation: Location? = null

    private fun handleNewLocation(location: Location) {
        val currentBest = lastKnownLocation
        if (currentBest == null) {
            lastKnownLocation = location
            return
        }

        val timeDelta = location.time - currentBest.time
        val isSignificantlyNewer = timeDelta > 60000
        val isSignificantlyOlder = timeDelta < -60000
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) {
            lastKnownLocation = location
        } else if (!isSignificantlyOlder) {
            val accuracyDelta = (location.accuracy - currentBest.accuracy).toInt()
            val isLessAccurate = accuracyDelta > 0
            val isMoreAccurate = accuracyDelta < 0
            val isSignificantlyLessAccurate = accuracyDelta > 200

            if (isMoreAccurate) {
                lastKnownLocation = location
            } else if (isNewer && !isLessAccurate) {
                lastKnownLocation = location
            } else if (isNewer && !isSignificantlyLessAccurate && location.provider == currentBest.provider) {
                lastKnownLocation = location
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            for (location in p0.locations) {
                handleNewLocation(location)
            }
        }
    }

    // Shake tracking
    private var mAccel = 0f
    private var mAccelCurrent = SensorManager.GRAVITY_EARTH
    private var mAccelLast = SensorManager.GRAVITY_EARTH

    private var isShaking = false
    private var shakeStartTime = 0L
    private var lastShakeEventTime = 0L
    private val SHAKE_DURATION_REQUIRED = 2000L // Needs to shake for at least 2 seconds
    private val SHAKE_STOP_TIMEOUT = 1500L // Must stop for 1.5 seconds to trigger

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
                // Debounce rapid duplicate broadcasts from a single physical click (commonly < 200ms)
                if (currentTime - lastVolumeClickTime < 300) {
                    return
                }
                
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
        
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            volumeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    private var isTrackingStarted = false

    private fun startTracking() {
        if (isTrackingStarted) return
        isTrackingStarted = true

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sosapp:background_wake_lock")
        try {
            wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000)
                    .setMinUpdateIntervalMillis(10000)
                    .setMaxUpdateDelayMillis(30000)
                    .build()
                @Suppress("MissingPermission")
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            wakeLock?.let {
                 if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } catch (e: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startTracking()
        }, 1000)

        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun triggerSosFromBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("TRIGGER_SOS", true)
            lastKnownLocation?.let { loc ->
                putExtra("LAST_LAT", loc.latitude)
                putExtra("LAST_LON", loc.longitude)
            }
        }
        
        try {
            startActivity(mainIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Also use full screen intent notification to bypass Android 10+ background launch restrictions
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            123,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createHighPriorityNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(this, "high_priority_sos")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS Trigger Tracking Activated")
            .setContentText("Tap here to start SOS transmission and capture emergency media!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notificationBuilder.build())
    }

    private fun createHighPriorityNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "high_priority_sos",
                "High Priority SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used to show SOS trigger immediately"
                setBypassDnd(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
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
                if (!isShaking) {
                    isShaking = true
                    shakeStartTime = now
                }
                lastShakeEventTime = now
            } else {
                if (isShaking) {
                    val timeSinceLastShake = now - lastShakeEventTime
                    if (timeSinceLastShake > SHAKE_STOP_TIMEOUT) {
                        val shakeDuration = lastShakeEventTime - shakeStartTime
                        if (shakeDuration > SHAKE_DURATION_REQUIRED) {
                            // Shook for at least 2 seconds, and now stopped!
                            triggerSosFromBackground()
                        }
                        // Reset
                        isShaking = false
                    }
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
                    if (now - lastImpactTime > 10000) {
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
