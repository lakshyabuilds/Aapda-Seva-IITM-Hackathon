package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Serializable
data class NvidiaChatRequest(
    val model: String,
    val messages: List<NvidiaMessage>,
    val max_tokens: Int = 1024,
    val temperature: Double = 0.15,
    val top_p: Double = 1.0,
    val frequency_penalty: Double = 0.0,
    val presence_penalty: Double = 0.0,
    val stream: Boolean = false
)

@Serializable
data class NvidiaMessage(
    val role: String,
    val content: String
)

@Serializable
data class NvidiaChatResponse(
    val choices: List<NvidiaChoice>? = null
)

@Serializable
data class NvidiaChoice(
    val message: NvidiaMessage? = null
)

interface NvidiaApiService {
    @POST("v1/chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: NvidiaChatRequest
    ): NvidiaChatResponse
}

object NvidiaRetrofitClient {
    private const val BASE_URL = "https://integrate.api.nvidia.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: NvidiaApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(NvidiaApiService::class.java)
    }
}

suspend fun generateAiHelpResponse(
    prompt: String,
    locationText: String,
    fallbackError: String,
    fallbackEmpty: String
): String = withContext(Dispatchers.IO) {
    val systemPrompt = "You are 'Aapda Seva AI'. " +
            "You must interpret the emergency, and respond in the purer version of the same language the user asked in. " +
            "STRICTLY follow these rules: " +
            "1. Answer in max 1 line. " +
            "2. DO NOT use any formatting, like asterisks, bold, or lists. " +
            "3. Be direct and give simple, clear instructions. " +
            "4. Always prepend: '⚠️ Call local emergency services. '\n" +
            "The user's current location is: $locationText"

    val request = NvidiaChatRequest(
        model = "mistralai/mistral-large-3-675b-instruct-2512",
        messages = listOf(
            NvidiaMessage(role = "system", content = systemPrompt),
            NvidiaMessage(role = "user", content = prompt)
        ),
        stream = false
    )
    try {
        val apiKey = BuildConfig.NVIDIA_API_KEY
        val authHeader = "Bearer $apiKey"
        val response = NvidiaRetrofitClient.service.generateContent(authHeader, request)
        response.choices?.firstOrNull()?.message?.content ?: fallbackEmpty
    } catch (e: Exception) {
        fallbackError
    }
}
