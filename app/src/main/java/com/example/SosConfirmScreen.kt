package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.telephony.SmsManager
import android.os.Build
import com.example.data.AppDatabase
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

enum class SosConfirmState {
    COUNTDOWN_INITIAL,
    SENDING_SILENT,
    SILENT_SENT_WAITING_FOR_LOUD,
    COMPLETED
}

@Composable
fun SosConfirmScreen(
    location: android.location.Location?,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    var state by remember { mutableStateOf(SosConfirmState.COUNTDOWN_INITIAL) }
    var initialCountdown by remember { mutableIntStateOf(3) }
    var loudCountdown by remember { mutableIntStateOf(3) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var audioDeferred by remember { mutableStateOf<kotlinx.coroutines.Deferred<String?>?>(null) }
    var photoDeferred by remember { mutableStateOf<kotlinx.coroutines.Deferred<String?>?>(null) }

    // Battery Intent
    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
        context.registerReceiver(null, ifilter)
    }
    val batteryPct: Float? = batteryStatus?.let { intent ->
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        level * 100 / scale.toFloat()
    }

    LaunchedEffect(Unit) {
        val incidentId = java.util.UUID.randomUUID().toString()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val isoDate = sdf.format(java.util.Date())

        val userProfileEntity = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).userProfileDao().getUserProfile()
        }
        val userIdStr = userProfileEntity?.name?.takeIf { it.isNotBlank() } ?: "UnknownUser"

        // PHASE 1: QUICK DISPATCH (Immediate Location & Status)
        val initialPayload = SosPayload(
            id = incidentId,
            incidentId = incidentId,
            userId = userIdStr,
            type = "QUICK_DISPATCH",
            timestamp = isoDate,
            source = "Android App",
            isTelemetry = false,
            stealthMode = true,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0
        )
        
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                SosRetrofitClient.service.dispatchSos(initialPayload)
            } catch (e: Exception) {}
        }

        // Parallel media capture
        val audioDeferred = async(Dispatchers.IO) { StealthMediaCapture.captureAudio(context, 3000) }
        val photoDeferred = async(Dispatchers.Main) { StealthMediaCapture.capturePhotos(context) }
        
        for (i in 3 downTo 1) {
            initialCountdown = i
            delay(1000)
        }
        state = SosConfirmState.SENDING_SILENT
        
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isLowPowerMode = powerManager.isPowerSaveMode

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryLevel = if (level >= 0 && scale > 0) level.toFloat() / scale.toFloat() else 0.5f
            
            val statusInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL
            val statusText = if (isCharging) "charging" else "discharging"
            
            val deviceInfo = mapOf(
                "os" to "Android",
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER
            )
            
            val audioData = audioDeferred.await()
            val photoData = photoDeferred.await()

            // PHASE 2: CRITICAL PAYLOAD (With Multimedia)
            val fullPayload = initialPayload.copy(
                type = "MULTI_TAP_SOS",
                locationInfo = LocationInfo(
                    speed = location?.speed,
                    heading = location?.bearing,
                    accuracy = location?.accuracy
                ),
                battery = BatteryInfo(level = batteryLevel, status = statusText, lowPowerMode = isLowPowerMode),
                device = deviceInfo,
                photo = photoData,
                audio = if (audioData != null) listOf(audioData) else emptyList()
            )
            
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    SosRetrofitClient.service.dispatchSos(fullPayload)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.e("SosApiError", "Failed to compile SOS payload", e)
        }
        
        // NATIVE CONTACT SMS
        try {
            val mapUrl = "https://www.google.com/maps/search/?api=1&query=${location?.latitude ?: 0.0},${location?.longitude ?: 0.0}"
            val message = "SOS! I need help. My current location is: $mapUrl"
            val contacts = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).contactDao().getContactsList()
            }
            
            if (contacts.isNotEmpty()) {
                val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                if (hasSmsPerm) {
                    val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    for (contact in contacts) {
                        try { smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null) } catch (e: Exception) {}
                    }
                } else {
                    val numbers = contacts.joinToString(";") { it.phoneNumber }
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$numbers")
                        putExtra("sms_body", message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(smsIntent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // HELPLINE CALL LOGIC
        try {
            var countryCode = ""
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                countryCode = telephonyManager.networkCountryIso ?: ""
                if (countryCode.isBlank()) {
                    countryCode = telephonyManager.simCountryIso ?: ""
                }
            } catch (e: Exception) {}

            if (countryCode.isBlank()) {
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    val lat = location?.latitude ?: 0.0
                    val lng = location?.longitude ?: 0.0
                    if (lat != 0.0 && lng != 0.0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val listener = @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU) object : android.location.Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                    if (addresses.isNotEmpty() && countryCode.isBlank()) {
                                        countryCode = addresses[0].countryCode ?: ""
                                    }
                                }
                                override fun onError(errorMessage: String?) {}
                            }
                            geocoder.getFromLocation(lat, lng, 1, listener)
                        } else {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(lat, lng, 1)
                            if (!addresses.isNullOrEmpty()) countryCode = addresses[0].countryCode ?: ""
                        }
                    }
                } catch (e: Exception) {}
            }

            val helplineNumber = when(countryCode.uppercase(java.util.Locale.US)) {
                "IN" -> "112"
                "US", "CA", "PK" -> "911" 
                "GB" -> "999"
                "AU" -> "000"
                "CN", "JP" -> "110"
                else -> "112" // Global GSM standard fallback
            }

            val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            val callAction = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val dialIntent = Intent(callAction).apply {
                data = Uri.parse("tel:$helplineNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            context.startActivity(dialIntent)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        state = SosConfirmState.COMPLETED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            SosConfirmState.COUNTDOWN_INITIAL -> {
                Text(
                    text = "Initiating SOS",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                CountdownRing(seconds = initialCountdown, totalSeconds = 3)
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { onCancel() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("CANCEL", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            SosConfirmState.SENDING_SILENT -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("Silent Dispatching...", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
            }
            SosConfirmState.SILENT_SENT_WAITING_FOR_LOUD -> {
                // Empty, no longer used
            }
            SosConfirmState.COMPLETED -> {
                Text(
                    text = "SOS actions dispatched.",
                    color = Color(0xFF4CAF50),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your location and emergency data have been processed.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { onFinish() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Return to Home", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun CountdownRing(seconds: Int, totalSeconds: Int) {
    var animationPlayed by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animationPlayed = true
    }
    
    val progress by animateFloatAsState(
        targetValue = if (animationPlayed) 0f else 1f,
        animationSpec = tween(
            durationMillis = totalSeconds * 1000,
            easing = LinearEasing
        ),
        label = "Countdown Animation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center, 
        modifier = Modifier
            .size(200.dp)
            .semantics { stateDescription = "SOS dispatches in $seconds seconds" }
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            drawArc(
                color = primaryColor.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = seconds.toString(),
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
