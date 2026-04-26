package com.hackerlauncher.modules

import com.hackerlauncher.R
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ClipboardItem(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isPinned: Boolean = false
)

class ClipboardManagerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var clearAllButton: ImageButton
    private lateinit var exportButton: ImageButton
    private lateinit var emptyStateText: TextView
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var clipboardManager: android.content.ClipboardManager

    private val clipboardItems = mutableListOf<ClipboardItem>()
    private val prefs by lazy {
        requireContext().getSharedPreferences("clipboard_prefs", Context.MODE_PRIVATE)
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val clipChangedListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
            if (text.isNotBlank() && clipboardItems.none { it.text == text }) {
                val item = ClipboardItem(
                    id = System.currentTimeMillis().toString(),
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isPinned = false
                )
                clipboardItems.add(0, item)
                saveItems()
                clipboardAdapter.notifyItemInserted(0)
                recyclerView.scrollToPosition(0)
                updateEmptyState()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_clipboard_manager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewClipboard)
        searchEditText = view.findViewById(R.id.editTextSearch)
        clearAllButton = view.findViewById(R.id.buttonClearAll)
        exportButton = view.findViewById(R.id.buttonExport)
        emptyStateText = view.findViewById(R.id.textViewEmptyState)

        clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        setupRecyclerView()
        setupSearch()
        setupButtons()
        loadItems()
        updateEmptyState()

        clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
    }

    private fun setupRecyclerView() {
        clipboardAdapter = ClipboardAdapter(
            items = clipboardItems,
            onPinClick = { position ->
                val item = clipboardItems[position]
                clipboardItems[position] = item.copy(isPinned = !item.isPinned)
                saveItems()
                clipboardAdapter.notifyItemChanged(position)
                val msg = if (item.isPinned) "Unpinned" else "Pinned"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { position ->
                clipboardItems.removeAt(position)
                saveItems()
                clipboardAdapter.notifyItemRemoved(position)
                updateEmptyState()
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            },
            onCopyClick = { position ->
                val item = clipboardItems[position]
                val clip = android.content.ClipData.newPlainText("text", item.text)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            dateFormat = dateFormat
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = clipboardAdapter
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val filtered = clipboardItems.filter {
                    it.text.lowercase(Locale.getDefault()).contains(query)
                }
                clipboardAdapter.updateItems(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupButtons() {
        clearAllButton.setOnClickListener {
            if (clipboardItems.isEmpty()) return@setOnClickListener
            val pinned = clipboardItems.filter { it.isPinned }
            clipboardItems.clear()
            clipboardItems.addAll(pinned)
            saveItems()
            clipboardAdapter.updateItems(clipboardItems.toList())
            updateEmptyState()
            Toast.makeText(requireContext(), "Cleared (pinned kept)", Toast.LENGTH_SHORT).show()
        }

        exportButton.setOnClickListener {
            lifecycleScope.launch {
                exportItems()
            }
        }
    }

    private suspend fun exportItems() {
        withContext(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                for (item in clipboardItems) {
                    val jsonObj = JSONObject().apply {
                        put("id", item.id)
                        put("text", item.text)
                        put("timestamp", item.timestamp)
                        put("isPinned", item.isPinned)
                        put("date", dateFormat.format(Date(item.timestamp)))
                    }
                    jsonArray.put(jsonObj)
                }
                val exportDir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "HackerLauncher"
                )
                if (!exportDir.exists()) exportDir.mkdirs()
                val file = java.io.File(exportDir, "clipboard_export_${System.currentTimeMillis()}.json")
                file.writeText(jsonArray.toString(2))
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Exported to ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveItems() {
        val jsonArray = JSONArray()
        for (item in clipboardItems) {
            val jsonObj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("timestamp", item.timestamp)
                put("isPinned", item.isPinned)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString("clipboard_items", jsonArray.toString()).apply()
    }

    private fun loadItems() {
        val jsonStr = prefs.getString("clipboard_items", null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            clipboardItems.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                clipboardItems.add(
                    ClipboardItem(
                        id = jsonObj.getString("id"),
                        text = jsonObj.getString("text"),
                        timestamp = jsonObj.getLong("timestamp"),
                        isPinned = jsonObj.optBoolean("isPinned", false)
                    )
                )
            }
            clipboardAdapter.updateItems(clipboardItems.toList())
        } catch (_: Exception) {
        }
    }

    private fun updateEmptyState() {
        emptyStateText.visibility = if (clipboardItems.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
    }

    inner class ClipboardAdapter(
        private var items: List<ClipboardItem>,
        private val onPinClick: (Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit,
        private val onCopyClick: (Int) -> Unit,
        private val dateFormat: SimpleDateFormat
    ) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textContent: TextView = view.findViewById(R.id.textViewClipContent)
            val textTimestamp: TextView = view.findViewById(R.id.textViewClipTimestamp)
            val buttonPin: ImageButton = view.findViewById(R.id.buttonPin)
            val buttonDelete: ImageButton = view.findViewById(R.id.buttonDelete)
            val buttonCopy: ImageButton = view.findViewById(R.id.buttonCopy)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_clipboard, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textContent.text = item.text.take(200) + if (item.text.length > 200) "..." else ""
            holder.textTimestamp.text = dateFormat.format(Date(item.timestamp))

            if (item.isPinned) {
                holder.buttonPin.setImageResource(R.drawable.ic_pin_filled)
                holder.itemView.setBackgroundColor(0xFF1A3A1A.toInt())
            } else {
                holder.buttonPin.setImageResource(R.drawable.ic_pin_outline)
                holder.itemView.setBackgroundColor(0xFF000000.toInt())
            }

            holder.buttonPin.setOnClickListener { onPinClick(holder.adapterPosition) }
            holder.buttonDelete.setOnClickListener { onDeleteClick(holder.adapterPosition) }
            holder.buttonCopy.setOnClickListener { onCopyClick(holder.adapterPosition) }

            holder.itemView.setOnClickListener {
                val dialog = android.app.AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                    .setTitle("Clipboard Content")
                    .setMessage(item.text)
                    .setPositiveButton("Copy") { _, _ -> onCopyClick(holder.adapterPosition) }
                    .setNegativeButton("Close", null)
                    .create()
                dialog.show()
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateItems(newItems: List<ClipboardItem>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
