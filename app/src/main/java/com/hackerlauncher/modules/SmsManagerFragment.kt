package com.hackerlauncher.modules

import com.hackerlauncher.R

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager as AndroidSmsManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsManagerFragment : Fragment() {

    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var tvSmsCount: TextView
    private lateinit var fabCompose: com.google.android.material.floatingactionbutton.FloatingActionButton

    private val conversations = mutableListOf<SmsConversation>()
    private val filteredConversations = mutableListOf<SmsConversation>()
    private lateinit var conversationAdapter: ConversationAdapter

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] ?: false
        val sendGranted = permissions[Manifest.permission.SEND_SMS] ?: false
        if (readGranted) loadSmsMessages()
        else showToast("READ_SMS permission required")
    }

    data class SmsMessage(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int, // 1=inbox, 2=sent, 3=draft, 4=outbox, 5=failed, 6=queued
        val read: Boolean
    )

    data class SmsConversation(
        val threadId: Long,
        val address: String,
        val contactName: String?,
        val messages: MutableList<SmsMessage>,
        val lastMessage: SmsMessage,
        val unreadCount: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_sms_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.searchInput)
        recyclerView = view.findViewById(R.id.smsRecycler)
        emptyState = view.findViewById(R.id.emptyState)
        tvSmsCount = view.findViewById(R.id.tvSmsCount)
        fabCompose = view.findViewById(R.id.fabCompose)

        conversationAdapter = ConversationAdapter(filteredConversations) { conversation ->
            showConversationDetail(conversation)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = conversationAdapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterConversations(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fabCompose.setOnClickListener { showComposeDialog() }

        requestSmsPermissions()
    }

    private fun requestSmsPermissions() {
        val needsRead = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.READ_SMS
        ) != PackageManager.PERMISSION_GRANTED

        val needsSend = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.SEND_SMS
        ) != PackageManager.PERMISSION_GRANTED

        if (needsRead || needsSend) {
            val perms = mutableListOf<String>()
            if (needsRead) perms.add(Manifest.permission.READ_SMS)
            if (needsSend) perms.add(Manifest.permission.SEND_SMS)
            smsPermissionLauncher.launch(perms.toTypedArray())
        } else {
            loadSmsMessages()
        }
    }

    private fun loadSmsMessages() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val messages = mutableListOf<SmsMessage>()

                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                )

                val cursor = requireContext().contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )

                cursor?.use {
                    val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                    val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                    val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                    while (it.moveToNext()) {
                        messages.add(
                            SmsMessage(
                                id = it.getLong(idIdx),
                                threadId = it.getLong(threadIdx),
                                address = it.getString(addrIdx) ?: "Unknown",
                                body = it.getString(bodyIdx) ?: "",
                                date = it.getLong(dateIdx),
                                type = it.getInt(typeIdx),
                                read = it.getInt(readIdx) == 1
                            )
                        )
                    }
                }

                // Group by thread
                val threadMap = mutableMapOf<Long, MutableList<SmsMessage>>()
                for (msg in messages) {
                    threadMap.getOrPut(msg.threadId) { mutableListOf() }.add(msg)
                }

                val convos = threadMap.map { (threadId, msgs) ->
                    val sortedMsgs = msgs.sortedByDescending { it.date }
                    val address = sortedMsgs.first().address
                    val contactName = getContactName(address)
                    val unread = sortedMsgs.count { !it.read }

                    SmsConversation(
                        threadId = threadId,
                        address = address,
                        contactName = contactName,
                        messages = sortedMsgs.toMutableList(),
                        lastMessage = sortedMsgs.first(),
                        unreadCount = unread
                    )
                }.sortedByDescending { it.lastMessage.date }

                withContext(Dispatchers.Main) {
                    conversations.clear()
                    conversations.addAll(convos)
                    filteredConversations.clear()
                    filteredConversations.addAll(convos)
                    conversationAdapter.notifyDataSetChanged()
                    tvSmsCount.text = "${convos.size} conversations"
                    emptyState.visibility = if (convos.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            phoneNumber
        )
        val cursor = requireContext().contentResolver.query(
            uri,
            arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun filterConversations(query: String) {
        val q = query.lowercase(Locale.getDefault())
        filteredConversations.clear()
        if (q.isEmpty()) {
            filteredConversations.addAll(conversations)
        } else {
            filteredConversations.addAll(conversations.filter { conv ->
                conv.address.lowercase(Locale.getDefault()).contains(q) ||
                conv.contactName?.lowercase(Locale.getDefault())?.contains(q) == true ||
                conv.messages.any { it.body.lowercase(Locale.getDefault()).contains(q) }
            })
        }
        conversationAdapter.notifyDataSetChanged()
        emptyState.visibility = if (filteredConversations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showConversationDetail(conversation: SmsConversation) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_sms_conversation, null)

        val tvTitle: TextView = sheetView.findViewById(R.id.tvConversationTitle)
        val tvAddress: TextView = sheetView.findViewById(R.id.tvConversationAddress)
        val messagesLayout: LinearLayout = sheetView.findViewById(R.id.messagesLayout)
        val etReply: EditText = sheetView.findViewById(R.id.etReply)
        val btnSend: MaterialButton = sheetView.findViewById(R.id.btnSend)
        val chipCall: Chip = sheetView.findViewById(R.id.chipCall)
        val chipDelete: Chip = sheetView.findViewById(R.id.chipDeleteConversation)

        tvTitle.text = conversation.contactName ?: conversation.address
        tvAddress.text = conversation.address

        // Show messages (most recent first)
        messagesLayout.removeAllViews()
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        for (msg in conversation.messages.take(50)) {
            val msgView = TextView(requireContext()).apply {
                val typeStr = when (msg.type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "◀ IN"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "▶ OUT"
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> "◆ DRAFT"
                    else -> "●"
                }
                text = "$typeStr [${dateFormat.format(Date(msg.date))}]\n${msg.body}"
                setTextColor(
                    if (msg.type == Telephony.Sms.MESSAGE_TYPE_INBOX)
                        android.graphics.Color.parseColor("#00FF00")
                    else
                        android.graphics.Color.parseColor("#00AA00")
                )
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 12f
                setPadding(8, 8, 8, 8)
                if (!msg.read && msg.type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    setBackgroundColor(android.graphics.Color.parseColor("#002200"))
                }
            }
            messagesLayout.addView(msgView)

            // Long press to delete individual message
            msgView.setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Message")
                    .setMessage("Delete this message?")
                    .setPositiveButton("Delete") { _, _ -> deleteMessage(msg) }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }

        btnSend.setOnClickListener {
            val text = etReply.text.toString().trim()
            if (text.isNotEmpty()) {
                sendSms(conversation.address, text)
                etReply.text.clear()
            }
        }

        chipCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${conversation.address}")))
        }

        chipDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Conversation")
                .setMessage("Delete all messages with ${conversation.address}?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteConversation(conversation.threadId)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showComposeDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etPhone = EditText(requireContext()).apply {
            hint = "Phone number"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.parseColor("#005500"))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val etMessage = EditText(requireContext()).apply {
            hint = "Message"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.parseColor("#005500"))
            typeface = android.graphics.Typeface.MONOSPACE
            minLines = 3
        }

        layout.addView(etPhone)
        layout.addView(etMessage)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send SMS")
            .setView(layout)
            .setPositiveButton("Send") { _, _ ->
                val phone = etPhone.text.toString().trim()
                val msg = etMessage.text.toString().trim()
                if (phone.isNotEmpty() && msg.isNotEmpty()) {
                    sendSms(phone, msg)
                } else {
                    showToast("Enter phone and message")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                requireContext().getSystemService("sms") as AndroidSmsManager
            } else {
                @Suppress("DEPRECATION")
                AndroidSmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            showToast("SMS sent to $phoneNumber")
            // Refresh list after sending
            loadSmsMessages()
        } catch (e: Exception) {
            showToast("Failed to send SMS: ${e.message}")
        }
    }

    private fun deleteMessage(message: SmsMessage) {
        try {
            val deleted = requireContext().contentResolver.delete(
                Uri.parse("content://sms/${message.id}"),
                null, null
            )
            if (deleted > 0) {
                showToast("Message deleted")
                loadSmsMessages()
            } else {
                showToast("Failed to delete message")
            }
        } catch (e: Exception) {
            showToast("Delete failed: ${e.message}")
        }
    }

    private fun deleteConversation(threadId: Long) {
        try {
            val deleted = requireContext().contentResolver.delete(
                Uri.parse("content://sms"),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            showToast("Deleted $deleted messages")
            loadSmsMessages()
        } catch (e: Exception) {
            showToast("Delete failed: ${e.message}")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    // --- Inner Adapter ---

    inner class ConversationAdapter(
        private val items: List<SmsConversation>,
        private val onClick: (SmsConversation) -> Unit
    ) : RecyclerView.Adapter<ConversationAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvConvName)
            val tvAddress: TextView = view.findViewById(R.id.tvConvAddress)
            val tvPreview: TextView = view.findViewById(R.id.tvConvPreview)
            val tvTime: TextView = view.findViewById(R.id.tvConvTime)
            val tvUnread: TextView = view.findViewById(R.id.tvConvUnread)
            val tvMsgCount: TextView = view.findViewById(R.id.tvConvMsgCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sms_conversation, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.contactName ?: item.address
            holder.tvAddress.text = item.address
            holder.tvPreview.text = item.lastMessage.body.take(60) +
                    if (item.lastMessage.body.length > 60) "..." else ""
            holder.tvTime.text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(item.lastMessage.date))
            holder.tvMsgCount.text = "${item.messages.size} msgs"

            if (item.unreadCount > 0) {
                holder.tvUnread.visibility = View.VISIBLE
                holder.tvUnread.text = "${item.unreadCount}"
            } else {
                holder.tvUnread.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
