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
data class WikidataResponse(
    val results: WikidataResults? = null
)

@Serializable
data class WikidataResults(
    val bindings: List<WikidataBinding>? = null
)

@Serializable
data class WikidataBinding(
    val item: WikidataValue? = null,
    val itemLabel: WikidataValue? = null,
    val lat: WikidataValue? = null,
    val lon: WikidataValue? = null,
    val phone: WikidataValue? = null
)

@Serializable
data class WikidataValue(
    val value: String? = null
)

interface WikidataApiService {
    @Headers(
        "Accept: application/sparql-results+json",
        "User-Agent: EmergencyApp/1.0"
    )
    @GET("sparql")
    suspend fun getServices(
        @Query("query") query: String,
        @Query("format") format: String = "json"
    ): WikidataResponse
}

object WikidataRetrofitClient {
    private const val BASE_URL = "https://query.wikidata.org/"

    private val json = Json { ignoreUnknownKeys = true }

    val service: WikidataApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WikidataApiService::class.java)
    }
}
