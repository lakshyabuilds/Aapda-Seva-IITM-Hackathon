package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
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
import com.example.data.AppDatabase
import com.example.data.EmergencyServiceRepository

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = EmergencyServiceRepository(database.emergencyServiceDao())
        val factory = ViewModelFactory(repository)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                
                // Shared Location State
                var sharedLocation by remember { mutableStateOf<Location?>(null) }
                var selectedMapTarget by remember { mutableStateOf<Poi?>(null) }
                
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                
                var triggerSosQueue by remember { mutableStateOf(intent.getBooleanExtra("TRIGGER_SOS", false)) }
                
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
                                    val newLang = if (java.util.Locale.getDefault().language == "hi") "en" else "hi"
                                    LanguageHelper.setLanguage(context, newLang)
                                    val activity = context as? Activity
                                    activity?.finish()
                                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                }) {
                                    Text(if (java.util.Locale.getDefault().language == "hi") "English" else "हिंदी")
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
        Manifest.permission.SEND_SMS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val locationPermissionsState = rememberMultiplePermissionsState(permissionsList)

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var showPermissionRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!locationPermissionsState.allPermissionsGranted) {
            showPermissionRationale = true
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(id = R.string.permissions_required_title)) },
            text = { Text("Aapda Seva needs Location and SMS to send your coordinates rapidly during a crash.") },
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

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            val serviceIntent = Intent(context, SosBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    val noLandmarkFoundText = stringResource(id = R.string.no_landmark_found)
    val landmarkNotAvailableText = stringResource(id = R.string.landmark_not_available)
    val locationPermDeniedText = stringResource(id = R.string.location_permission_denied)
    val locationPermRequiredText = stringResource(id = R.string.location_permission_required)

    DisposableEffect(locationPermissionsState.allPermissionsGranted) {
        var callback: LocationCallback? = null
        if (locationPermissionsState.allPermissionsGranted) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(10000)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    for (loc in p0.locations) {
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
            }

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                addressText = locationPermDeniedText
            }
        } else {
            addressText = locationPermRequiredText
        }

        onDispose {
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
            onClick = { onSosClick() },
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
