package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement>
)

@Serializable
data class OverpassElement(
    val id: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: OverpassTags? = null
)

@Serializable
data class OverpassCenter(
    val lat: Double? = null,
    val lon: Double? = null
)

@Serializable
data class OverpassTags(
    val name: String? = null,
    val amenity: String? = null,
    val shop: String? = null,
    val phone: String? = null,
    val emergency: String? = null,
    val office: String? = null
)

interface OverpassApiService {
    @GET("api/interpreter")
    suspend fun getServices(
        @Query("data") data: String
    ): OverpassResponse
}

object OverpassRetrofitClient {
    private const val BASE_URL = "https://overpass-api.de/"

    private val json = Json { ignoreUnknownKeys = true }

    val service: OverpassApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OverpassApiService::class.java)
    }
}
