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
data class WikipediaResponse(
    val query: WikipediaQuery? = null
)

@Serializable
data class WikipediaQuery(
    val geosearch: List<WikipediaGeosearch>? = null
)

@Serializable
data class WikipediaGeosearch(
    val pageid: Long? = null,
    val title: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
)

interface WikipediaApiService {
    @Headers("User-Agent: AIStudioApp/1.0")
    @GET("w/api.php")
    suspend fun getGeosearch(
        @Query("action") action: String = "query",
        @Query("list") list: String = "geosearch",
        @Query("gscoord") gscoord: String,
        @Query("gsradius") gsradius: Int = 10000,
        @Query("gslimit") gslimit: Int = 20,
        @Query("format") format: String = "json"
    ): WikipediaResponse
}

object WikipediaRetrofitClient {
    private const val BASE_URL = "https://en.wikipedia.org/"

    private val json = Json { ignoreUnknownKeys = true }

    val service: WikipediaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WikipediaApiService::class.java)
    }
}
