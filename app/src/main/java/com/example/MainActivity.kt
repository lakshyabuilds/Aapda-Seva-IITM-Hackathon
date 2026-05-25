package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import android.content.Intent
import com.example.SosBackgroundService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.data.AppDatabase
import com.example.data.EmergencyServiceRepository
import android.preference.PreferenceManager
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            Configuration.getInstance().userAgentValue = packageName
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Schedule offline incident sync
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        try {
            val periodicSyncWork = PeriodicWorkRequestBuilder<IncidentSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "PeriodicIncidentSync",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncWork
            )

            // Try immediate sync if internet is available
            val singleSyncWork = OneTimeWorkRequestBuilder<IncidentSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "ImmediateIncidentSync",
                ExistingWorkPolicy.REPLACE,
                singleSyncWork
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = EmergencyServiceRepository(database.emergencyServiceDao())
        val factory = ViewModelFactory(repository)

        enableEdgeToEdge()
        setContent {
            val initialLang = if (java.util.Locale.getDefault().language == "hi") "hi" else "en"
            var currentLanguage by remember { mutableStateOf(initialLang) }

            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val locale = remember(currentLanguage) { java.util.Locale(currentLanguage) }
            val configuration = remember(currentLanguage, baseContext.resources.configuration) {
                android.content.res.Configuration(baseContext.resources.configuration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }

            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides configuration
            ) {
                MyApplicationTheme {
                    val navController = rememberNavController()
                
                // Shared Location State
                var sharedLocation by remember { mutableStateOf<Location?>(null) }
                var selectedMapTarget by remember { mutableStateOf<Poi?>(null) }
                
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                
                var triggerSosQueue by remember { mutableStateOf(intent.getBooleanExtra("TRIGGER_SOS", false)) }
                
                LaunchedEffect(Unit) {
                    if (intent.hasExtra("LAST_LAT") && intent.hasExtra("LAST_LON")) {
                        val loc = Location("")
                        loc.latitude = intent.getDoubleExtra("LAST_LAT", 0.0)
                        loc.longitude = intent.getDoubleExtra("LAST_LON", 0.0)
                        sharedLocation = loc
                    }
                }
                
                // We use a side-effect channel to prevent multiple triggers from recompositions
                LaunchedEffect(triggerSosQueue) {
                    if (triggerSosQueue) {
                        intent.removeExtra("TRIGGER_SOS")
                        triggerSosQueue = false
                        navController.navigate("sos_confirm") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                        }
                    }
                }
                
                // Expose a method to handle new intents instead of trusting recomposition of 'intent' alone
                DisposableEffect(Unit) {
                    val listener = androidx.core.util.Consumer<Intent> { newIntent ->
                        if (newIntent.getBooleanExtra("TRIGGER_SOS", false)) {
                            if (newIntent.hasExtra("LAST_LAT") && newIntent.hasExtra("LAST_LON")) {
                                val loc = Location("")
                                loc.latitude = newIntent.getDoubleExtra("LAST_LAT", 0.0)
                                loc.longitude = newIntent.getDoubleExtra("LAST_LON", 0.0)
                                sharedLocation = loc
                            }
                            triggerSosQueue = true
                        }
                    }
                    addOnNewIntentListener(listener)
                    onDispose {
                        removeOnNewIntentListener(listener)
                    }
                }

                @OptIn(ExperimentalMaterial3Api::class)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(id = R.string.app_name)) },
                            actions = {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                TextButton(onClick = {
                                    val newLang = if (currentLanguage == "hi") "en" else "hi"
                                    LanguageHelper.setLanguage(context, newLang)
                                    currentLanguage = newLang
                                }) {
                                    Text(if (currentLanguage == "hi") "English" else "हिंदी")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Warning, contentDescription = stringResource(id = R.string.nav_sos)) },
                                label = { Text(stringResource(id = R.string.nav_sos)) },
                                selected = currentRoute == "sos_screen",
                                onClick = {
                                    navController.navigate("sos_screen") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.LocationOn, contentDescription = stringResource(id = R.string.nav_map)) },
                                label = { Text(stringResource(id = R.string.nav_map)) },
                                selected = currentRoute == "map_screen",
                                onClick = {
                                    navController.navigate("map_screen") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Person, contentDescription = stringResource(id = R.string.nav_contacts)) },
                                label = { Text(stringResource(id = R.string.nav_contacts)) },
                                selected = currentRoute == "contacts_screen",
                                onClick = {
                                    navController.navigate("contacts_screen") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.List, contentDescription = stringResource(id = R.string.nav_services)) },
                                label = { Text(stringResource(id = R.string.nav_services)) },
                                selected = currentRoute == "services_screen",
                                onClick = {
                                    navController.navigate("services_screen") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Mic, contentDescription = stringResource(id = R.string.nav_ai_help)) },
                                label = { Text(stringResource(id = R.string.nav_ai_help)) },
                                selected = currentRoute == "ai_help_screen",
                                onClick = {
                                    navController.navigate("ai_help_screen") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController, 
                        startDestination = "sos_screen",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("sos_screen") {
                            SOSAppContent(
                                modifier = Modifier.fillMaxSize(),
                                sharedLocation = sharedLocation,
                                onLocationUpdate = { sharedLocation = it },
                                onOpenMap = { navController.navigate("map_screen") },
                                onSosClick = { navController.navigate("sos_confirm") }
                            )
                        }
                        composable("sos_confirm") {
                            SosConfirmScreen(
                                location = sharedLocation,
                                onCancel = { navController.popBackStack() },
                                onFinish = {
                                    navController.popBackStack() 
                                }
                            )
                        }
                        composable("map_screen") {
                            MapScreen(
                                location = sharedLocation,
                                targetPoi = selectedMapTarget,
                                onNavigateBack = { 
                                    selectedMapTarget = null 
                                    navController.popBackStack() 
                                }
                            )
                        }
                        composable("contacts_screen") {
                            ContactsScreen(
                                location = sharedLocation,
                                onOpenProfile = { navController.navigate("profile_screen") }
                            )
                        }
                        composable("services_screen") {
                            val viewModel: ServicesViewModel = viewModel(factory = factory)
                            ServicesScreen(
                                location = sharedLocation,
                                viewModel = viewModel,
                                onViewMap = { lat, lon, name ->
                                    selectedMapTarget = Poi(
                                        id = name.hashCode().toLong(),
                                        name = name,
                                        lat = lat,
                                        lon = lon,
                                        type = "Service"
                                    )
                                    navController.navigate("map_screen")
                                }
                            )
                        }
                        composable("ai_help_screen") {
                            AiHelpScreen(location = sharedLocation)
                        }
                        composable("profile_screen") {
                            ProfileScreen()
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SOSAppContent(
    modifier: Modifier = Modifier,
    sharedLocation: Location?,
    onLocationUpdate: (Location) -> Unit,
    onOpenMap: () -> Unit,
    onSosClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var location by remember { mutableStateOf(sharedLocation) }
    var addressText by remember { mutableStateOf("") }
    val fetchingLocText = stringResource(id = R.string.fetching_location)
    LaunchedEffect(Unit) {
        addressText = fetchingLocText
    }

    val permissionsList = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val locationPermissionsState = rememberMultiplePermissionsState(permissionsList)

    val backgroundLocationState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        null
    }

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var showBackgroundPermissionRationale by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!locationPermissionsState.allPermissionsGranted && !locationPermissionsState.shouldShowRationale) {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    val hasLocationPerm = locationPermissionsState.permissions.any {
        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION || 
         it.permission == Manifest.permission.ACCESS_COARSE_LOCATION) && 
        it.status.isGranted
    }

    val hasNotificationPerm = notificationPermissionState?.status?.isGranted != false

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(id = R.string.permissions_required_title)) },
            text = { Text("Aapda Seva needs Location, SMS, Camera, and Microphone to send coordinates, capture emergency photos and audio during an SOS event.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        locationPermissionsState.launchMultiplePermissionRequest()
                    }
                ) {
                    Text(stringResource(id = R.string.agree))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionRationale = false }
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showBackgroundPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundPermissionRationale = false },
            title = { Text(stringResource(id = R.string.bg_permission_required_title)) },
            text = { Text(stringResource(id = R.string.bg_permission_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundPermissionRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = android.net.Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                        } else {
                            backgroundLocationState?.launchPermissionRequest()
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackgroundPermissionRationale = false }
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("Notification Permission Required") },
            text = { Text("Aapda Seva requires Notification permission to reliably run the emergency background service. Without it, the service may fail to start on modern devices. Please enable it in Settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = android.net.Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(id = R.string.settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotificationRationale = false }
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(hasLocationPerm, hasNotificationPerm) {
        if (hasLocationPerm && hasNotificationPerm) {
            val serviceIntent = Intent(context, SosBackgroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val noLandmarkFoundText = stringResource(id = R.string.no_landmark_found)
    val landmarkNotAvailableText = stringResource(id = R.string.landmark_not_available)
    val locationPermDeniedText = stringResource(id = R.string.location_permission_denied)
    val locationPermRequiredText = stringResource(id = R.string.location_permission_required)

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(hasLocationPerm, lifecycleOwner) {
        var callback: LocationCallback? = null
        
        val legacyLocationListener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                val currentBest = location
                var isBetter = false
                if (currentBest == null) {
                    isBetter = true
                } else {
                    val timeDelta = loc.time - currentBest.time
                    val isSignificantlyNewer = timeDelta > 60000
                    val isSignificantlyOlder = timeDelta < -60000
                    val isNewer = timeDelta > 0

                    if (isSignificantlyNewer) {
                        isBetter = true
                    } else if (!isSignificantlyOlder) {
                        val accuracyDelta = (loc.accuracy - currentBest.accuracy).toInt()
                        val isLessAccurate = accuracyDelta > 0
                        val isMoreAccurate = accuracyDelta < 0
                        val isSignificantlyLessAccurate = accuracyDelta > 200

                        if (isMoreAccurate) {
                            isBetter = true
                        } else if (isNewer && !isLessAccurate) {
                            isBetter = true
                        } else if (isNewer && !isSignificantlyLessAccurate && loc.provider == currentBest.provider) {
                            isBetter = true
                        }
                    }
                }
                
                if (isBetter) {
                    location = loc
                    onLocationUpdate(loc)
                    coroutineScope.launch(Dispatchers.IO) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                geocoder.getFromLocation(loc.latitude, loc.longitude, 1) { addresses ->
                                    if (addresses.isNotEmpty()) {
                                        val address = addresses[0]
                                        val landmark = address.featureName ?: address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
                                        val translated = address.getAddressLine(0) ?: landmark
                                        addressText = translated
                                    } else {
                                        addressText = noLandmarkFoundText
                                    }
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                if (addresses != null && addresses.isNotEmpty()) {
                                    val address = addresses[0]
                                    val landmark = address.featureName ?: address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
                                    val translated = address.getAddressLine(0) ?: landmark
                                    withContext(Dispatchers.Main) { addressText = translated }
                                } else {
                                    withContext(Dispatchers.Main) { addressText = noLandmarkFoundText }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { addressText = landmarkNotAvailableText }
                        }
                    }
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (hasLocationPerm) {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setWaitForAccurateLocation(false)
                        .setMinUpdateIntervalMillis(2000)
                        .setMaxUpdateDelayMillis(10000)
                        .build()

                    callback = object : LocationCallback() {
                        override fun onLocationResult(p0: LocationResult) {
                            for (loc in p0.locations) {
                                legacyLocationListener.onLocationChanged(loc)
                            }
                        }
                    }

                    try {
                        @Suppress("MissingPermission")
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) legacyLocationListener.onLocationChanged(loc)
                        }
                        @Suppress("MissingPermission")
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            callback!!,
                            Looper.getMainLooper()
                        )
                    } catch (e: SecurityException) {
                        addressText = locationPermDeniedText
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    addressText = locationPermRequiredText
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                callback?.let {
                    fusedLocationClient.removeLocationUpdates(it)
                    callback = null
                }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            callback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Title
        Text(
            text = stringResource(id = R.string.title_aapda_seva),
            fontSize = 28.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Center Button
        Button(
            onClick = {
                if (!hasLocationPerm) {
                    showPermissionRationale = true
                } else if (!hasNotificationPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showNotificationRationale = true
                } else if (backgroundLocationState != null && backgroundLocationState.status.isGranted == false) {
                    showBackgroundPermissionRationale = true
                } else {
                    onSosClick()
                }
            },
            modifier = Modifier
                .size(240.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp, pressedElevation = 4.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.sos_button),
                    fontSize = 36.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Location Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val unk = stringResource(id = R.string.unknown_coordinates)
                val formatText = stringResource(id = R.string.lat_lon_format)
                val locText = location?.let { 
                    String.format(formatText, "%.4f".format(it.latitude), "%.4f".format(it.longitude))
                } ?: unk

                Text(
                    text = locText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = addressText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
