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

class ServicesViewModel(private val repository: EmergencyServiceRepository) : ViewModel() {

    private val _currentFilter = MutableStateFlow("All")
    val currentFilter: StateFlow<String> = _currentFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setFilter(filter: String) {
        _currentFilter.value = filter
    }

    // Combine Room flow based on filter
    val services: StateFlow<List<EmergencyServiceEntity>> = _currentFilter
        .flatMapLatest { filter ->
            if (filter == "All") {
                repository.allServices
            } else {
                repository.getServicesByType(filter)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun fetchNearbyServices(location: Location) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val lat = location.latitude
                val lon = location.longitude
                
                val fetchedServices = mutableListOf<EmergencyServiceEntity>()
                
                fetchedServices.addAll(fetchOverpassServices(lat, lon))

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
                        
                        // Same POI if they are the exact same type and < 150m apart,
                        // OR if they share the same name and type and are < 2000m apart
                        (sameType && distance < 150f) || (sameType && sameName && distance < 2000f)
                    }
                    if (!isDuplicate) {
                        uniqueServices.add(service)
                    }
                }
                
                if (uniqueServices.isNotEmpty()) {
                    // Important: only clear cache if we successfully retrieved new POIs
                    repository.clearAllCache()
                    repository.insertServices(uniqueServices)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchOverpassServices(lat: Double, lon: Double): List<EmergencyServiceEntity> {
        val list = mutableListOf<EmergencyServiceEntity>()
        try {
            val query = """
                [out:json][timeout:25];
                (
                  nwr(around:15000, $lat, $lon)["amenity"="hospital"];
                  nwr(around:15000, $lat, $lon)["amenity"="clinic"];
                  nwr(around:15000, $lat, $lon)["amenity"="doctors"];
                  nwr(around:15000, $lat, $lon)["amenity"="pharmacy"];
                  nwr(around:15000, $lat, $lon)["amenity"="police"];
                  nwr(around:15000, $lat, $lon)["amenity"="fire_station"];
                  nwr(around:15000, $lat, $lon)["emergency"="ambulance_station"];
                  nwr(around:15000, $lat, $lon)["emergency"="water_rescue"];
                  nwr(around:15000, $lat, $lon)["shop"="car_repair"];
                  nwr(around:15000, $lat, $lon)["shop"="tyres"];
                  nwr(around:15000, $lat, $lon)["shop"="car"];
                  nwr(around:15000, $lat, $lon)["shop"="motorcycle"];
                );
                out center;
            """.trimIndent()
            
            val responseBody = OverpassRetrofitClient.service.getServices(query)
            val responseString = responseBody.string()
            val jsonObject = JSONObject(responseString)
            val elements = jsonObject.optJSONArray("elements")
            
            if (elements != null) {
                for (i in 0 until elements.length()) {
                        val element = elements.getJSONObject(i)
                        val id = element.optLong("id").toString()
                        val tags = element.optJSONObject("tags") ?: continue
                        
                        val name = tags.optString("name", "Unknown Service")
                        
                        var locLat = element.optDouble("lat", Double.NaN)
                        var locLon = element.optDouble("lon", Double.NaN)
                        
                        if (locLat.isNaN() || locLon.isNaN()) {
                            val center = element.optJSONObject("center")
                            if (center != null) {
                                locLat = center.optDouble("lat", Double.NaN)
                                locLon = center.optDouble("lon", Double.NaN)
                            }
                        }
                        
                        if (locLat.isNaN() || locLon.isNaN()) continue
                        
                        val amenity = tags.optString("amenity")
                        val emergency = tags.optString("emergency")
                        val towing = tags.optString("service")
                        val shop = tags.optString("shop")
                        
                        val type = when {
                            amenity == "hospital" || amenity == "clinic" || amenity == "doctors" || amenity == "pharmacy" -> "Hospital"
                            amenity == "police" -> "Police Station"
                            amenity == "fire_station" || emergency == "water_rescue" || emergency == "ambulance_station" -> "Rescue Service"
                            towing == "towing" -> "Towing Service"
                            shop == "tyres" || shop == "car_repair" -> "Puncture Shop"
                            shop == "car" || shop == "motorcycle" -> "Vehicle Showroom"
                            else -> "Other"
                        }
                        
                        val phone = tags.optString("phone", tags.optString("contact:phone", ""))
                        
                        list.add(EmergencyServiceEntity(
                            id = "overpass_$id",
                            name = name,
                            type = type,
                            lat = locLat,
                            lon = locLon,
                            phone = phone,
                            source = "Overpass"
                        ))
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
