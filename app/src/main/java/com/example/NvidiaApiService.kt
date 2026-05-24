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

suspend fun generateAiHelpResponse(prompt: String, locationText: String): String = withContext(Dispatchers.IO) {
    val systemPrompt = "You are 'Aapda Seva AI' - an emergency medical first-aid helper for India. " +
            "The user might write in Hindi, broken English, phonetic Hinglish (e.g., 'khoon beh rha hai', 'chot lag gayi', 'saanp kaat liya'), or regional slangs. " +
            "You must always interpret the distress, translate it internally, and respond ONLY in highly simplified Hindi (हिंदी). " +
            "Use clear, concise, step-by-step bullet points for medical emergency instructions. " +
            "Do not use complex medical terminology. Keep your tone highly calm, direct, and comforting. " +
            "Every step must be direct and easy for a non-reader to follow. " +
            "Always prepend: '⚠️ DISCLAIMER: यह केवल प्राथमिक चिकित्सा मार्गदर्शन है। तुरंत 112 डायल करें।'\n" +
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
        val authHeader = "Bearer ${BuildConfig.NVIDIA_API_KEY}"
        val response = NvidiaRetrofitClient.service.generateContent(authHeader, request)
        response.choices?.firstOrNull()?.message?.content ?: "क्षमा करें, मैं अभी आपकी सहायता करने में असमर्थ हूँ।"
    } catch (e: Exception) {
        "त्रुटि: इंटरनेट कनेक्शन नहीं मिल रहा है। कृपया ऑफलाइन विकल्पों का उपयोग करें या तुरंत 112 डायल करें।"
    }
}
