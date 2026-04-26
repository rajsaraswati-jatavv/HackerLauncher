package com.hackerlauncher.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.hackerlauncher.utils.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var db: ChatDatabase? = null
    private var apiService: ChatApiService? = null
    private var chatHistory = mutableListOf<ChatMessage>()
    private var sessionId = "default"

    fun initDatabase(context: Context) {
        db = Room.databaseBuilder(
            context.applicationContext,
            ChatDatabase::class.java,
            "chat_database"
        ).build()
        loadHistory()
    }

    fun initApi(context: Context) {
        val prefs = PreferencesManager(context)
        val apiKey = prefs.getChatApiKey()
        val apiProvider = prefs.getChatApiProvider()
        val baseUrl = when (apiProvider) {
            "openai" -> "https://api.openai.com/"
            "gemini" -> "https://generativelanguage.googleapis.com/"
            else -> "https://api.openai.com/"
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ChatApiService::class.java)
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val messages = db?.messageDao()?.getMessagesBySession(sessionId) ?: emptyList()
            _messages.value = messages
            chatHistory.clear()
            messages.forEach { msg ->
                chatHistory.add(ChatMessage(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.content
                ))
            }
        }
    }

    fun sendMessage(content: String, context: Context? = null) {
        viewModelScope.launch {
            val userMessage = Message(content = content, isUser = true, sessionId = sessionId)
            db?.messageDao()?.insert(userMessage)
            _messages.value = _messages.value + userMessage
            chatHistory.add(ChatMessage(role = "user", content = content))

            _isTyping.value = true
            _error.value = null

            try {
                val response = withContext(Dispatchers.IO) {
                    callApi(content, context)
                }
                val aiMessage = Message(content = response, isUser = false, sessionId = sessionId)
                db?.messageDao()?.insert(aiMessage)
                _messages.value = _messages.value + aiMessage
                chatHistory.add(ChatMessage(role = "assistant", content = response))
            } catch (e: Exception) {
                _error.value = e.message
                // Fallback to mock response
                val mockResponse = generateMockResponse(content)
                val aiMessage = Message(content = mockResponse, isUser = false, sessionId = sessionId)
                db?.messageDao()?.insert(aiMessage)
                _messages.value = _messages.value + aiMessage
                chatHistory.add(ChatMessage(role = "assistant", content = mockResponse))
            }

            _isTyping.value = false
        }
    }

    private suspend fun callApi(userMessage: String, context: Context?): String {
        val prefs = context?.let { PreferencesManager(it) }
        val apiKey = prefs?.getChatApiKey() ?: ""

        if (apiKey.isEmpty()) {
            return generateMockResponse(userMessage)
        }

        val request = ChatRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(
                ChatMessage(role = "system", content = "You are a helpful AI assistant inside HackerLauncher, a hacker-themed Android app. Respond concisely with a technical flair. Use hacker/security terminology when appropriate.")
            ) + chatHistory.takeLast(10),
            max_tokens = 1024,
            temperature = 0.7
        )

        val response = apiService?.sendMessage(
            url = "v1/chat/completions",
            authHeader = "Bearer $apiKey",
            request = request
        ) ?: throw Exception("API service not initialized")

        return response.choices.firstOrNull()?.message?.content
            ?: throw Exception("No response from API")
    }

    private fun generateMockResponse(userMessage: String): String {
        val lower = userMessage.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") ->
                "Greetings, fellow hacker. How can I assist you today?"
            lower.contains("help") ->
                "I can help with: coding questions, security concepts, tool usage, and general queries. What do you need?"
            lower.contains("hack") ->
                "Remember: with great power comes great responsibility. I can explain concepts for educational purposes. What specifically interests you?"
            lower.contains("security") ->
                "Cybersecurity is a vast field. Key areas: network security, application security, cryptography, and social engineering. Which area shall we explore?"
            lower.contains("python") || lower.contains("code") ->
                "Python is excellent for security tooling. Key libraries: requests, scapy, beautifulsoup4, paramiko. What would you like to build?"
            lower.contains("network") ->
                "Network analysis tools: nmap for scanning, wireshark for packet analysis, netcat for connections. What do you need help with?"
            else ->
                "Interesting query. I'm currently in offline mode. Connect an API key in settings for full AI capabilities. " +
                "For now, I can provide general guidance on: security, coding, networking, and tools."
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db?.messageDao()?.deleteSession(sessionId)
            chatHistory.clear()
            _messages.value = emptyList()
        }
    }

    fun newSession() {
        sessionId = System.currentTimeMillis().toString()
        chatHistory.clear()
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        db?.close()
    }
}
