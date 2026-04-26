package com.hackerlauncher.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hackerlauncher.R
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<Message>()

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(msgs: List<Message>) {
        messages.clear()
        messages.addAll(msgs)
        notifyDataSetChanged()
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.tvMessage.text = message.content
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.tvTime.text = timeFormat.format(Date(message.timestamp))

        val params = holder.messageContainer.layoutParams as RecyclerView.LayoutParams

        if (message.isUser) {
            holder.tvMessage.setBackgroundResource(R.drawable.bg_message_user)
            holder.tvMessage.setTextColor(0xFF000000.toInt())
            holder.messageContainer.gravity = Gravity.END
            holder.tvSender.text = "You"
            params.marginStart = 80
            params.marginEnd = 8
        } else {
            holder.tvMessage.setBackgroundResource(R.drawable.bg_message_ai)
            holder.tvMessage.setTextColor(0xFF00FF00.toInt())
            holder.messageContainer.gravity = Gravity.START
            holder.tvSender.text = "AI"
            params.marginStart = 8
            params.marginEnd = 80
        }
        holder.messageContainer.layoutParams = params
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvChatMessage)
        val tvTime: TextView = view.findViewById(R.id.tvChatTime)
        val tvSender: TextView = view.findViewById(R.id.tvChatSender)
        val messageContainer: LinearLayout = view.findViewById(R.id.messageContainer)
    }
}
