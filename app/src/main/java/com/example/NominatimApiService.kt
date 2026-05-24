package com.example

import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

@Serializable
data class NominatimResponse(
    val osm_id: Long? = null,
    val name: String? = null,
    val lat: String? = null,
    val lon: String? = null,
    val extratags: NominatimExtratags? = null
)

@Serializable
data class NominatimExtratags(
    val phone: String? = null
)

interface NominatimApiService {
    @Headers("User-Agent: EmergencyServicesApp")
    @GET("search")
    suspend fun getServices(
        @Query("format") format: String = "json",
        @Query("q") query: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int = 5,
        @Query("extratags") extratags: Int = 1
    ): List<NominatimResponse>
}

object NominatimRetrofitClient {
    private const val BASE_URL = "https://nominatim.openstreetmap.org/"

    private val json = Json { ignoreUnknownKeys = true }

    val service: NominatimApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NominatimApiService::class.java)
    }
}
