package com.example

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface DigiLockerApiService {
    @FormUrlEncoded
    @POST("public/oauth2/1/token")
    suspend fun getAccessToken(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TokenResponse

    @GET("public/oauth2/1/user")
    suspend fun getUserProfile(
        @Header("Authorization") authorization: String
    ): DigiLockerUserProfile
}

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

@Serializable
data class DigiLockerUserProfile(
    val name: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val bloodGroup: String? = null
)

object DigiLockerApi {
    private const val BASE_URL = "https://api.digitallocker.gov.in/"
    private val json = Json { ignoreUnknownKeys = true }

    val retrofitService: DigiLockerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DigiLockerApiService::class.java)
    }
}
