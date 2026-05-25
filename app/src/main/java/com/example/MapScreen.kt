package com.example

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

@Composable
fun rememberIsNetworkAvailable(): State<Boolean> {
    val context = LocalContext.current
    val isAvailable = remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isAvailable.value = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isAvailable.value = true
            }

            override fun onLost(network: Network) {
                isAvailable.value = false
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    return isAvailable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    location: Location?,
    targetPoi: Poi? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isOnline by rememberIsNetworkAvailable()
    var selectedPoi by remember(targetPoi) { mutableStateOf<Poi?>(targetPoi) }
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // Configuration is now initialized securely in MainActivity to avoid race conditions.

    LaunchedEffect(location) {
        if (location != null && pois.isEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val fetchedPois = fetchNearbyPois(location.latitude, location.longitude)
                val wikiPois = fetchWikipediaPois(location.latitude, location.longitude)
                val quakePois = fetchEarthquakePois(location.latitude, location.longitude)
                withContext(Dispatchers.Main) {
                    pois = fetchedPois + wikiPois + quakePois
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Map", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (isOnline && location != null) {
                        TextButton(onClick = {
                            val mapView = org.osmdroid.views.MapView(context)
                            mapView.setTileSource(GoogleHybrid)
                            val cacheManager = org.osmdroid.tileprovider.cachemanager.CacheManager(mapView)
                            val bbox = org.osmdroid.util.BoundingBox(
                                location.latitude + 0.05,
                                location.longitude + 0.05,
                                location.latitude - 0.05,
                                location.longitude - 0.05
                            )
                            cacheManager.downloadAreaAsync(context, bbox, 10, 16)
                        }) {
                            Text("SAVE OFFLINE", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val borderColor = if (isOnline) Color.Green else Color.Red
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(4.dp, borderColor, RoundedCornerShape(16.dp))
                    .background(Color.White)
            ) {
                if (location != null) {
                    val mapCenterLat = targetPoi?.lat ?: location.latitude
                    val mapCenterLon = targetPoi?.lon ?: location.longitude

                    OsmMapView(
                        location = location,
                        centerLat = mapCenterLat,
                        centerLon = mapCenterLon,
                        targetPoi = targetPoi,
                        pois = pois,
                        isOnline = isOnline,
                        onPoiClick = { poi ->
                            selectedPoi = poi
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Waiting for location...", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedPoi != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = selectedPoi!!.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "Type: ${selectedPoi!!.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val dist = FloatArray(1)
                        if (location != null) {
                            Location.distanceBetween(
                                location.latitude, location.longitude,
                                selectedPoi!!.lat, selectedPoi!!.lon,
                                dist
                            )
                            val distanceInMeters = dist[0]
                            val distanceText = if (distanceInMeters > 1000) {
                                "${"%.2f".format(distanceInMeters / 1000)} km away"
                            } else {
                                "${distanceInMeters.roundToInt()} meters away"
                            }
                            Text(text = "Distance: $distanceText", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap on a marker to see details",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

val GoogleHybrid = object : OnlineTileSourceBase(
    "GoogleHybrid",
    0, 20, 256, ".png",
    arrayOf("https://mt0.google.com/vt/lyrs=y&hl=en&", "https://mt1.google.com/vt/lyrs=y&hl=en&", "https://mt2.google.com/vt/lyrs=y&hl=en&", "https://mt3.google.com/vt/lyrs=y&hl=en&")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + "x=" + MapTileIndex.getX(pMapTileIndex) +
               "&y=" + MapTileIndex.getY(pMapTileIndex) +
               "&z=" + MapTileIndex.getZoom(pMapTileIndex)
    }
}

@Composable
fun OsmMapView(
    location: Location,
    centerLat: Double,
    centerLon: Double,
    pois: List<Poi>,
    targetPoi: Poi?,
    isOnline: Boolean,
    onPoiClick: (Poi) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setTileSource(GoogleHybrid)
            setUseDataConnection(isOnline) // Crucial for aggressive offline caching
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(centerLat, centerLon))
        }
    }
    
    // Update data connection if online status changes dynamically
    LaunchedEffect(isOnline) {
        mapView.setUseDataConnection(isOnline)
    }

    LaunchedEffect(targetPoi) {
        if (targetPoi != null) {
            mapView.controller.animateTo(GeoPoint(targetPoi.lat, targetPoi.lon))
            mapView.controller.setZoom(17.0)
        }
    }

    val myLocationMarker = remember { Marker(mapView) }
    val targetMarker = remember { Marker(mapView) }
    val poiMarkers = remember { mutableMapOf<Long, Marker>() }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            // Current Location Marker
            myLocationMarker.position = GeoPoint(location.latitude, location.longitude)
            myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            myLocationMarker.title = "You are here"
            if (!map.overlays.contains(myLocationMarker)) {
                map.overlays.add(myLocationMarker)
            }
            
            // Target POI Marker if provided
            if (targetPoi != null && pois.none { it.id == targetPoi.id }) {
                targetMarker.position = GeoPoint(targetPoi.lat, targetPoi.lon)
                targetMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                targetMarker.title = targetPoi.name
                targetMarker.setOnMarkerClickListener { _, _ ->
                    onPoiClick(targetPoi)
                    true
                }
                if (!map.overlays.contains(targetMarker)) {
                    map.overlays.add(targetMarker)
                }
            } else {
                map.overlays.remove(targetMarker)
            }

            // POI Markers Diff Logic
            val newPoiIds = pois.map { it.id }.toSet()
            val existingPoiIds = poiMarkers.keys.toSet()

            // Remove old markers
            val idsToRemove = existingPoiIds - newPoiIds
            idsToRemove.forEach { id ->
                poiMarkers.remove(id)?.let { map.overlays.remove(it) }
            }

            // Add new markers
            val idsToAdd = newPoiIds - existingPoiIds
            idsToAdd.forEach { id ->
                val poi = pois.first { it.id == id }
                val poiMarker = Marker(map)
                poiMarker.position = GeoPoint(poi.lat, poi.lon)
                poiMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                poiMarker.title = poi.name
                poiMarker.setOnMarkerClickListener { _, _ ->
                    onPoiClick(poi)
                    true
                }
                poiMarkers[id] = poiMarker
                map.overlays.add(poiMarker)
            }
            
            map.invalidate()
        }
    )
}

data class Poi(val id: Long, val name: String, val lat: Double, val lon: Double, val type: String)

private suspend fun fetchNearbyPois(lat: Double, lon: Double): List<Poi> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val pois = mutableListOf<Poi>()
    val client = okhttp3.OkHttpClient()
    
    try {
        val query = """
            [out:json][timeout:25];
            (
              node["amenity"="hospital"](around:5000, $lat, $lon);
              node["amenity"="clinic"](around:5000, $lat, $lon);
              node["amenity"="police"](around:5000, $lat, $lon);
              node["amenity"="fire_station"](around:5000, $lat, $lon);
            );
            out body;
        """.trimIndent()
        
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val requestBody = "data=$query".toRequestBody(mediaType)
        
        val request = okhttp3.Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(requestBody)
            .build()
            
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseData = response.body?.string()
                if (responseData != null) {
                    val jsonObject = org.json.JSONObject(responseData)
                    val elements = jsonObject.optJSONArray("elements")
                    
                    if (elements != null) {
                        for (i in 0 until elements.length()) {
                            val element = elements.getJSONObject(i)
                            val id = element.optLong("id")
                            
                            val locLat = element.optDouble("lat", Double.NaN)
                            val locLon = element.optDouble("lon", Double.NaN)
                            
                            val tags = element.optJSONObject("tags")
                            if (tags != null && !locLat.isNaN() && !locLon.isNaN()) {
                                val name = tags.optString("name", "Unknown Service")
                                var type = tags.optString("amenity", "")
                                if (type.isEmpty()) type = "emergency"
                                
                                pois.add(Poi(id, name, locLat, locLon, type))
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext pois
}

private fun fetchWikipediaPois(lat: Double, lon: Double): List<Poi> {
    val pois = mutableListOf<Poi>()
    try {
        val response = kotlinx.coroutines.runBlocking {
            WikipediaRetrofitClient.service.getGeosearch(gscoord = "$lat|$lon")
        }
        val geosearch = response.query?.geosearch
        
        if (geosearch != null) {
            for (item in geosearch) {
                val pageId = item.pageid ?: continue
                val title = item.title ?: "Wikipedia Article"
                val itemLat = item.lat ?: continue
                val itemLon = item.lon ?: continue
                
                pois.add(Poi(pageId, title, itemLat, itemLon, "Wikipedia Article"))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return pois
}

private fun fetchEarthquakePois(lat: Double, lon: Double): List<Poi> {
    val pois = mutableListOf<Poi>()
    try {
        val url = URL("https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=$lat&longitude=$lon&maxradiuskm=500&limit=15")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            val features = jsonObject.optJSONArray("features")
            
            if (features != null) {
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val properties = feature.optJSONObject("properties")
                    val geometry = feature.optJSONObject("geometry")
                    
                    if (properties != null && geometry != null) {
                        val coords = geometry.optJSONArray("coordinates")
                        val title = properties.optString("title", "Earthquake")
                        val magnitude = properties.optDouble("mag", 0.0)
                        val typeStr = "Earthquake (Mag %.1f)".format(magnitude)
                        
                        if (coords != null && coords.length() >= 2) {
                            val locLon = coords.optDouble(0, Double.NaN)
                            val locLat = coords.optDouble(1, Double.NaN)
                            
                            if (!locLat.isNaN() && !locLon.isNaN()) {
                                // Generate a synthetic ID
                                val id = title.hashCode().toLong()
                                pois.add(Poi(id, title, locLat, locLon, typeStr))
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return pois
}

