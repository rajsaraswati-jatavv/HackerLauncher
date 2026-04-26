package com.hackerlauncher.chat

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class ChatRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<ChatMessage>,
    val max_tokens: Int = 1024,
    val temperature: Double = 0.7
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String = "",
    val choices: List<Choice> = emptyList()
)

data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val finish_reason: String? = null
)

interface ChatApiService {
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): ChatResponse
}
