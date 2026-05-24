package com.example

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface OverpassApiService {
    @GET("api/interpreter")
    suspend fun getServices(@Query("data") query: String): ResponseBody
}

object OverpassRetrofitClient {
    private const val BASE_URL = "https://overpass-api.de/"

    val service: OverpassApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .build()
            .create(OverpassApiService::class.java)
    }
}
