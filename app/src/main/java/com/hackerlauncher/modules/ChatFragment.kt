package com.hackerlauncher.modules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import com.hackerlauncher.chat.ChatAdapter
import com.hackerlauncher.chat.ChatViewModel
import com.hackerlauncher.chat.Message
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClearChat: Button
    private lateinit var tvTyping: TextView
    private lateinit var chatAdapter: ChatAdapter
    private val chatViewModel = ChatViewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChat = view.findViewById(R.id.rvChat)
        etMessage = view.findViewById(R.id.etChatMessage)
        btnSend = view.findViewById(R.id.btnChatSend)
        btnClearChat = view.findViewById(R.id.btnClearChat)
        tvTyping = view.findViewById(R.id.tvTypingIndicator)

        chatAdapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter

        chatViewModel.initDatabase(requireContext())
        chatViewModel.initApi(requireContext())

        // Observe messages
        lifecycleScope.launch {
            chatViewModel.messages.collect { messages ->
                chatAdapter.setMessages(messages)
                if (messages.isNotEmpty()) {
                    rvChat.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        // Observe typing indicator
        lifecycleScope.launch {
            chatViewModel.isTyping.collect { isTyping ->
                tvTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
                tvTyping.text = "AI is typing..."
            }
        }

        // Observe errors
        lifecycleScope.launch {
            chatViewModel.error.collect { error ->
                error?.let {
                    // Using mock response fallback, errors are handled gracefully
                }
            }
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                chatViewModel.sendMessage(text, requireContext())
                etMessage.text.clear()
            }
        }

        btnClearChat.setOnClickListener {
            chatViewModel.clearHistory()
        }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                btnSend.performClick()
                true
            } else false
        }
    }
}
