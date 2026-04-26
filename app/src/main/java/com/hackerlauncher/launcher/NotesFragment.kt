package com.hackerlauncher.launcher

import com.hackerlauncher.utils.PreferencesManager

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Notes Fragment with AES-256 encryption support.
 * Features: create/edit/delete notes, search, pin, encrypt,
 * tags, auto-save, export as text file.
 * Data stored in SharedPreferences as JSON for simplicity.
 */
class NotesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var prefsManager: PreferencesManager

    private var notes = mutableListOf<NoteItem>()
    private var filteredNotes = mutableListOf<NoteItem>()
    private var searchQuery = ""

    /**
     * Note data model.
     */
    data class NoteItem(
        val id: String,
        var title: String,
        var content: String,
        var date: Long,
        var encrypted: Boolean,
        var tags: List<String>,
        var pinned: Boolean,
        var encryptedContent: String = "" // Base64 encoded encrypted content
    ) {
        fun getFormattedDate(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(date))
        }

        fun getPreview(): String {
            return if (encrypted) {
                "[ENCRYPTED] ${title.take(30)}"
            } else {
                content.take(60).replace("\n", " ")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        prefsManager = PreferencesManager(context)
        loadNotes()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(8, 8, 8, 8)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top bar
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val titleLabel = TextView(context).apply {
            text = "> notes"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 20f
            setShadowLayer(8f, 0f, 0f, Color.parseColor("#3300FF00"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleLabel)

        val addBtn = TextView(context).apply {
            text = "[ +NEW ]"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.parseColor("#0A1A0A"))
            setOnClickListener { showAddNoteDialog() }
        }
        topBar.addView(addBtn)

        val exportAllBtn = TextView(context).apply {
            text = "[ EXPORT ]"
            setTextColor(Color.parseColor("#00AA00"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.parseColor("#0A1A0A"))
            setOnClickListener { exportAllNotes() }
        }
        topBar.addView(exportAllBtn)

        rootLayout.addView(topBar)

        // Search
        searchEditText = EditText(context).apply {
            hint = "> search_notes..."
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(16, 12, 16, 12)
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                filterNotes()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        rootLayout.addView(searchEditText)

        // Notes list
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(recyclerView)

        notesAdapter = NotesAdapter(filteredNotes, ::onNoteClick, ::onNoteLongClick)
        recyclerView.adapter = notesAdapter

        return rootLayout
    }

    private fun loadNotes() {
        notes.clear()
        val jsonStr = prefsManager.getNotesJson()
        if (jsonStr.isNotEmpty()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
                    val tags = mutableListOf<String>()
                    for (j in 0 until tagsArray.length()) {
                        tags.add(tagsArray.getString(j))
                    }
                    notes.add(
                        NoteItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            content = obj.getString("content"),
                            date = obj.getLong("date"),
                            encrypted = obj.optBoolean("encrypted", false),
                            tags = tags,
                            pinned = obj.optBoolean("pinned", false),
                            encryptedContent = obj.optString("encryptedContent", "")
                        )
                    )
                }
            } catch (e: Exception) {
                // Failed to parse notes
            }
        }
        filterNotes()
    }

    private fun saveNotes() {
        try {
            val jsonArray = JSONArray()
            for (note in notes) {
                val obj = JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("date", note.date)
                    put("encrypted", note.encrypted)
                    put("pinned", note.pinned)
                    put("encryptedContent", note.encryptedContent)
                    val tagsArray = JSONArray()
                    for (tag in note.tags) tagsArray.put(tag)
                    put("tags", tagsArray)
                }
                jsonArray.put(obj)
            }
            prefsManager.saveNotesJson(jsonArray.toString())
        } catch (e: Exception) {
            // Silently fail
        }
    }

    private fun filterNotes() {
        filteredNotes.clear()
        val q = searchQuery.lowercase().trim()

        val pinnedNotes = notes.filter { it.pinned }
        val unpinnedNotes = notes.filter { !it.pinned }

        val allSorted = pinnedNotes + unpinnedNotes

        for (note in allSorted) {
            if (q.isEmpty() ||
                note.title.lowercase().contains(q) ||
                note.content.lowercase().contains(q) ||
                note.tags.any { it.lowercase().contains(q) }
            ) {
                filteredNotes.add(note)
            }
        }

        if (::notesAdapter.isInitialized) {
            notesAdapter.notifyDataSetChanged()
        }
    }

    private fun onNoteClick(note: NoteItem) {
        if (note.encrypted) {
            showDecryptDialog(note)
        } else {
            showEditNoteDialog(note)
        }
    }

    private fun onNoteLongClick(note: NoteItem): Boolean {
        val options = mutableListOf<String>()
        options.add("Edit")
        options.add(if (note.pinned) "Unpin" else "Pin")
        options.add("Add Tag")
        options.add(if (note.encrypted) "Decrypt" else "Encrypt")
        options.add("Export")
        options.add("Delete")

        AlertDialog.Builder(requireContext())
            .setTitle("> ${note.title}")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showEditNoteDialog(note)
                    1 -> togglePin(note)
                    2 -> showAddTagDialog(note)
                    3 -> if (note.encrypted) showDecryptDialog(note) else showEncryptDialog(note)
                    4 -> exportNote(note)
                    5 -> showDeleteConfirmation(note)
                }
            }
            .show()
        return true
    }

    private fun showAddNoteDialog() {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val titleInput = EditText(context).apply {
            hint = "> title"
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 12, 16, 12)
        }
        container.addView(titleInput)

        val contentInput = EditText(context).apply {
            hint = "> content..."
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 12, 16, 12)
            minLines = 5
            gravity = Gravity.TOP
        }
        container.addView(contentInput)

        val tagsInput = EditText(context).apply {
            hint = "> tags (comma-separated)"
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 12, 16, 12)
        }
        container.addView(tagsInput)

        AlertDialog.Builder(context)
            .setTitle("> new_note")
            .setView(container)
            .setPositiveButton("save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString()
                val tags = tagsInput.text.toString().split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (title.isEmpty()) {
                    Toast.makeText(context, "> title_required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val note = NoteItem(
                    id = generateId(),
                    title = title,
                    content = content,
                    date = System.currentTimeMillis(),
                    encrypted = false,
                    tags = tags,
                    pinned = false
                )
                notes.add(0, note)
                saveNotes()
                filterNotes()
                Toast.makeText(context, "> note_saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showEditNoteDialog(note: NoteItem) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val titleInput = EditText(context).apply {
            setText(note.title)
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 12, 16, 12)
        }
        container.addView(titleInput)

        val contentInput = EditText(context).apply {
            setText(note.content)
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 12, 16, 12)
            minLines = 5
            gravity = Gravity.TOP
        }
        container.addView(contentInput)

        val tagsInput = EditText(context).apply {
            setText(note.tags.joinToString(", "))
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(16, 12, 16, 12)
        }
        container.addView(tagsInput)

        AlertDialog.Builder(context)
            .setTitle("> edit_note")
            .setView(container)
            .setPositiveButton("save") { _, _ ->
                note.title = titleInput.text.toString().trim().ifEmpty { "untitled" }
                note.content = contentInput.text.toString()
                note.tags = tagsInput.text.toString().split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                note.date = System.currentTimeMillis()

                saveNotes()
                filterNotes()
                Toast.makeText(context, "> note_updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun togglePin(note: NoteItem) {
        note.pinned = !note.pinned
        saveNotes()
        filterNotes()
        Toast.makeText(
            requireContext(),
            if (note.pinned) "> pinned: ${note.title}" else "> unpinned: ${note.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showAddTagDialog(note: NoteItem) {
        val input = EditText(requireContext()).apply {
            hint = "> tag_name"
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("> add_tag")
            .setView(input)
            .setPositiveButton("add") { _, _ ->
                val tag = input.text.toString().trim()
                if (tag.isNotEmpty() && tag !in note.tags) {
                    note.tags = note.tags + tag
                    saveNotes()
                    filterNotes()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showEncryptDialog(note: NoteItem) {
        val input = EditText(requireContext()).apply {
            hint = "> encryption_password"
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("> encrypt_note")
            .setMessage("AES-256 encryption. Remember your password!")
            .setView(input)
            .setPositiveButton("encrypt") { _, _ ->
                val password = input.text.toString()
                if (password.length < 4) {
                    Toast.makeText(requireContext(), "> password_min_4_chars", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    val encrypted = encrypt(note.content, password)
                    note.encryptedContent = encrypted
                    note.encrypted = true
                    note.content = "" // Clear plaintext
                    saveNotes()
                    filterNotes()
                    Toast.makeText(requireContext(), "> note_encrypted", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "> encryption_error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showDecryptDialog(note: NoteItem) {
        val input = EditText(requireContext()).apply {
            hint = "> decryption_password"
            setHintTextColor(0xFF00AA00.toInt())
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("> decrypt_note")
            .setView(input)
            .setPositiveButton("decrypt") { _, _ ->
                val password = input.text.toString()
                try {
                    val decrypted = decrypt(note.encryptedContent, password)
                    note.content = decrypted
                    note.encrypted = false
                    note.encryptedContent = ""
                    saveNotes()
                    filterNotes()
                    showEditNoteDialog(note)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "> decryption_failed: wrong_password?", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(note: NoteItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("> delete_note?")
            .setMessage("Delete '${note.title}'? This cannot be undone.")
            .setPositiveButton("delete") { _, _ ->
                notes.removeAll { it.id == note.id }
                saveNotes()
                filterNotes()
                Toast.makeText(requireContext(), "> note_deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun exportNote(note: NoteItem) {
        val context = requireContext()
        try {
            val filename = note.title.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".txt"
            val content = buildString {
                appendLine("> exported_note")
                appendLine("> title: ${note.title}")
                appendLine("> date: ${note.getFormattedDate()}")
                appendLine("> tags: ${note.tags.joinToString(", ")}")
                appendLine("> encrypted: ${note.encrypted}")
                appendLine("---")
                appendLine(if (note.encrypted) "[ENCRYPTED]" else note.content)
            }

            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val exportDir = File(downloadsDir, "HackerLauncher/Notes")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, filename)

            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray())
            }

            Toast.makeText(context, "> exported_to: Downloads/HackerLauncher/Notes/$filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "> export_error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAllNotes() {
        val context = requireContext()
        try {
            val sb = StringBuilder()
            sb.appendLine("> hackerlauncher_notes_export")
            sb.appendLine("> date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
            sb.appendLine("> count: ${notes.size}")
            sb.appendLine("=" .repeat(50))

            for ((index, note) in notes.withIndex()) {
                sb.appendLine()
                sb.appendLine("--- NOTE ${index + 1} ---")
                sb.appendLine("title: ${note.title}")
                sb.appendLine("date: ${note.getFormattedDate()}")
                sb.appendLine("tags: ${note.tags.joinToString(", ")}")
                sb.appendLine("pinned: ${note.pinned}")
                sb.appendLine("encrypted: ${note.encrypted}")
                sb.appendLine("content:")
                sb.appendLine(if (note.encrypted) "[ENCRYPTED]" else note.content)
            }

            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val exportDir = File(downloadsDir, "HackerLauncher/Notes")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, "all_notes_${System.currentTimeMillis()}.txt")

            FileOutputStream(file).use { fos ->
                fos.write(sb.toString().toByteArray())
            }

            Toast.makeText(context, "> all_notes_exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "> export_error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    //region AES-256 Encryption

    companion object {
        private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KEY_LENGTH = 256
        private const val ITERATIONS = 10000
        private const val IV_LENGTH = 16

        fun newInstance(): NotesFragment = NotesFragment()
    }

    private fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM)
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine salt + iv + encrypted
        val combined = salt + iv + encryptedBytes
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encryptedText: String, password: String): String {
        val combined = Base64.getDecoder().decode(encryptedText)

        val salt = combined.copyOfRange(0, 16)
        val iv = combined.copyOfRange(16, 16 + IV_LENGTH)
        val encryptedBytes = combined.copyOfRange(16 + IV_LENGTH, combined.size)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM)
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    //endregion

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..12).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
    }

    //region NotesAdapter

    class NotesAdapter(
        private val items: List<NoteItem>,
        private val onClick: (NoteItem) -> Unit,
        private val onLongClick: (NoteItem) -> Boolean
    ) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

        class NoteViewHolder(
            itemView: View,
            val titleView: TextView,
            val previewView: TextView,
            val dateView: TextView,
            val tagsView: TextView,
            val pinBadge: TextView,
            val lockBadge: TextView
        ) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val context = parent.context
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundColor(Color.parseColor("#0A0A0A"))
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4; bottomMargin = 4 }
            }

            // Top row: title + badges
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val pinBadge = TextView(context).apply {
                text = "📌"
                textSize = 12f
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 4 }
            }

            val lockBadge = TextView(context).apply {
                text = "🔒"
                textSize = 12f
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 4 }
            }

            val titleView = TextView(context).apply {
                setTextColor(Color.parseColor("#00FF00"))
                typeface = Typeface.MONOSPACE
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            topRow.addView(pinBadge)
            topRow.addView(lockBadge)
            topRow.addView(titleView)
            container.addView(topRow)

            val previewView = TextView(context).apply {
                setTextColor(Color.parseColor("#008800"))
                typeface = Typeface.MONOSPACE
                textSize = 12f
                maxLines = 2
                setPadding(0, 4, 0, 0)
            }
            container.addView(previewView)

            // Bottom row: date + tags
            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 0)
            }

            val dateView = TextView(context).apply {
                setTextColor(Color.parseColor("#005500"))
                typeface = Typeface.MONOSPACE
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tagsView = TextView(context).apply {
                setTextColor(Color.parseColor("#003300"))
                typeface = Typeface.MONOSPACE
                textSize = 10f
                gravity = Gravity.END
            }

            bottomRow.addView(dateView)
            bottomRow.addView(tagsView)
            container.addView(bottomRow)

            return NoteViewHolder(container, titleView, previewView, dateView, tagsView, pinBadge, lockBadge)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val item = items[position]
            holder.titleView.text = item.title
            holder.previewView.text = item.getPreview()
            holder.dateView.text = item.getFormattedDate()
            holder.tagsView.text = item.tags.joinToString(" ") { "#$it" }

            holder.pinBadge.visibility = if (item.pinned) View.VISIBLE else View.GONE
            holder.lockBadge.visibility = if (item.encrypted) View.VISIBLE else View.GONE

            // Green left border for pinned
            holder.itemView.setBackgroundColor(
                if (item.pinned) Color.parseColor("#0A1A0A") else Color.parseColor("#0A0A0A")
            )

            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onLongClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    //endregion
}
