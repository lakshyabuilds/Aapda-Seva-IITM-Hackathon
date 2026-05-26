package com.example

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EmergencyServiceEntity
import com.example.data.EmergencyServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ServicesViewModel(private val repository: EmergencyServiceRepository) : ViewModel() {

    private val _currentFilter = MutableStateFlow("All")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()
    
    private val _selectedRadius = MutableStateFlow(15000)
    val selectedRadius: StateFlow<Int> = _selectedRadius.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setFilter(filter: String) {
        _currentFilter.value = filter
    }
    
    fun setRadius(radius: Int) {
        _selectedRadius.value = radius
        lastFetchedLocation?.let { fetchServicesIgnoreCache(it) }
    }

    private val _lastLocation = MutableStateFlow<Location?>(null)
    private var lastFetchedLocation: Location?
        get() = _lastLocation.value
        set(value) {
            _lastLocation.value = value
        }

    // Combine Room flow based on filter
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val services: StateFlow<List<EmergencyServiceEntity>> = combine(
        _currentFilter.flatMapLatest { filter ->
            if (filter == "All") {
                repository.allServices
            } else {
                repository.getServicesByType(filter)
            }
        },
        _lastLocation
    ) { list, location ->
        if (location == null) {
            list
        } else {
            list.sortedBy { service ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude,
                    service.lat, service.lon,
                    results
                )
                results[0]
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun fetchNearbyServices(location: Location) {
        fetchServicesIgnoreCache(location, force = false)
    }

    fun forceFetchNearbyServices(location: Location) {
        fetchServicesIgnoreCache(location, force = true)
    }

    private fun fetchServicesIgnoreCache(location: Location, force: Boolean = true) {
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            android.util.Log.e("ServicesViewModel", "Fetch aborted: Location is 0.0, 0.0")
            return
        }

        if (_isLoading.value) return
        if (!force) {
            lastFetchedLocation?.let {
                if (it.distanceTo(location) < 100f) {
                    android.util.Log.d("ServicesViewModel", "Fetch skipped: distance < 100m")
                    return // Less than 100m radius change, don't re-fetch
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val lat = location.latitude
                val lon = location.longitude
                android.util.Log.d("ServicesViewModel", "Fetching services for lat: $lat, lon: $lon, radius: ${_selectedRadius.value}")
                
                val fetchedServices = fetchOverpassServices(lat, lon, _selectedRadius.value)
                android.util.Log.d("ServicesViewModel", "Overpass returned ${fetchedServices.size} raw services")

                // Spatial deduplication: Group POIs that refer to the same physical entity
                val uniqueServices = mutableListOf<EmergencyServiceEntity>()
                for (service in fetchedServices) {
                    val isDuplicate = uniqueServices.any { existing ->
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            service.lat, service.lon,
                            existing.lat, existing.lon,
                            results
                        )
                        val distance = results[0]
                        val sameType = service.type == existing.type
                        val sameName = service.name.equals(existing.name, ignoreCase = true)
                        
                        (sameType && distance < 50f) || (sameType && sameName && distance < 500f)
                    }
                    if (!isDuplicate) {
                        uniqueServices.add(service)
                    }
                }
                
                android.util.Log.d("ServicesViewModel", "After deduplication, kept ${uniqueServices.size} services")

                if (uniqueServices.isNotEmpty()) {
                    repository.clearAllCache() // Clear only after success with data
                    repository.insertServices(uniqueServices)
                    lastFetchedLocation = Location(location) // Copy location only on success
                } else {
                    android.util.Log.w("ServicesViewModel", "0 services found after deduplication. Keeping old cache.")
                    lastFetchedLocation = Location(location) // Still update location so we don't spam fetch for a genuinely empty area
                }
            } catch (e: Exception) {
                android.util.Log.e("ServicesViewModel", "Fetch failed completely", e)
                lastFetchedLocation = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchOverpassServices(lat: Double, lon: Double, radius: Int): List<EmergencyServiceEntity> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val allResults = mutableListOf<EmergencyServiceEntity>()

            try {
                val results = executeOverpassQuery(lat, lon, radius)
                allResults.addAll(results)
            } catch (e: Exception) {
                android.util.Log.e("ServicesViewModel", "Failed query at radius $radius", e)
                throw e
            }
            allResults
        }
    }

    private fun executeOverpassQuery(lat: Double, lon: Double, radius: Int): List<EmergencyServiceEntity> {
        val query = """
[out:json][timeout:25];
(
  node["amenity"~"hospital|clinic"](around:$radius, $lat, $lon);
  way["amenity"~"hospital|clinic"](around:$radius, $lat, $lon);
  node["amenity"="police"](around:$radius, $lat, $lon);
  way["amenity"="police"](around:$radius, $lat, $lon);
  node["emergency"="ambulance_station"](around:$radius, $lat, $lon);
  way["emergency"="ambulance_station"](around:$radius, $lat, $lon);
  node["shop"~"car_repair|motorcycle_repair|tyres"](around:$radius, $lat, $lon);
  way["shop"~"car_repair|motorcycle_repair|tyres"](around:$radius, $lat, $lon);
  node["amenity"="fuel"](around:$radius, $lat, $lon);
  way["amenity"="fuel"](around:$radius, $lat, $lon);
);
out center tags;
        """.trimIndent()

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://overpass.osm.ch/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        var lastException: Exception? = null
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        for (url in endpoints) {
            try {
                val reqBody = okhttp3.FormBody.Builder()
                    .add("data", query)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .header("User-Agent", "AapdaSeva/1.0 contact@aapdaseva.com")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val inputStream = response.body!!.byteStream()
                    val reader = android.util.JsonReader(java.io.InputStreamReader(inputStream, "UTF-8"))
                    return parseOverpassStream(reader)
                } else {
                    android.util.Log.e("ServicesViewModel", "Error fetching from $url code ${response.code}")
                    lastException = Exception("HTTP error code: ${response.code}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ServicesViewModel", "Exception with $url", e)
                lastException = e
            }
        }

        throw lastException ?: Exception("All endpoints failed")
    }

    private fun parseOverpassStream(reader: android.util.JsonReader): List<EmergencyServiceEntity> {
        val list = mutableListOf<EmergencyServiceEntity>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "elements") {
                reader.beginArray()
                while (reader.hasNext()) {
                    val entity = parseElement(reader)
                    if (entity != null) {
                        list.add(entity)
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        reader.close()
        return list
    }

    private fun parseElement(reader: android.util.JsonReader): EmergencyServiceEntity? {
        var id = java.util.UUID.randomUUID().toString()
        var lat: Double? = null
        var lon: Double? = null
        var nameTag = ""
        var phoneTag = ""
        var amenity = ""
        var shop = ""
        var emergency = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextLong().toString()
                "lat" -> lat = reader.nextDouble()
                "lon" -> lon = reader.nextDouble()
                "center" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "lat" -> lat = reader.nextDouble()
                            "lon" -> lon = reader.nextDouble()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "tags" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (val tagKey = reader.nextName()) {
                            "name" -> nameTag = reader.nextString()
                            "name:en" -> if (nameTag.isEmpty()) nameTag = reader.nextString() else reader.skipValue()
                            "amenity" -> amenity = reader.nextString()
                            "shop" -> shop = reader.nextString()
                            "emergency" -> emergency = reader.nextString()
                            "phone" -> phoneTag = reader.nextString()
                            "contact:phone" -> if (phoneTag.isEmpty()) phoneTag = reader.nextString() else reader.skipValue()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (lat == null || lon == null) return null

        val type = when {
            amenity == "hospital" || amenity == "clinic" -> "Hospital"
            amenity == "police" -> "Police Station"
            emergency == "ambulance_station" -> "Rescue Service"
            shop == "car_repair" || shop == "tyres" || shop == "motorcycle_repair" -> "Puncture Shop"
            amenity == "fuel" -> "Fuel/Mechanic"
            else -> {
                android.util.Log.d("ServicesViewModel", "Skipped element $id: amenity='$amenity', shop='$shop', emergency='$emergency'")
                return null
            }
        }

        val finalName = if (nameTag.isNotEmpty()) nameTag else type

        return EmergencyServiceEntity(
            id = "overpass_$id",
            name = finalName,
            type = type,
            lat = lat,
            lon = lon,
            phone = phoneTag,
            source = "Overpass"
        )
    }
}
