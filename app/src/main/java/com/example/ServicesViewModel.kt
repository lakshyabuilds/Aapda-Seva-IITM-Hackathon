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
                repository.clearOldCache()
                val lat = location.latitude
                val lon = location.longitude
                
                val fetchedServices = mutableListOf<EmergencyServiceEntity>()
                
                // Fetch using async/await to run concurrently and prevent blocking Nominatim requests
                val overpassJob = async { fetchOverpassServices(lat, lon) }
                val wikidataJob = async { fetchWikidataServices(lat, lon) }
                
                // Fetch from Nominatim API with delays to prevent HTTP 429 Too Many Requests
                val nominatimJob = async {
                    val list = mutableListOf<EmergencyServiceEntity>()
                    list.addAll(fetchNominatimServices("hospital", lat, lon, "Hospital"))
                    kotlinx.coroutines.delay(1200)
                    list.addAll(fetchNominatimServices("police", lat, lon, "Police Station"))
                    kotlinx.coroutines.delay(1200)
                    list.addAll(fetchNominatimServices("fire station", lat, lon, "Rescue Service"))
                    kotlinx.coroutines.delay(1200)
                    list.addAll(fetchNominatimServices("towing", lat, lon, "Towing Service"))
                    list
                }
                
                // Await results from background queries
                fetchedServices.addAll(overpassJob.await())
                fetchedServices.addAll(wikidataJob.await())
                fetchedServices.addAll(nominatimJob.await())

                // Deduplicate by name and type to avoid clutter
                val uniqueServices = fetchedServices.distinctBy { it.name.lowercase() + it.type }
                
                repository.insertServices(uniqueServices)
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
                  nwr(around:5000, $lat, $lon)["amenity"="hospital"];
                  nwr(around:5000, $lat, $lon)["amenity"="police"];
                  nwr(around:5000, $lat, $lon)["amenity"="fire_station"];
                  nwr(around:5000, $lat, $lon)["emergency"="ambulance_station"];
                  nwr(around:5000, $lat, $lon)["emergency"="water_rescue"];
                  nwr(around:5000, $lat, $lon)["shop"="car_repair"]["service"="towing"];
                  nwr(around:5000, $lat, $lon)["amenity"="clinic"];
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
                        
                        val type = when {
                            amenity == "hospital" || amenity == "clinic" -> "Hospital"
                            amenity == "police" -> "Police Station"
                            amenity == "fire_station" || emergency == "water_rescue" || emergency == "ambulance_station" -> "Rescue Service"
                            towing == "towing" -> "Towing Service"
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

    private suspend fun fetchNominatimServices(query: String, lat: Double, lon: Double, mappedType: String): List<EmergencyServiceEntity> {
        val list = mutableListOf<EmergencyServiceEntity>()
        try {
            val response = NominatimRetrofitClient.service.getServices(
                query = query,
                lat = lat,
                lon = lon
            )
            
            for (item in response) {
                val id = item.osm_id?.toString() ?: continue
                val name = item.name ?: "Unknown Name"
                if (name.isEmpty() || name == "Unknown Name") continue
                
                val locLat = item.lat?.toDoubleOrNull() ?: continue
                val locLon = item.lon?.toDoubleOrNull() ?: continue
                
                val phone = item.extratags?.phone ?: ""
                
                list.add(EmergencyServiceEntity(
                    id = "nom_$id",
                    name = name,
                    type = mappedType,
                    lat = locLat,
                    lon = locLon,
                    phone = phone,
                    source = "Nominatim"
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
    
    private suspend fun fetchWikidataServices(lat: Double, lon: Double): List<EmergencyServiceEntity> {
        val list = mutableListOf<EmergencyServiceEntity>()
        try {
            val query = """
                SELECT ?item ?itemLabel ?lat ?lon ?phone WHERE {
                  SERVICE wikibase:around { 
                    ?item wdt:P625 ?location . 
                    bd:serviceParam wikibase:center "Point($lon $lat)"^^geo:wktLiteral . 
                    bd:serviceParam wikibase:radius "15" . 
                  }
                  { ?item wdt:P31/wdt:P279* wd:Q16917. } UNION { ?item wdt:P31/wdt:P279* wd:Q1322234. }
                  ?item p:P625/psv:P625 ?coord_node .
                  ?coord_node wikibase:geoLatitude ?lat ; wikibase:geoLongitude ?lon .
                  OPTIONAL { ?item wdt:P1329 ?phone . }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "[AUTO_LANGUAGE],en". }
                } LIMIT 50
            """.trimIndent() // Q16917 = hospital, Q1322234 = police station
            
            val response = WikidataRetrofitClient.service.getServices(query = query)
            val bindings = response.results?.bindings
            
            if (bindings != null) {
                for (binding in bindings) {
                    val itemUri = binding.item?.value ?: ""
                    val id = itemUri.substringAfterLast("/")
                    
                    val label = binding.itemLabel?.value ?: "Wikidata Location"
                    val itemLat = binding.lat?.value?.toDoubleOrNull()
                    val itemLon = binding.lon?.value?.toDoubleOrNull()
                    
                    val phone = binding.phone?.value ?: ""
                    
                    // Infer from label since wikidata SPARQL UNION doesn't easily return branch taken
                    val type = if (label.lowercase().contains("police")) "Police Station" else "Hospital"
                    
                    if (itemLat != null && itemLon != null) {
                        list.add(EmergencyServiceEntity(
                            id = "wiki_$id",
                            name = label,
                            type = type,
                            lat = itemLat,
                            lon = itemLon,
                            phone = phone,
                            source = "Wikidata"
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
