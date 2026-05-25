package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class LocationInfo(
    val speed: Float? = null,
    val heading: Float? = null,
    val altitude: Double? = null,
    val accuracy: Float? = null
)

@Serializable
data class BatteryInfo(
    val level: Float,
    val status: String,
    val lowPowerMode: Boolean
)

@Serializable
data class SosPayload(
    val id: String? = null,
    val incidentId: String? = null,
    val userId: String? = null,
    val type: String,
    val timestamp: String,
    val source: String,
    val isTelemetry: Boolean,
    val stealthMode: Boolean,
    val latitude: Double,
    val longitude: Double,
    val locationInfo: LocationInfo? = null,
    val battery: BatteryInfo? = null,
    val device: Map<String, String>? = null,
    val network: Map<String, String>? = null,
    val photo: List<String>? = null,
    val audio: List<String>? = null,
    val medicalProfile: MedicalProfile? = null
)

@Serializable
data class MedicalProfile(
    val name: String,
    val age: Int,
    val bloodGroup: String,
    val allergies: List<String>,
    val notes: String
)

interface SosApiService {
    @POST("api/webhook/sos")
    suspend fun dispatchSos(
        @Body payload: SosPayload
    ): okhttp3.ResponseBody
}

object SosRetrofitClient {
    private const val BASE_URL = "https://aapda-seva-dashboard.vercel.app/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: SosApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(SosApiService::class.java)
    }
}
